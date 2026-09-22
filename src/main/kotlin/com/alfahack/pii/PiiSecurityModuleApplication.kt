package com.alfahack.pii

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PiiSecurityModuleApplication

fun main(args: Array<String>) {
	runApplication<PiiSecurityModuleApplication>(*args)
}
