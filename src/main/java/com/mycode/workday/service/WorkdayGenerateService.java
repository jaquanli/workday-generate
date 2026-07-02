package com.mycode.workday.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mycode.workday.model.Workday;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * 工作日生成服务。算法基于「固定月份天数表 + 接口辅助」，将请求数从「全年逐天」降到约 106 次：
 * <ol>
 *   <li><b>固定天数表</b>：1/3/5/7/8/10/12 月 31 天，4/6/9/11 月 30 天，仅 2 月待定。</li>
 *   <li><b>2 月探测</b>：{@code ?d={year}0229}，返回 0/1/2 → 有 29 日；返回 JSON 错误对象 → 28 天。</li>
 *   <li><b>全年法定节假日</b>：{@code ?d={year}} 一次，用于剔除工作日中的法定假日/调休。</li>
 *   <li><b>普通周末</b>：本地按 {@link DayOfWeek} 判断，无需请求。</li>
 *   <li><b>补班日</b>：周末中调休要上班的日子，全年接口不标记，需对每个周末请求
 *       {@code ?d=yyyymmdd} 确认（返回 0），16 线程并发。</li>
 * </ol>
 * 工作日 = (非周末 且 非法定假日) ∪ (周末 且 补班日)。
 * <p>
 * 进度通过 {@code BiConsumer<Integer, Integer> (已完成, 总数)} 回调上报，在工作线程触发。
 */
@Slf4j
@Service
public class WorkdayGenerateService {

    private static final String API_URL = "http://tool.bitefu.net/jiari/?d=";
    /** 固定月份天数：1/3/5/7/8/10/12=31，4/6/9/11=30，2 月由探测决定 */
    private static final int[] DAYS_IN_MONTH = {0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
    /**
     * 补班查询并发度。该免费接口对高并发较敏感（16 并发约 3% 超时），
     * 实测 4 并发稳定无超时；优先保证成功率，耗时仅多几秒。
     */
    private static final int QUERY_THREADS = 4;
    /** 单请求超时（秒）：给慢响应留足时间，避免误判超时 */
    private static final int REQUEST_TIMEOUT_SECONDS = 15;
    /** 单请求失败后的最大重试次数（含首次共 RETRY+1 次） */
    private static final int MAX_RETRY = 3;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 生成工作日（不带进度回调）。 */
    public List<Workday> generate(int year) throws Exception {
        return generate(year, null);
    }

    /**
     * 生成指定年份的工作日列表。
     *
     * @param progress 进度回调 {@code (已完成, 总数)}，可为 null；在工作线程触发。
     */
    public List<Workday> generate(int year, BiConsumer<Integer, Integer> progress) throws Exception {
        // 1) 探测 2 月天数 + 2) 全年法定节假日
        int febDays = detectFebruaryDays(year);
        Set<String> holidayKeys = fetchHolidayKeys(year);

        // 3) 按固定天数表枚举全年日期，收集周末日用于补班查询
        List<LocalDate> allDays = new ArrayList<>();
        List<LocalDate> weekendDays = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            int days = (month == 2) ? febDays : DAYS_IN_MONTH[month];
            for (int day = 1; day <= days; day++) {
                var date = LocalDate.of(year, month, day);
                allDays.add(date);
                if (isWeekend(date)) {
                    weekendDays.add(date);
                }
            }
        }

        // 4) 并发查询周末补班日
        Set<LocalDate> makeupWorkdays = queryMakeupWorkdays(weekendDays, progress);
        log.info("{} 年：2月{}天，节假日{}个，补班{}个", year, febDays, holidayKeys.size(), makeupWorkdays.size());

        // 5) 组装工作日：(非周末 且 非法定假日) ∪ (周末 且 补班)
        List<Workday> workdays = new ArrayList<>();
        for (var date : allDays) {
            boolean isWorkday = isWeekend(date)
                    ? makeupWorkdays.contains(date)
                    : !holidayKeys.contains(String.format("%02d%02d", date.getMonthValue(), date.getDayOfMonth()));
            if (isWorkday) {
                workdays.add(new Workday(0, date.atStartOfDay(), date.getMonthValue(), date.getYear()));
            }
        }
        log.info("{} 年共生成工作日 {} 个", year, workdays.size());
        return workdays;
    }

    /** 探测 2 月天数：返回 0/1/2 → 29 天；返回 JSON 错误对象或失败 → 28 天。 */
    private int detectFebruaryDays(int year) {
        var body = getWithRetry(API_URL + String.format("%d0229", year));
        if (body != null) {
            var t = body.trim();
            if ("0".equals(t) || "1".equals(t) || "2".equals(t)) {
                return 29;
            }
            if (t.startsWith("{")) {
                return 28;
            }
        }
        log.warn("2 月 29 日探测未得到明确结果，按 28 天处理");
        return 28;
    }

    /** 获取全年法定节假日/调休的 {@code "MMdd"} 键集合。失败则抛异常。 */
    private Set<String> fetchHolidayKeys(int year) throws Exception {
        Set<String> keys = new HashSet<>();
        var body = getWithRetry(API_URL + year);
        if (body == null) {
            // 全年节假日是核心数据，缺失会导致大量误判，必须告知用户重试
            throw new IllegalStateException("获取 " + year + " 年节假日数据失败（接口无响应），请点击「生成工作日」重试");
        }
        JsonNode yearNode = objectMapper.readTree(body).get(String.valueOf(year));
        if (yearNode != null && yearNode.isObject()) {
            yearNode.fieldNames().forEachRemaining(keys::add);
        }
        return keys;
    }

    /** 并发查询每个周末是否为补班日（返回 0）。有查询彻底失败则抛异常，避免返回残缺结果。 */
    private Set<LocalDate> queryMakeupWorkdays(List<LocalDate> weekendDays,
                                               BiConsumer<Integer, Integer> progress) throws Exception {
        Set<LocalDate> makeup = ConcurrentHashMap.newKeySet();
        // 记录彻底失败的日期（重试耗尽仍未拿到响应）
        List<LocalDate> failed = Collections.synchronizedList(new ArrayList<>());
        if (weekendDays.isEmpty()) {
            return makeup;
        }
        int[] done = {0};
        int total = weekendDays.size();
        ExecutorService pool = Executors.newFixedThreadPool(QUERY_THREADS);
        try {
            for (var date : weekendDays) {
                pool.submit(() -> {
                    try {
                        var fdate = String.format("%d%02d%02d",
                                date.getYear(), date.getMonthValue(), date.getDayOfMonth());
                        var body = getWithRetry(API_URL + fdate);
                        if (body == null) {
                            // 重试耗尽仍无响应：记录失败，不静默吞掉
                            failed.add(date);
                        } else if (body.trim().equals("0")) {
                            makeup.add(date);
                        }
                    } finally {
                        done[0]++;
                        if (progress != null) {
                            try {
                                progress.accept(done[0], total);
                            } catch (Exception ignored) {
                            }
                        }
                    }
                });
            }
        } finally {
            pool.shutdown();
            if (!pool.awaitTermination(2, TimeUnit.MINUTES)) {
                log.warn("补班查询超时，强制关闭线程池");
                pool.shutdownNow();
            }
        }
        if (!failed.isEmpty()) {
            // 有查询彻底失败 → 抛异常，由调用方提示用户重试，避免呈现残缺结果
            throw new IllegalStateException(
                    "有 " + failed.size() + " 个周末的状态查询失败（接口超时或无响应），请点击「生成工作日」重试");
        }
        return makeup;
    }

    /**
     * 带重试的 HTTP GET；彻底失败返回 null。
     * <p>
     * 退避策略：基础间隔 500ms 起步指数增长（500/1000/2000ms），叠加随机抖动，
     * 避免多个失败请求在同一时刻集体重试，从而二次压垮接口。
     */
    private String getWithRetry(String url) {
        for (int attempt = 0; attempt <= MAX_RETRY; attempt++) {
            String body = httpGet(url);
            if (body != null) {
                return body;
            }
            if (attempt < MAX_RETRY) {
                try {
                    // 基础退避 500ms * 2^attempt + [0,300)ms 随机抖动
                    long backoff = 500L * (1L << attempt) + ThreadLocalRandom.current().nextInt(300);
                    Thread.sleep(backoff);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
        return null;
    }

    private String httpGet(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return response.body();
            }
            log.warn("HTTP 请求失败: {} status={}", url, response.statusCode());
        } catch (Exception e) {
            log.warn("HTTP 请求异常: {} - {}", url, e.getMessage());
        }
        return null;
    }

    private boolean isWeekend(LocalDate date) {
        var dow = date.getDayOfWeek();
        return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
    }
}
