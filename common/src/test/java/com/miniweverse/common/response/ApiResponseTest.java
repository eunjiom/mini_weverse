package com.miniweverse.common.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniweverse.common.exception.ErrorCode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

@Tag("unit")
class ApiResponseTest {

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

    @Test
    void success_응답은_success가_true이고_data를_담는다() {
        ApiResponse<String> response = ApiResponse.success("hello");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEqualTo("hello");
        assertThat(response.getError()).isNull();
    }

    @Test
    void error_응답은_success가_false이고_errorCode_정보를_담는다() {
        ApiResponse<Void> response = ApiResponse.error(TestErrorCode.SAMPLE);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getData()).isNull();
        assertThat(response.getError().getCode()).isEqualTo("TEST_001");
        assertThat(response.getError().getMessage()).isEqualTo("샘플 에러");
    }
}
