package com.example.echo.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.List;
import java.util.Map;

/**
 * 上传超限等异常兜底：返回 JSON 而不是默认 HTML 错误页。
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> uploadTooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.badRequest().body(Map.of(
                "ok", false,
                "errors", List.of("上传文件超过大小限制（单个文件 5MB）")
        ));
    }
}
