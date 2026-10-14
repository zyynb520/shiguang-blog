package com.example.hello_spring.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 全局异常处理：让「意外」也走统一的 {@code { code, msg, data }} 信封。
 *
 * <p>职责边界：
 * <ul>
 *   <li>业务错误（code 400）在 Controller 里正常 return {@code Result.error(...)}，走不到这里；</li>
 *   <li>这里兜的是意外情况：请求体不是合法 JSON、必填参数没传、参数类型不对、
 *       上传超过大小限制，以及任何没预料到的异常。</li>
 * </ul>
 *
 * <p>两条原则：
 * <ol>
 *   <li><b>未预料的异常写完整堆栈到日志</b>（生产环境日志进 journald），
 *       但只把一句人话返回给前端，不泄露堆栈、SQL、文件路径等内部细节；</li>
 *   <li><b>HTTP 状态码如实返回</b>（400 / 413 / 500）。前端 {@code api.js} 对任意状态码
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

    /** 兜底：任何没被上面单独处理的异常 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnexpected(Exception e) {
        // 这里是唯一需要看完整堆栈的地方，日志级别用 error
        log.error("未处理的异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(500, "服务器开小差了，请稍后重试"));
    }

    private static ResponseEntity<Result<Void>> badRequest(String msg) {
        return ResponseEntity.badRequest().body(Result.error(msg));
    }
}
