package dev.myagent.business.controller;

import dev.myagent.business.dto.agent.AgentError;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class AgentExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<AgentError> status(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).contentType(MediaType.APPLICATION_JSON)
                .body(new AgentError("request_failed", exception.getReason()));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<AgentError> invalid(Exception exception) {
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON)
                .body(new AgentError("invalid_request", "请求参数不正确，请检查消息长度和对话 ID"));
    }
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<AgentError> database(DataAccessException exception) {
        return ResponseEntity.internalServerError().contentType(MediaType.APPLICATION_JSON)
                .body(new AgentError("storage_failed", "无法访问对话存储，请稍后重试"));
    }
}
