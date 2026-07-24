package com.miniweverse.common.exception.handler;

import com.miniweverse.common.exception.BusinessException;
import com.miniweverse.common.exception.CommonErrorCode;
import com.miniweverse.common.exception.ErrorCode;
import com.miniweverse.common.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * common 모듈 소유 타입(BusinessException/ErrorCode/ApiResponse)에만 의존해서 서비스별
 * 재구현 없이 여러 서비스가 그대로 재사용할 수 있다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(ApiResponse.error(errorCode.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(CommonErrorCode.VALIDATION_ERROR.getHttpStatus())
                .body(ApiResponse.error(CommonErrorCode.VALIDATION_ERROR.getCode(), message));
    }

    /**
     * @Validated + @RequestParam/@PathVariable 제약(예: @Min, @Max) 위반 시 발생.
     * @Valid @RequestBody 위반(MethodArgumentNotValidException)과는 별개 예외 타입이라 따로 처리한다.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(CommonErrorCode.VALIDATION_ERROR.getHttpStatus())
                .body(ApiResponse.error(CommonErrorCode.VALIDATION_ERROR.getCode(), message));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameterException(MissingServletRequestParameterException e) {
        String message = e.getParameterName() + ": 필수 파라미터입니다.";
        return ResponseEntity.status(CommonErrorCode.VALIDATION_ERROR.getHttpStatus())
                .body(ApiResponse.error(CommonErrorCode.VALIDATION_ERROR.getCode(), message));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatchException(MethodArgumentTypeMismatchException e) {
        String message = e.getName() + ": 요청 값의 형식이 올바르지 않습니다.";
        return ResponseEntity.status(CommonErrorCode.VALIDATION_ERROR.getHttpStatus())
                .body(ApiResponse.error(CommonErrorCode.VALIDATION_ERROR.getCode(), message));
    }

    /**
     * 매핑된 컨트롤러가 없을 때 Spring이 던지는 예외 — 이걸 따로 안 잡으면 아래 catch-all에
     * 걸려서 404가 아니라 500으로 응답된다.
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNotFoundException(Exception e) {
        return ResponseEntity.status(CommonErrorCode.NOT_FOUND.getHttpStatus())
                .body(ApiResponse.error(CommonErrorCode.NOT_FOUND));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        // 예상 못한(도메인 예외로 분류 안 된) 예외라 원인을 반드시 남겨야 한다 — 지금까지는
        // 아무 로그 없이 그냥 500만 내려가서 실제 원인 파악이 안 되는 문제가 있었다.
        log.error("처리되지 않은 예외 발생", e);
        return ResponseEntity.status(CommonErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus())
                .body(ApiResponse.error(CommonErrorCode.INTERNAL_SERVER_ERROR));
    }
}
