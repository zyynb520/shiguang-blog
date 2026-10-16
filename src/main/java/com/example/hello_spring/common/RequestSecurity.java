package com.example.hello_spring.common;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 判断一个请求是不是走的 HTTPS。
 *
 * <p>应用只监听 {@code 127.0.0.1}，真实请求都来自本机 nginx，所以
 * {@code request.isSecure()} 永远是 false —— nginx 把原始协议写在
 * {@code X-Forwarded-Proto} 里（值是 {@code $scheme}），这里优先读它。
 * 该头由 nginx 覆盖写入，客户端伪造不了；应用本身也不对外暴露。
 *
 * <p>本地开发直接访问 {@code localhost:8080}（没有 nginx、也没有这个头）时，
 * 会退化用 Servlet 自己的判断，所以本地用 https 调试也不会被误判。
 */
public final class RequestSecurity {

    private RequestSecurity() {
    }

    /** @return true 表示这次请求来自 HTTPS */
    public static boolean isSecure(HttpServletRequest request) {
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        if (forwardedProto != null && !forwardedProto.isBlank()) {
            return "https".equalsIgnoreCase(forwardedProto.trim());
        }
        return request.isSecure();
    }
}
