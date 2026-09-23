package com.alfahack.pii.config

import com.alfahack.pii.exception.RateLimitException
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Фильтр rate limiting для эндпоинта /process.
 *
 * При превышении лимита возвращает 429 с заголовком Retry-After.
 */
@Component
class RateLimitFilter(
    private val rateLimitService: RateLimitService,
    meterRegistry: MeterRegistry,
) : OncePerRequestFilter() {
    private val rateLimitedTotal: Counter =
        Counter
            .builder("pii_rate_limited_total")
            .description("Rate limited requests (429)")
            .register(meterRegistry)

    override fun shouldNotFilter(request: HttpServletRequest): Boolean = request.requestURI != "/process"

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        try {
            rateLimitService.tryConsume()
            filterChain.doFilter(request, response)
        } catch (ex: RateLimitException) {
            rateLimitedTotal.increment()
            response.status = 429
            response.setHeader("Retry-After", ex.retryAfterSeconds.toString())
            response.contentType = "application/json"
            response.writer.write("""{"status":429,"message":"Too many requests"}""")
        }
    }
}
