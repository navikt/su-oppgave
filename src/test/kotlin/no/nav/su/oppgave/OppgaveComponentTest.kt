package no.nav.su.oppgave

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpHeaders.XCorrelationId
import io.ktor.server.testing.withTestApplication
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.meldinger.kafka.Topics.SØKNAD_TOPIC
import no.nav.su.meldinger.kafka.soknad.NySøknadMedJournalId
import no.nav.su.meldinger.kafka.soknad.NySøknadMedSkyggesak
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@KtorExperimentalAPI
class OppgaveComponentTest {

    companion object {
        private val wireMockServer: WireMockServer = WireMockServer(WireMockConfiguration.options().dynamicPort())
        private val stsStub = StsStub()

        val correlationId = "correlationId"
        val journalpostId = "12345678"
        val aktørId = "123123123"
        val sakId = "111"
        val oppgaveId = 999L

        fun stubOppgavepost() {
            stubFor(
                    post(urlPathEqualTo(oppgavePath))
                            .withHeader(HttpHeaders.Authorization, equalTo("Bearer $STS_TOKEN"))
                            .withHeader(XCorrelationId, equalTo(correlationId))
                            .willReturn(
                                    okJson(
                                            """
                                                {
                                                  "id": $oppgaveId,
                                                  "tildeltEnhetsnr": "0219",
                                                  "journalpostId": "$journalpostId",
                                                  "saksreferanse": "$sakId",
                                                  "aktoerId": "$aktørId",
                                                  "tilordnetRessurs": "Z998323",
                                                  "beskrivelse": "string",
                                                  "tema": "SUP",
                                                  "oppgavetype": "ATT",
                                                  "versjon": 1,
                                                  "fristFerdigstillelse": "2018-03-24",
                                                  "aktivDato": "2018-03-10",
                                                  "opprettetTidspunkt": "2020-03-16T15:39:49.408+01:00",
                                                  "opprettetAv": "srvsupstonad",
                                                  "prioritet": "HOY",
                                                  "status": "OPPRETTET",
                                                  "metadata": {}
                                                }
                                            """.trimIndent()
                                            )
                                        )
                    )
        }

        @BeforeAll
        @JvmStatic
        fun start() {
            wireMockServer.apply {
                start()
                val client = create().port(wireMockServer.port()).build()
                configureFor(client)
            }
            stubFor(stsStub.stubbedSTS())
            stubOppgavepost()
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

        val observer = object : OppgaveObserver {
            var idFraOppgave = 0L
            override fun oppgaveOpprettet(id: Long) {
                idFraOppgave = id
            }
        }
        withTestApplication({
            testEnv(wireMockServer)
            suoppgave(listOf(observer))
        }) {
            val producer = environment.config.kafkaMiljø().producer()
            producer.send(
                NySøknadMedJournalId(correlationId = correlationId, sakId = "1", søknadId = "1", søknad = """{"key":"value"}""", fnr = "12345678910", aktørId = "1234567891011", gsakId = "6", journalId = "1")
                    .toProducerRecord(SØKNAD_TOPIC))
            Thread.sleep(2000)
            wireMockServer.verify(1, postRequestedFor(urlEqualTo(oppgavePath)))
            assertEquals(oppgaveId, observer.idFraOppgave)
        }
    }

    @Test
    fun `ignorerer søknader uten journalid`(){
        withTestApplication({
            testEnv(wireMockServer)
            suoppgave()
        }) {
            val producer = environment.config.kafkaMiljø().producer()
            producer.send(
                NySøknadMedSkyggesak(correlationId = "cora", sakId = "2", søknadId = "1", søknad = """{"key":"value"}""", fnr = "12345678910", aktørId = "1234567891011", gsakId = "6")
                    .toProducerRecord(SØKNAD_TOPIC))
            Thread.sleep(2000)
            wireMockServer.verify(0, postRequestedFor(urlEqualTo(oppgavePath)))
        }
    }
}
