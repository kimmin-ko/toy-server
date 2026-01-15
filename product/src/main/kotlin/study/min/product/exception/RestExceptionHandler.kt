package study.min.product.exception

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant

/**
 * REST API 예외 처리 핸들러
 * - gRPC Status 코드를 HTTP Status 코드로 매핑
 */
@RestControllerAdvice
class RestExceptionHandler {

    /**
     * 재고 부족 예외 처리
     * gRPC RESOURCE_EXHAUSTED → HTTP 409 Conflict
     */
    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(e: IllegalStateException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(
                ErrorResponse(
                    message = e.message ?: "재고 부족",
                    timestamp = Instant.now().toEpochMilli()
                )
            )
    }

    /**
     * 리소스 없음 예외 처리
     * gRPC NOT_FOUND → HTTP 404 Not Found
     */
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(e: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(
                ErrorResponse(
                    message = e.message ?: "리소스를 찾을 수 없습니다",
                    timestamp = Instant.now().toEpochMilli()
                )
            )
    }

    /**
     * 일반 예외 처리
     * gRPC INTERNAL → HTTP 500 Internal Server Error
     */
    @ExceptionHandler(Exception::class)
    fun handleGeneral(e: Exception): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(
                ErrorResponse(
                    message = "서버 오류가 발생했습니다",
                    timestamp = Instant.now().toEpochMilli()
                )
            )
    }
}

/**
 * 에러 응답 DTO
 */
data class ErrorResponse(
    val message: String,
    val timestamp: Long
)
