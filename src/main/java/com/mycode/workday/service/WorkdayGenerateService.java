package com.mycode.workday.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mycode.workday.model.Workday;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

/**
 * 工作日生成服务。
 * <p>
 * 复用原项目 WorkHoliday.execute 的节假日判断逻辑：
 * 1. 调用 http://tool.bitefu.net/jiari/?d={year} 获取全年假期标记（值: 1=假期, 2=周末）
 * 2. 周末（约 104 天）需要单独查询是否补班：调用 ?d={yyyymmdd}，返回 0 表示补班上班
 * <p>
 * <b>性能优化</b>：原实现把全年 104 个周末日逐个<b>串行</b>发起 HTTP 请求，
 * 单年耗时可达数十秒。现改为：
 * <ul>
 *   <li>全年数据一次性拉取并解析为 {@link Map}，本地 O(1) 判断；</li>
 *   <li>仅对「周末」调用接口查询补班，且用固定线程池<b>并发</b>发起；</li>
 *   <li>通过 {@link IntConsumer} 回调上报进度（已完成/总数），供 UI 显示进度条。</li>
 * </ul>
 */
@Service
public class WorkdayGenerateService {

    private static final Logger log = LoggerFactory.getLogger(WorkdayGenerateService.class);
    private static final String API_URL = "http://tool.bitefu.net/jiari/?d=";

    /** 补班查询的并发线程数 */
    private static final int QUERY_THREADS = 8;
    /** 单次补班查询等待所有任务的最长时间（秒） */
    private static final long AWAIT_MINUTES = 2L;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 生成指定年份的工作日列表（不带进度回调）。
     */
    public List<Workday> generate(int year) throws Exception {
        return generate(year, null);
    }

    /**
     * 生成指定年份的工作日列表。
     *
     * @param year            年份
     * @param progressCallback 进度回调，参数为「已完成数量」；仅用于 UI 进度展示，可为 null。
     *                         回调在<b>工作线程</b>触发，调用方需自行切换回 UI 线程。
     */
    public List<Workday> generate(int year, IntConsumer progressCallback) throws Exception {
        // 1) 一次性获取全年假期标记，本地判断，避免逐天请求
        Map<String, Integer> holidayMap = fetchHolidayMap(year);
        log.info("获取到 {} 年假期数据: {} 条", year, holidayMap.size());

        // 2) 先收集所有「周末」日期：只有周末才可能补班，需要单独查询
        List<LocalDate> weekendDates = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            int daysInMonth = YearMonth.of(year, month).lengthOfMonth();
            for (int day = 1; day <= daysInMonth; day++) {
                LocalDate date = LocalDate.of(year, month, day);
                if (isWeekend(date)) {
                    weekendDates.add(date);
                }
            }
        }

        // 3) 并发查询每个周末是否为补班日。返回「是补班」的日期集合
        Map<LocalDate, Boolean> makeupWorkdays = queryMakeupWorkdays(year, weekendDates, progressCallback);

        // 4) 组装工作日列表：工作日(非法定假期) + 补班日
        List<Workday> workdays = new ArrayList<>();
        int no = 1;
        for (int month = 1; month <= 12; month++) {
            int daysInMonth = YearMonth.of(year, month).lengthOfMonth();
            for (int day = 1; day <= daysInMonth; day++) {
                LocalDate date = LocalDate.of(year, month, day);
                boolean isWorkday;
                if (isWeekend(date)) {
                    // 周末：仅当为补班日时才计入工作日
                    isWorkday = makeupWorkdays.getOrDefault(date, Boolean.FALSE);
                } else {
                    // 工作日：排除法定假期（holidayMap 中标记为 1）
                    String md = String.format("%02d%02d", month, day);
                    Integer flag = holidayMap.get(md);
                    isWorkday = flag == null || flag != 1;
                }
                if (isWorkday) {
                    workdays.add(new Workday(0, no++, date, month, year));
                }
            }
        }
        return workdays;
    }

    /**
     * 并发查询给定周末日期中哪些是补班日（返回 0）。
     * 查询过程中通过 {@code progressCallback} 上报累计已完成数量。
     */
    private Map<LocalDate, Boolean> queryMakeupWorkdays(int year,
                                                        List<LocalDate> weekendDates,
                                                        IntConsumer progressCallback) throws InterruptedException {
        Map<LocalDate, Boolean> result = new ConcurrentHashMap<>();
        if (weekendDates.isEmpty()) {
            return result;
        }

        int[] done = {0};
        int total = weekendDates.size();
        ExecutorService pool = Executors.newFixedThreadPool(QUERY_THREADS);
        try {
            for (LocalDate date : weekendDates) {
                pool.submit(() -> {
                    try {
                        String fdate = String.format("%d%02d%02d", year, date.getMonthValue(), date.getDayOfMonth());
                        String body = httpGet(API_URL + fdate);
                        // 返回 "0" 表示补班上班
                        if (body != null && body.trim().equals("0")) {
                            result.put(date, Boolean.TRUE);
                        }
                    } finally {
                        done[0]++;
                        if (progressCallback != null) {
                            try {
                                progressCallback.accept(done[0]);
                            } catch (Exception ignored) {
                                // 回调异常不应影响生成流程
                            }
                        }
                    }
                });
            }
        } finally {
            pool.shutdown();
            // 等待所有补班查询完成
            if (!pool.awaitTermination(AWAIT_MINUTES, TimeUnit.MINUTES)) {
                log.warn("补班查询未在 {} 分钟内完成，强制关闭线程池", AWAIT_MINUTES);
                pool.shutdownNow();
            }
        }
        log.info("{} 年共识别补班日 {} 个", year, result.size());
        return result;
    }

    /**
     * 获取全年假期标记，解析为 {@code "MMdd" -> 状态码} 的 Map。
     * 状态码：1=法定假期, 2=周末休息（由 API 约定）。
     */
    private Map<String, Integer> fetchHolidayMap(int year) throws Exception {
        Map<String, Integer> map = new ConcurrentHashMap<>();
        String body = httpGet(API_URL + year);
        if (body == null) {
            return map;
        }
        JsonNode yearNode = objectMapper.readTree(body).get(String.valueOf(year));
        if (yearNode == null || !yearNode.isObject()) {
            return map;
        }
        yearNode.fields().forEachRemaining(e -> {
            try {
                map.put(e.getKey(), e.getValue().asInt());
            } catch (Exception ignored) {
            }
        });
        return map;
    }

    private String httpGet(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return response.body();
            }
            log.warn("HTTP {} 请求失败: status={}", url, response.statusCode());
        } catch (Exception e) {
            log.warn("HTTP 请求异常: {} - {}", url, e.getMessage());
        }
        return null;
    }

    /**
     * 判断是否为周末（周六或周日）。
     */
    private boolean isWeekend(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
    }
}
