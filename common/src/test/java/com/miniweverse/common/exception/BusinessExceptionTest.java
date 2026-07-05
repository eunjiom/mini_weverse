package com.miniweverse.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

@Tag("unit")
class BusinessExceptionTest {

    private enum TestErrorCode implements ErrorCode {
        SAMPLE("TEST_001", "샘플 에러", HttpStatus.BAD_REQUEST);

        private final String code;
        private final String message;
        private final HttpStatus httpStatus;

        TestErrorCode(String code, String message, HttpStatus httpStatus) {
            this.code = code;
            this.message = message;
            this.httpStatus = httpStatus;
        }

        @Override
        public String getCode() {
            return code;
        }

        @Override
        public String getMessage() {
            return message;
        }

        @Override
        public HttpStatus getHttpStatus() {
            return httpStatus;
        }
    }

    private static class TestException extends BusinessException {
        TestException() {
            super(TestErrorCode.SAMPLE);
        }

        TestException(String message) {
            super(TestErrorCode.SAMPLE, message);
        }
    }

    @Test
    void errorCode의_메시지를_기본_메시지로_사용한다() {
        TestException exception = new TestException();

        assertThat(exception.getMessage()).isEqualTo("샘플 에러");
        assertThat(exception.getErrorCode()).isEqualTo(TestErrorCode.SAMPLE);
    }

    @Test
    void 커스텀_메시지로_덮어쓸_수_있다() {
        TestException exception = new TestException("커스텀 메시지");

        assertThat(exception.getMessage()).isEqualTo("커스텀 메시지");
        assertThat(exception.getErrorCode()).isEqualTo(TestErrorCode.SAMPLE);
    }
}
