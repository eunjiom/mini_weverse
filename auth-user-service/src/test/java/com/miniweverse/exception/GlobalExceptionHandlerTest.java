package com.miniweverse.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniweverse.common.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void BusinessException은_errorCode의_httpStatus와_코드로_응답한다() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(new DuplicateFollowException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getError().getCode()).isEqualTo("AUTH_USER_001");
        assertThat(response.getBody().getError().getMessage()).isEqualTo("이미 팔로우한 아티스트입니다.");
    }

    @Test
    void 유효성_검증_실패는_400과_필드별_에러_메시지로_응답한다() throws NoSuchMethodException {
        MethodParameter methodParameter = new MethodParameter(
                DummyTarget.class.getDeclaredMethod("method", String.class), 0);
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new DummyTarget(), "dummyTarget");
        bindingResult.addError(new FieldError("dummyTarget", "email", "이메일은 필수입니다."));
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(methodParameter, bindingResult);

        ResponseEntity<ApiResponse<Void>> response = handler.handleValidationException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getError().getMessage()).contains("email: 이메일은 필수입니다.");
    }

    private static class DummyTarget {
        void method(String arg) {
        }
    }
}
