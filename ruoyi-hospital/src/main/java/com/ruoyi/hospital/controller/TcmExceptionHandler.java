package com.ruoyi.hospital.controller;

import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.ruoyi.common.exception.ServiceException;

/**
 * TCM 前端专用异常处理器
 *
 * 前端 api.js 使用 response.ok + payload.message 判断错误，
 * 因此需要返回实际的 HTTP 状态码（非 200）和 { message: "..." } 格式。
 * 限定范围为 com.ruoyi.hospital.controller 下的 Controller。
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.ruoyi.hospital.controller")
public class TcmExceptionHandler
{
    private static final Logger log = LoggerFactory.getLogger(TcmExceptionHandler.class);

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<Map<String, Object>> handleServiceException(ServiceException e)
    {
        log.error("TCM业务异常: {}", e.getMessage());
        Map<String, Object> body = new HashMap<>();
        body.put("message", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(BadCredentialsException e)
    {
        log.error("TCM认证失败: {}", e.getMessage());
        Map<String, Object> body = new HashMap<>();
        body.put("message", "邮箱或密码错误");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException e)
    {
        Map<String, Object> body = new HashMap<>();
        body.put("message", "没有权限访问");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception e, HttpServletRequest request)
    {
        log.error("TCM未知异常: {} - {}", request.getRequestURI(), e.getMessage(), e);
        Map<String, Object> body = new HashMap<>();
        body.put("message", "服务器内部错误，请联系管理员");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
