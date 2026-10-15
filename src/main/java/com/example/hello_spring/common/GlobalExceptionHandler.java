package com.example.hello_spring.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理：让「意外」也走统一的 {@code { code, msg, data }} 信封。
 *
 * <p>职责边界：
 * <ul>
 *   <li>业务错误（code 400）在 Controller 里正常 return {@code Result.error(...)}，走不到这里；</li>
 *   <li>这里兜的是意外情况：请求体不是合法 JSON、必填参数没传、参数类型不对、
 *       上传超过大小限制、路径不存在（404），以及任何没预料到的异常。</li>
 * </ul>
 *
 * <p>两条原则：
 * <ol>
 *   <li><b>未预料的异常写完整堆栈到日志</b>（生产环境日志进 journald），
 *       但只把一句人话返回给前端，不泄露堆栈、SQL、文件路径等内部细节；</li>
 *   <li><b>HTTP 状态码如实返回</b>（400 / 404 / 413 / 500）。前端 {@code api.js} 对任意状态码
 *       都会尝试解析 JSON 信封并取 {@code msg} 弹提示，所以用户看到的是友好文案而不是
 *       「服务器返回异常（HTTP 500）」。</li>
 * </ol>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 请求体不是合法 JSON，或字段类型对不上 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleUnreadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return badRequest("请求格式不正确");
    }

    /** 必填的查询参数没传，例如 /comment/list 少了 articleId */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingParam(MissingServletRequestParameterException e) {
        return badRequest("缺少参数：" + e.getParameterName());
    }

    /** 参数类型不对，例如 /article/detail/abc（期望数字） */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return badRequest("参数格式不正确：" + e.getName());
    }

    /**
     * 上传文件超过大小限制。
     *
     * <p>应用的单个文件上限是 50MB、单次请求 60MB；nginx 也设了 60m。
     * 50~60MB 这一档由这里接住，超过 60MB 会被 nginx 直接挡掉（返回 nginx 自己的页面）。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<Void>> handleUploadTooLarge(MaxUploadSizeExceededException e) {
        log.warn("上传超过大小限制: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Result.error(413, "文件太大了，单个文件最大 50MB"));
    }

    /**
     * 接口或静态资源不存在 → 404。
     *
     * <p>必须单独处理：Spring 找不到静态资源时会抛这个异常，一旦被下面的兜底 handler 接住，
     * 所有 404 都会变成 500 —— 语义错了不说，别人随便扫几个不存在的路径就能刷出一屏 ERROR 日志。
     *
     * <p>刻意<b>不记日志</b>：正常的 404（旧链接、爬虫、扫描器）不该污染 error 日志。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResource(NoResourceFoundException e) {
        return notFound();
    }

    /** 同上；当由 DispatcherServlet 直接抛 NoHandlerFoundException 时走这里 */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Result<Void>> handleNoHandler(NoHandlerFoundException e) {
        return notFound();
    }

    /**
     * HTTP 方法不对，例如用 GET 访问只接受 POST 的 /article/delete → 405。
     *
     * <p>单独处理的原因和 404 一样：不处理就会被兜底成 500。
     * 顺带把 405 该有的 {@code Allow} 响应头补上，告诉客户端支持哪些方法。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        String[] supported = e.getSupportedMethods();
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
        if (supported != null && supported.length > 0) {
            // 405 按规范应该带 Allow 头，告诉客户端支持哪些方法
            builder.header(HttpHeaders.ALLOW, String.join(", ", supported));
        }
        return builder.body(Result.error(405, "该接口不支持这个请求方法"));
    }

    /** 请求体类型不对，例如该传 JSON 却传了 text/plain → 415 */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMediaType(HttpMediaTypeNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(Result.error(415, "请求体格式不支持"));
    }

    /**
     * 兜底：任何没被上面单独处理的异常。
     *
     * <p>先识别 Spring MVC 自带的异常 —— 它们都实现了 {@link ErrorResponse} 并自带正确的状态码
     * （方法不允许、媒体类型不支持、缺少 multipart 字段……）。这类属于「客户端把请求发错了」，
     * 必须按原状态码返回，否则就会像 404/405 那样被误报成 500：
     * 语义错了，而且别人随便构造几个畸形请求就能把 error 日志刷满。
     *
     * <p>只有真正没预料到的异常才记完整堆栈 + 返回 500。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnexpected(Exception e) {
        if (e instanceof ErrorResponse response && response.getStatusCode().is4xxClientError()) {
            HttpStatusCode status = response.getStatusCode();
            log.warn("请求被拒绝（HTTP {}）: {}", status.value(), e.getMessage());
            return ResponseEntity.status(status).body(Result.error(status.value(), clientMessageOf(response, e)));
        }
        // 这里是唯一需要看完整堆栈的地方，日志级别用 error
        log.error("未处理的异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(500, "服务器开小差了，请稍后重试"));
    }

    /** 4xx 的提示文案：优先用 Spring 给的原因，其次异常消息，最后兜一句通用的 */
    private static String clientMessageOf(ErrorResponse response, Exception e) {
        String detail = response.getBody() == null ? null : response.getBody().getDetail();
        if (detail != null && !detail.isBlank()) {
            return detail;
        }
        String message = e.getMessage();
        return (message == null || message.isBlank()) ? "请求不被支持" : message;
    }

    private static ResponseEntity<Result<Void>> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "接口或资源不存在"));
    }

    private static ResponseEntity<Result<Void>> badRequest(String msg) {
        return ResponseEntity.badRequest().body(Result.error(msg));
    }
}
