package com.example.hello_spring.common;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 与请求本身有关的工具：判断是否 HTTPS、取客户端真实 IP。
 *
 * <p>应用只监听 {@code 127.0.0.1}，真实请求都来自本机 nginx，所以
 * {@code request.isSecure()} 永远是 false、{@code getRemoteAddr()} 拿到的永远是本机地址，
 * 这两个信息都得从 nginx 转发的头里取。
 */
public final class RequestSecurity {

    private RequestSecurity() {
    }

    /**
     * 判断这次请求是不是走的 HTTPS。
     *
     * <p>优先读 nginx 写入的 {@code X-Forwarded-Proto}（值是 {@code $scheme}），
     * 该头由 nginx 覆盖写入，客户端伪造不了；
     * 本地开发直接访问 {@code localhost:8080}（没有 nginx、也没有这个头）时，
     * 退化用 Servlet 自己的判断，所以本地用 https 调试也不会被误判。
     *
     * @return true 表示这次请求来自 HTTPS
     */
    public static boolean isSecure(HttpServletRequest request) {
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        if (forwardedProto != null && !forwardedProto.isBlank()) {
            return "https".equalsIgnoreCase(forwardedProto.trim());
        }
        return request.isSecure();
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
     *
     * <p>浏览量去重（{@link ArticleViewRecorder}）与登录 IP 白名单都用它，
     * 保证两处判断口径一致。
     */
    public static String clientIp(HttpServletRequest request) {
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
}
