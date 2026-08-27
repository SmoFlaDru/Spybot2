package com.spybot.core.config

import com.zaxxer.hikari.HikariDataSource
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.jdbc.DataSourceBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.web.reactive.function.client.WebClient
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import javax.sql.DataSource

@Configuration
class LegacyDatasourceConfig {

    @Bean
    @ConditionalOnMissingBean(DataSource::class)
    fun dataSource(environment: Environment): DataSource {
        val rawUrl = environment.getProperty("spring.datasource.url")
            ?: environment.getProperty("DB_URL")
            ?: "jdbc:postgresql://localhost:5432/spybot"
        val explicitUsername = environment.getProperty("spring.datasource.username")
            ?: environment.getProperty("DB_USER")
        val explicitPassword = environment.getProperty("spring.datasource.password")
            ?: environment.getProperty("DB_PASSWORD")
            ?: readPasswordFromFile(environment.getProperty("DB_PASSWORD_FILE"))

        val details = parseDatabaseSettings(rawUrl, explicitUsername, explicitPassword)

        return DataSourceBuilder.create()
            .type(HikariDataSource::class.java)
            .driverClassName("org.postgresql.Driver")
            .url(details.url)
            .username(details.username)
            .password(details.password)
            .build()
    }

    @Bean
    fun webClientBuilder(): WebClient.Builder = WebClient.builder()

    private fun parseDatabaseSettings(
        rawUrl: String,
        explicitUsername: String?,
        explicitPassword: String?,
    ): DatabaseSettings {
        if (rawUrl.startsWith("jdbc:")) {
            return DatabaseSettings(
                url = rawUrl,
                username = explicitUsername ?: "postgres",
                password = explicitPassword ?: "postgres",
            )
        }

        val normalizedUrl = when {
            rawUrl.startsWith("postgres://") -> rawUrl.replaceFirst("postgres://", "http://")
            rawUrl.startsWith("postgresql://") -> rawUrl.replaceFirst("postgresql://", "http://")
            else -> return DatabaseSettings(
                url = rawUrl,
                username = explicitUsername ?: "postgres",
                password = explicitPassword ?: "postgres",
            )
        }

        val uri = URI(normalizedUrl)
        val userInfo = uri.userInfo.orEmpty().split(":", limit = 2)
        val username = explicitUsername ?: userInfo.getOrNull(0).orEmpty().ifBlank { "postgres" }
        val password = explicitPassword ?: userInfo.getOrNull(1).orEmpty().ifBlank { "postgres" }
        val port = if (uri.port == -1) 5432 else uri.port
        val database = uri.path.removePrefix("/").ifBlank { "spybot" }
        val querySuffix = uri.rawQuery?.let { "?$it" }.orEmpty()

        return DatabaseSettings(
            url = "jdbc:postgresql://${uri.host}:$port/$database$querySuffix",
            username = username,
            password = password,
        )
    }

    private data class DatabaseSettings(
        val url: String,
        val username: String,
        val password: String,
    )

    private fun readPasswordFromFile(location: String?): String? {
        val path = location?.takeIf { it.isNotBlank() } ?: return null
        val file = Path.of(path)
        if (!Files.exists(file)) {
            return null
        }
        return Files.readString(file).trim().ifBlank { null }
    }
}
