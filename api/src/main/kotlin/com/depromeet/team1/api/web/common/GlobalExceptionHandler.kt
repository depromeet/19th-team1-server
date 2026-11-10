package com.depromeet.team1.api.web.common

import com.depromeet.team1.core.common.error.AppException
import com.depromeet.team1.core.common.error.CommonError
import com.depromeet.team1.core.common.error.ErrorCategory
import com.depromeet.team1.core.common.error.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.ErrorResponse as SpringErrorResponse

/**
 * 모든 예외를 실패 응답 형식으로 바꾸는 한 곳.
 * 응답 메시지는 에러 코드의 문구만 쓰고, 예외의 상세 내용은 로그에만 남긴다. 500대 응답은 ERROR, 나머지는 WARN 으로 기록한다.
 */
@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(AppException::class)
    fun handleApp(e: AppException): ResponseEntity<ErrorResponse> =
        respond(e.errorCode, e.errorCode.category.toHttpStatus(), e)

    @ExceptionHandler(
        MethodArgumentNotValidException::class,
        HttpMessageNotReadableException::class,
        MethodArgumentTypeMismatchException::class,
        MissingServletRequestParameterException::class,
    )
    fun handleInvalidInput(e: Exception): ResponseEntity<ErrorResponse> =
        respond(CommonError.INVALID_INPUT, HttpStatus.BAD_REQUEST, e)

    /**
     * 위에서 다루지 않은 예외. Spring MVC 가 던지는 예외(없는 경로, 지원하지 않는 요청 방식 등)는 그 예외가 가진 상태 코드를 따르고,
     * 그 밖의 예외는 500 으로 응답한다.
     */
    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception): ResponseEntity<ErrorResponse> {
        val status = (e as? SpringErrorResponse)?.let { HttpStatus.valueOf(it.statusCode.value()) }
            ?: HttpStatus.INTERNAL_SERVER_ERROR
        val errorCode = when {
            status == HttpStatus.NOT_FOUND -> CommonError.RESOURCE_NOT_FOUND
            status.is4xxClientError -> CommonError.INVALID_INPUT
            else -> CommonError.INTERNAL_ERROR
        }
        return respond(errorCode, status, e)
    }

    private fun respond(errorCode: ErrorCode, status: HttpStatus, e: Exception): ResponseEntity<ErrorResponse> {
        if (status.is5xxServerError) {
            log.error("[{}] {}", errorCode.name, e.message, e)
        } else {
            log.warn("[{}] {}", errorCode.name, e.message)
        }
        return ResponseEntity.status(status).body(ErrorResponse.of(errorCode))
    }

    private fun ErrorCategory.toHttpStatus(): HttpStatus = when (this) {
        ErrorCategory.BAD_REQUEST -> HttpStatus.BAD_REQUEST
        ErrorCategory.NOT_FOUND -> HttpStatus.NOT_FOUND
        ErrorCategory.CONFLICT -> HttpStatus.CONFLICT
        ErrorCategory.INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR
    }
}
