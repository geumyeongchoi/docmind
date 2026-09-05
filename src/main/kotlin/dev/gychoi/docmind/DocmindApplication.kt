package dev.gychoi.docmind

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync

@SpringBootApplication
@EnableAsync
@ConfigurationPropertiesScan
class DocmindApplication

fun main(args: Array<String>) {
    runApplication<DocmindApplication>(*args)
}
