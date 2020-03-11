package no.nav.su.oppgave

import com.github.tomakehurst.wiremock.WireMockServer
import io.ktor.application.Application
import io.ktor.config.MapApplicationConfig
import io.ktor.util.KtorExperimentalAPI

@KtorExperimentalAPI
fun Application.testEnv(wireMockServer: WireMockServer) {
    val baseUrl = wireMockServer.baseUrl()
    (environment.config as MapApplicationConfig).apply {
        put("kafka.commitInterval", "3")
        put("kafka.groupId", "blablausergroup")
        put("kafka.username", "kafkaUser")
        put("kafka.password", "kafkaPassword")
        put("kafka.bootstrap", EmbeddedKafka.kafkaInstance.brokersURL)
        put("kafka.trustStorePath", "")
        put("kafka.trustStorePassword", "")
    }
}