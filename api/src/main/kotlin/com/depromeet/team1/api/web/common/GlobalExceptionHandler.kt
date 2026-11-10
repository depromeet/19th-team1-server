package com.depromeet.team1.api.web.common

import com.depromeet.team1.core.common.error.BusinessException
import com.depromeet.team1.core.common.error.CommonErrorCode
import com.depromeet.team1.core.common.error.ErrorCode
import com.depromeet.team1.core.common.error.ErrorType
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.ErrorResponse as SpringErrorResponse
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException

/**
 * 모든 예외를 실패 응답 형식으로 바꾸는 한 곳. 응답 메시지는 에러 코드의 문구만 쓰고, 예외의 상세 내용은 로그에만 남긴다.
 */
@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(BusinessException::class)
    fun handleBusiness(e: BusinessException): ResponseEntity<ErrorResponse> = respond(e.errorCode, e)

    @ExceptionHandler(
        MethodArgumentNotValidException::class,
        HttpMessageNotReadableException::class,
        MethodArgumentTypeMismatchException::class,
        MissingServletRequestParameterException::class,
    )
    fun handleInvalidInput(e: Exception): ResponseEntity<ErrorResponse> = respond(CommonErrorCode.INVALID_INPUT, e)

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNotFound(e: NoResourceFoundException): ResponseEntity<ErrorResponse> =
        respond(CommonErrorCode.RESOURCE_NOT_FOUND, e)

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotAllowed(e: HttpRequestMethodNotSupportedException): ResponseEntity<ErrorResponse> =
        respond(CommonErrorCode.METHOD_NOT_ALLOWED, e, HttpStatus.METHOD_NOT_ALLOWED)

    /** 위에서 다루지 않은 예외. Spring MVC 가 던지는 예외(지원하지 않는 Content-Type 등)는 그 예외가 가진 상태 코드를 따른다. */
    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception): ResponseEntity<ErrorResponse> {
        if (e is SpringErrorResponse) {
            val status = HttpStatus.valueOf(e.statusCode.value())
            val errorCode =
                when {
                    status == HttpStatus.NOT_FOUND -> CommonErrorCode.RESOURCE_NOT_FOUND
                    status.is4xxClientError -> CommonErrorCode.INVALID_INPUT
                    else -> CommonErrorCode.INTERNAL_ERROR
                }
            return respond(errorCode, e, status)
        }
        return respond(CommonErrorCode.INTERNAL_ERROR, e)
    }

    private fun respond(
        errorCode: ErrorCode,
        e: Exception,
        status: HttpStatus = errorCode.type.toHttpStatus(),
    ): ResponseEntity<ErrorResponse> {
        if (errorCode.type.serverFault) {
            log.error("[{}] {}", errorCode.name, e.message, e)
        } else {
            log.warn("[{}] {}", errorCode.name, e.message)
        }
        return ResponseEntity.status(status).body(ErrorResponse.of(errorCode))
    }

    private fun ErrorType.toHttpStatus(): HttpStatus =
        when (this) {
            ErrorType.INVALID_INPUT -> HttpStatus.BAD_REQUEST
            ErrorType.UNAUTHORIZED -> HttpStatus.UNAUTHORIZED
            ErrorType.FORBIDDEN -> HttpStatus.FORBIDDEN
            ErrorType.NOT_FOUND -> HttpStatus.NOT_FOUND
            ErrorType.CONFLICT -> HttpStatus.CONFLICT
            ErrorType.EXTERNAL_FAILURE -> HttpStatus.BAD_GATEWAY
            ErrorType.INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR
        }
}
