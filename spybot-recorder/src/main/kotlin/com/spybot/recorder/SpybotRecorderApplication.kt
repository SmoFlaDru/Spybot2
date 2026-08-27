package com.spybot.recorder

import com.spybot.core.config.SpybotProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["com.spybot"])
@ConfigurationPropertiesScan(basePackageClasses = [SpybotProperties::class])
class SpybotRecorderApplication

fun main(args: Array<String>) {
    runApplication<SpybotRecorderApplication>(*args)
}
