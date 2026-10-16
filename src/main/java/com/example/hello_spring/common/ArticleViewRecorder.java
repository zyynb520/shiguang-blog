package com.example.hello_spring.common;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文章浏览量的计数策略：决定「这一次浏览该不该 +1」。
 *
 * <p>要解决的问题：真实访客算一次，而刷新、爬虫、浏览器预取、自己反复点开都不算。
 *
 * <p>两层判定，都通过才计数：
 * <ol>
 *   <li><b>浏览器维度（Cookie，24 小时）</b>：看过的文章 id 记在 Cookie 里，同一浏览器
 *       24 小时内重复打开同一篇不再计数。状态存在客户端，所以服务重启不影响、
 *       也不需要为此引入 Redis。</li>
 *   <li><b>IP 维度（内存，10 分钟）</b>：同一 IP 对同一篇文章 10 分钟内只计一次。
 *       防的是「清掉 Cookie 反复刷」和爬虫的快速抓取。纯内存、重启即失效，
 *       这是刻意的取舍：它只是防刷的辅助手段，不值得为它落库。</li>
 * </ol>
 *
 * <p>为什么不做「每个访客落一行记录」：那要新增表、每次浏览多一次写库，
 * 还会记录访客标识（隐私成本）。当前方案零表结构变更、零新增依赖、
 * 正常访客产生的写库次数从「每次刷新一次」降到「每人每篇每天一次」。
 *
 * <p><b>它不是安全控制</b>：Cookie 可以清、IP 可以换，浏览量本身只影响展示，
 * 不值得为它上更重的机制。
 */
@Component
public class ArticleViewRecorder {

    /** 记录「看过哪些文章」的 Cookie 名 */
    private static final String COOKIE_NAME = "sg_viewed";

    /** Cookie 与「浏览器维度去重」的有效期 */
    private static final Duration BROWSER_WINDOW = Duration.ofHours(24);

    /** 同一 IP 对同一篇文章的最短计数间隔 */
    private static final Duration IP_WINDOW = Duration.ofMinutes(10);

    /** Cookie 里最多记多少个文章 id，避免请求头无限增长（超了丢最旧的） */
    private static final int MAX_REMEMBERED = 60;

    /** 内存表超过这个规模就顺手清理过期项，避免被随机 IP 刷爆内存 */
    private static final int SWEEP_THRESHOLD = 5000;

    /** key = "文章id|IP"，value = 上次计数时间戳 */
    private final Map<String, Long> recentByIp = new ConcurrentHashMap<>();

    /**
     * 判定这次浏览是否应该计数；如果该计，顺便把 Cookie 更新掉。
     *
     * @param request   当前请求（读取已有 Cookie 与客户端 IP）
     * @param response  当前响应（写入更新后的 Cookie）
     * @param articleId 文章 id
     * @return true 表示调用方应该执行 {@code view_count + 1}
     */
    public boolean shouldCount(HttpServletRequest request, HttpServletResponse response, int articleId) {
        List<Integer> viewed = readViewedIds(request);

        // 第一层：这个浏览器 24 小时内看过这篇了
        if (viewed.contains(articleId)) {
            writeViewedIds(response, viewed); // 只续期，不计数
            return false;
        }

        // 第二层：这个 IP 刚看过这篇（可能是清了 Cookie 又来刷，或者是爬虫）
        if (!allowByIp(articleId, clientIp(request))) {
            return false;
        }

        // 两层都通过：记账并计数
        viewed.add(articleId);
        writeViewedIds(response, viewed);
        return true;
    }

    /**
     * 取客户端真实 IP。
     *
     * <p>应用只监听 127.0.0.1，请求都来自本机 nginx，所以 {@code getRemoteAddr()}
     * 拿到的是 127.0.0.1，必须读 nginx 转发的头：
     * <ul>
     *   <li>优先 {@code X-Real-IP}：nginx 里写的是 {@code $remote_addr}，
     *       <b>客户端伪造不了</b>；</li>
     *   <li>退化取 {@code X-Forwarded-For} 的<b>最后一段</b>：nginx 用的是
     *       {@code $proxy_add_x_forwarded_for}，它会把客户端自己传来的值留在前面，
     *       所以第一段可以被伪造，只有最后一段（nginx 追加的真实地址）可信。</li>
     * </ul>
     */
    private static String clientIp(HttpServletRequest request) {
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int lastComma = forwarded.lastIndexOf(',');
            String candidate = lastComma >= 0 ? forwarded.substring(lastComma + 1) : forwarded;
            if (!candidate.isBlank()) {
                return candidate.trim();
            }
        }
        return request.getRemoteAddr();
    }

    private boolean allowByIp(int articleId, String ip) {
        long now = System.currentTimeMillis();
        String key = articleId + "|" + ip;
        Long previous = recentByIp.get(key);
        if (previous != null && now - previous < IP_WINDOW.toMillis()) {
            return false;
        }
        recentByIp.put(key, now);
        sweepIfNeeded(now);
        return true;
    }

    /** 只在表变大时才遍历清理，避免每次请求都全表扫描 */
    private void sweepIfNeeded(long now) {
        if (recentByIp.size() < SWEEP_THRESHOLD) {
            return;
        }
        long deadline = now - IP_WINDOW.toMillis();
        recentByIp.entrySet().removeIf(entry -> entry.getValue() < deadline);
    }

    /** 解析 Cookie 里的 id 列表；解析不了的（被改过、格式不对）一律当作没记录 */
    private static List<Integer> readViewedIds(HttpServletRequest request) {
        List<Integer> ids = new ArrayList<>();
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return ids;
        }
        for (Cookie cookie : cookies) {
            if (!COOKIE_NAME.equals(cookie.getName())) {
                continue;
            }
            String value = cookie.getValue();
            if (value == null || value.isBlank()) {
                continue;
            }
            for (String part : value.split(",")) {
                try {
                    ids.add(Integer.parseInt(part.trim()));
                } catch (NumberFormatException ignored) {
                    // 单个片段坏了就跳过，不影响其它片段
                }
            }
        }
        return ids;
    }

    /** 写回 Cookie；超过上限时只保留最近看过的若干个 */
    private static void writeViewedIds(HttpServletResponse response, List<Integer> ids) {
        if (ids.size() > MAX_REMEMBERED) {
            ids = new ArrayList<>(ids.subList(ids.size() - MAX_REMEMBERED, ids.size()));
        }
        StringBuilder value = new StringBuilder();
        for (int id : ids) {
            if (value.length() > 0) {
                value.append(',');
            }
            value.append(id);
        }

        // 注意：暂时不能加 Secure —— 站点还没上 HTTPS，加了浏览器就不会回传，去重会失效。
        // 等配好 HTTPS 后把 .secure(true) 打开。
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, value.toString())
                .path("/")
                .maxAge(BROWSER_WINDOW)
                .httpOnly(true)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
