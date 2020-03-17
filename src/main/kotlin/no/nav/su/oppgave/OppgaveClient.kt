package no.nav.su.oppgave

import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpPost
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders.Accept
import io.ktor.http.HttpHeaders.XCorrelationId
import no.nav.su.meldinger.kafka.soknad.NySøknadMedJournalId
import no.nav.su.person.sts.StsConsumer
import org.json.JSONObject
import java.time.LocalDate

val oppgavePath = "/api/v1/oppgaver"

internal sealed class Oppgave {
    abstract fun opprettOppgave(nySøknadMedJournalId: NySøknadMedJournalId): Long
}

internal class DummyOppgave : Oppgave() {
    override fun opprettOppgave(nySøknadMedJournalId: NySøknadMedJournalId): Long = 0
}

internal class OppgaveClient(
        private val baseUrl: String,
        private val stsConsumer: StsConsumer,
        private val observers: List<OppgaveObserver> = emptyList()
) : Oppgave() {
    override fun opprettOppgave(nySøknadMedJournalId: NySøknadMedJournalId): Long {
        val (_, _, result) = "$baseUrl$oppgavePath".httpPost()
                .authentication().bearer(stsConsumer.token())
                .header(Accept, ContentType.Application.Json)
                .header(XCorrelationId, nySøknadMedJournalId.correlationId)
                .body(
                        """
                    { 
                        "tildeltEnhetsnr": "0219", Hvilken enhet? Denne trenger vi kanskje ikke blir kanskje satt automatisk etter disse reglene : https://norg2-frontend.nais.preprod.local/#/arbeidsfordeling
                        "journalpostId": ${nySøknadMedJournalId.journalId}
                        "saksreferanse": ${nySøknadMedJournalId.sakId}                        // eller gsakId ?
                        "aktoerId": ${nySøknadMedJournalId.aktørId}
                        "tilordnetRessurs": "Z998323",                                        // Trenger vi denne? Saksbehandler eller ikke satt
                        "beskrivelse": "string",                                              // Trenger vi beskrivelse ?
                        "tema": "SUP",                                                        // Denne er grei
                        "oppgavetype": "ATT",                                                 // Er ATT riktig for alle?
                        "aktivDato": ${LocalDate.now()},                                      // "2018-03-10", Dagen i dag, eller frem i tid?
                        "fristFerdigstillelse": ${LocalDate.now().plusDays(30)}     // "2018-03-24", Hvilken dato skal vi sette her?
                        "prioritet": "HOY",                                                   // Hva skal vi sette som prioritet?
                        "behandlingstema": "ab0158", https://kodeverk-web.nais.adeo.no/kodeverksoversikt/kodeverk/Behandlingstema/kode/
                        "behandlingstype": "ae0034", https://kodeverksmapper.nais.adeo.no/kodeverksmapper/underkategorier
                     }
         """.trimIndent()
                ).responseString()

        return result.fold(
                { resultat ->
                    val id = JSONObject(resultat).getLong("id")
                    observers.forEach {
                        it.oppgaveOpprettet(id)
                    }
                  id
                },
                { throw RuntimeException("Feil i kallet mot oppgave") }
        )
    }
}

interface OppgaveObserver {
    fun oppgaveOpprettet(id: Long)
}
