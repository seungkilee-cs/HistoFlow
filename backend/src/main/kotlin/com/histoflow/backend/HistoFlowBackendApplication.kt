package com.histoflow.backend

import com.histoflow.backend.config.MinioProperties
import com.histoflow.backend.config.TilingProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(value = [MinioProperties::class, TilingProperties::class])
class HistoFlowBackendApplication

fun main(args: Array<String>) {
	runApplication<HistoFlowBackendApplication>(*args)
}
