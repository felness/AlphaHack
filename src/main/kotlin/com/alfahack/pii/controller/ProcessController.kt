package com.alfahack.pii.controller

import com.alfahack.pii.model.ProcessRequest
import com.alfahack.pii.model.ProcessResponse
import com.alfahack.pii.service.ProcessService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

/**
 * REST-контроллер контракта /process.
 *
 * Обрабатывает и маскирование, и демаскирование.
 * Направление определяется по payload_id.
 */
@RestController
class ProcessController(
    private val processService: ProcessService
) {

    @PostMapping("/process")
    fun process(
        @Valid @RequestBody request: ProcessRequest,
        @RequestHeader(name = "X-System-Id", required = false) systemId: String?
    ): ResponseEntity<ProcessResponse> {
        val response = processService.process(request, systemId)
        return ResponseEntity.ok(response)
    }
}