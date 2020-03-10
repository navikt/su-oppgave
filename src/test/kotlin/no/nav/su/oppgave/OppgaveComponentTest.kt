package no.nav.su.oppgave

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.matching.AnythingPattern
import io.ktor.http.HttpHeaders
import io.ktor.server.testing.withTestApplication
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.meldinger.kafka.Topics.SØKNAD_TOPIC
import no.nav.su.meldinger.kafka.soknad.NySøknad
import no.nav.su.meldinger.kafka.soknad.NySøknadMedJournalId
import no.nav.su.meldinger.kafka.soknad.NySøknadMedSkyggesak
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration.ofSeconds

@KtorExperimentalAPI
class OppgaveComponentTest {

    companion object {
        private val wireMockServer: WireMockServer = WireMockServer(WireMockConfiguration.options().dynamicPort())

        @BeforeAll
        @JvmStatic
        fun start() {
            wireMockServer.apply {
                start()
                val client = WireMock.create().port(wireMockServer.port()).build()
                WireMock.configureFor(client)
            }
        }

        @AfterAll
        @JvmStatic
        fun stop() {
            wireMockServer.stop()
        }
    }

    @BeforeEach
    fun resetWiremock() {
        wireMockServer.resetRequests()
    }

    @Test
    fun `når vi får en melding om en søknad som har blitt journalført, så skal vi opprette oppgave`(){
        withTestApplication({
            testEnv(wireMockServer)
            suoppgave()
        }) {
            val kafkaConfig = KafkaConfigBuilder(environment.config)
            val producer = KafkaProducer(kafkaConfig.producerConfig(), StringSerializer(), StringSerializer())
            producer.send(
                NySøknadMedJournalId(correlationId = "cora", sakId = "1", søknadId = "1", søknad = """{"key":"value"}""", fnr = "12345678910", aktørId = "1234567891011", gsakId = "6", journalId = "1")
                    .toProducerRecord(SØKNAD_TOPIC))
            Thread.sleep(2000)
//            wireMockServer.verify(1, WireMock.postRequestedFor(urlEqualTo("/vivetikkehvadenneerenda")))
        }
    }

    @Test
    fun `ignorerer søknader uten journalid`(){
        withTestApplication({
            testEnv(wireMockServer)
            suoppgave()
        }) {
            val kafkaConfig = KafkaConfigBuilder(environment.config)
            val producer = KafkaProducer(kafkaConfig.producerConfig(), StringSerializer(), StringSerializer())
            producer.send(
                NySøknadMedSkyggesak(correlationId = "cora", sakId = "2", søknadId = "1", søknad = """{"key":"value"}""", fnr = "12345678910", aktørId = "1234567891011", gsakId = "6")
                    .toProducerRecord(SØKNAD_TOPIC))
            Thread.sleep(2000)
            wireMockServer.verify(0, WireMock.postRequestedFor(urlEqualTo("/vivetikkehvadenneerenda")))
        }
    }
}
