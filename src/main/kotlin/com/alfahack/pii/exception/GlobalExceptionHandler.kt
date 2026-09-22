package com.alfahack.pii.exception

import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest

/**
 * Глобальный обработчик ошибок.
 * Возвращает понятные сообщения, не раскрывая внутренние детали.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(ApiException::class)
    fun handleApiException(ex: ApiException, request: WebRequest): ResponseEntity<ErrorResponse> {
        log.warn("API error: status={}, message={}", ex.status.value(), ex.message)
        return ResponseEntity
            .status(ex.status)
            .body(ErrorResponse(ex.status.value(), ex.message))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val message = ex.bindingResult.fieldErrors
            .joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
        log.warn("Validation error: {}", message)
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(HttpStatus.BAD_REQUEST.value(), message))
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableException(ex: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> {
        log.warn("Malformed request body: {}", ex.message)
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(HttpStatus.BAD_REQUEST.value(), "Malformed request body"))
    }

    @ExceptionHandler(CallNotPermittedException::class)
    fun handleCircuitBreakerOpen(ex: CallNotPermittedException): ResponseEntity<ErrorResponse> {
        log.warn("Circuit breaker open, service degraded: {}", ex.javaClass.simpleName)
        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ErrorResponse(HttpStatus.SERVICE_UNAVAILABLE.value(), "Service temporarily unavailable"))
    }

    @ExceptionHandler(DataAccessException::class)
    fun handleDataAccessException(ex: DataAccessException): ResponseEntity<ErrorResponse> {
        log.warn("Data store unavailable: {}", ex.javaClass.simpleName)
        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ErrorResponse(HttpStatus.SERVICE_UNAVAILABLE.value(), "Data store temporarily unavailable"))
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ResponseEntity<ErrorResponse> {
        // Не логируем детали (могут содержать ПД), только класс ошибки
        log.error("Unexpected error: {}", ex.javaClass.simpleName, ex)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal server error"))
    }

    data class ErrorResponse(
        val status: Int,
        val message: String
    )
}