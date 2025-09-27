package com.histoflow.backend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class HistoFlowBackendApplication

fun main(args: Array<String>) {
	runApplication<HistoFlowBackendApplication>(*args)
}
