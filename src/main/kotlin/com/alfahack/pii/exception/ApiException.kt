package com.alfahack.pii.exception

import org.springframework.http.HttpStatus

/**
 * Базовое исключение API с HTTP-статусом.
 */
open class ApiException(
    val status: HttpStatus,
    override val message: String
) : RuntimeException(message)

/**
 * Соответствие по payload_id не найдено (для демаскирования).
 */
class CorrelationNotFoundException(payloadId: String) :
    ApiException(HttpStatus.NOT_FOUND, "Correlation not found for payload_id: $payloadId")

/**
 * Неизвестная или отключённая система.
 */
class SystemNotAllowedException(systemId: String) :
    ApiException(HttpStatus.FORBIDDEN, "System not allowed: $systemId")

/**
 * Слишком много запросов (429).
 */
class RateLimitException(val retryAfterSeconds: Long) :
    ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Retry after $retryAfterSeconds seconds")

/**
 * Payload превышает допустимый размер (400).
 */
class PayloadTooLargeException(maxSize: Long) :
    ApiException(HttpStatus.BAD_REQUEST, "Payload exceeds maximum allowed size of $maxSize characters")

/**
 * Внутренняя ошибка сервиса.
 */
class InternalProcessingException(message: String, cause: Throwable? = null) :
    ApiException(HttpStatus.INTERNAL_SERVER_ERROR, message)