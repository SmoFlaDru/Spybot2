package com.spybot.web

import com.spybot.core.config.SpybotProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication(scanBasePackages = ["com.spybot"])
@ConfigurationPropertiesScan(basePackageClasses = [SpybotProperties::class])
@EnableScheduling
class SpybotWebApplication

fun main(args: Array<String>) {
    runApplication<SpybotWebApplication>(*args)
}
