package no.nav.su.oppgave

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.withTestApplication
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.meldinger.kafka.Topics.SØKNAD_TOPIC
import no.nav.su.meldinger.kafka.soknad.NySøknad
import no.nav.su.meldinger.kafka.soknad.NySøknadMedJournalId
import org.json.JSONObject
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

        fun stubOppgavepost() =
                post(urlPathEqualTo(oppgavePath))
                        .withHeader(HttpHeaders.Authorization, equalTo("Bearer $STS_TOKEN"))
                        .withHeader(HttpHeaders.XCorrelationId, equalTo(correlationId))
                        .withHeader(HttpHeaders.Accept, equalTo(ContentType.Application.Json.toString()))
                        .withHeader(HttpHeaders.ContentType, equalTo(ContentType.Application.Json.toString()))
                        .willReturn(aResponse()
                                .withBody("""
                                    {
                                                      "id": $oppgaveId,
                                                      "tildeltEnhetsnr": "4811",
                                                      "journalpostId": "$journalpostId",
                                                      "saksreferanse": "$sakId",
                                                      "aktoerId": "$aktørId",
                                                      "tema": "SUP",
                                                      "behandlesAvApplikasjon": "SUPSTONAD",
                                                      "behandlingstema": "ab0431",
                                                      "oppgavetype": "BEH_SAK",
                                                      "behandlingstype": "ae0245",
                                                      "versjon": 1,
                                                      "fristFerdigstillelse": "2020-06-06",
                                                      "aktivDato": "2020-05-05",
                                                      "opprettetTidspunkt": "2020-06-05T14:48:16.234+02:00",
                                                      "opprettetAv": "srvsupstonad",
                                                      "prioritet": "NORM",
                                                      "status": "OPPRETTET",
                                                      "metadata": {}
                                                    }
                                """.trimIndent())
                                .withStatus(HttpStatusCode.Created.value)
                        )

        @BeforeAll
        @JvmStatic
        fun start() {
            wireMockServer.start()
            wireMockServer.stubFor(stsStub.stubbedSTS())
            wireMockServer.stubFor(stubOppgavepost())
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
    fun `når vi får en melding om en søknad som har blitt journalført, så skal vi opprette oppgave`() {

        val observer = object : OppgaveObserver {
            var idFraOppgave = 0L
            override fun oppgaveOpprettet(id: Long) {
                idFraOppgave = id
            }
        }

        val nySøknadMedJournalId = NySøknadMedJournalId(
                correlationId = correlationId,
                sakId = "1",
                søknadId = "1",
                søknad = """{"key":"value"}""",
                fnr = "12345678910",
                aktørId = "1234567891011",
                journalId = "1")

        withTestApplication({
            testEnv(wireMockServer)
            suoppgave(listOf(observer))
        }) {
            val producer = environment.config.kafkaMiljø().producer()
            producer.send(nySøknadMedJournalId.toProducerRecord(SØKNAD_TOPIC))
            Thread.sleep(2000)

            wireMockServer.verify(1, postRequestedFor(urlEqualTo(oppgavePath)))
            assertEquals(oppgaveId, observer.idFraOppgave)

            val requestJson = JSONObject(String(wireMockServer.allServeEvents.first { it.request.url == oppgavePath }.request.body))
            assertEquals(requestJson.getString("journalpostId"), nySøknadMedJournalId.journalId)
            assertEquals(requestJson.getString("saksreferanse"), nySøknadMedJournalId.sakId)
            assertEquals(requestJson.getString("aktoerId"), nySøknadMedJournalId.aktørId)
            assertEquals(requestJson.getString("oppgavetype"), "BEH_SAK")
            assertEquals(requestJson.getString("behandlingstema"), "ab0431")
            assertEquals(requestJson.getString("behandlingstype"), "ae0245")
            assertEquals(requestJson.getString("prioritet"), "NORM")
            assertEquals(requestJson.getString("behandlesAvApplikasjon"), "SUPSTONAD")
        }
    }

    @Test
    fun `ignorerer søknader uten journalid`() {
        withTestApplication({
            testEnv(wireMockServer)
            suoppgave()
        }) {
            val producer = environment.config.kafkaMiljø().producer()
            producer.send(NySøknad(
                    correlationId = "cora",
                    sakId = "2",
                    søknadId = "1",
                    søknad = """{"key":"value"}""",
                    fnr = "12345678910",
                    aktørId = "1234567891011").toProducerRecord(SØKNAD_TOPIC))
            Thread.sleep(2000)
            wireMockServer.verify(0, postRequestedFor(urlEqualTo(oppgavePath)))
        }
    }
}
