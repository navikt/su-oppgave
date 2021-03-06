package no.nav.su.oppgave

import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpPost
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import no.nav.su.meldinger.kafka.soknad.NySøknadMedJournalId
import no.nav.su.person.sts.StsConsumer
import org.json.JSONObject
import java.time.LocalDate

val oppgavePath = "/api/v1/oppgaver"

private const val TEMA_SU_UFØR_FLYKTNING = "ab0431"
private const val TYPE_FØRSTEGANGSSØKNAD = "ae0245"

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
                .header(HttpHeaders.Accept, ContentType.Application.Json)
                .header(HttpHeaders.ContentType, ContentType.Application.Json)
                .header(HttpHeaders.XCorrelationId, nySøknadMedJournalId.correlationId)
                .body("""
                    { 
                        "journalpostId": "${nySøknadMedJournalId.journalId}",
                        "saksreferanse": "${nySøknadMedJournalId.sakId}",
                        "aktoerId": "${nySøknadMedJournalId.aktørId}", 
                        "tema": "SUP",
                        "behandlesAvApplikasjon": "SUPSTONAD",
                        "oppgavetype": "BEH_SAK",
                        "behandlingstema": "$TEMA_SU_UFØR_FLYKTNING", 
                        "behandlingstype": "$TYPE_FØRSTEGANGSSØKNAD", 
                        "aktivDato": ${LocalDate.now()},
                        "fristFerdigstillelse": ${LocalDate.now().plusDays(30)},
                        "prioritet": "NORM"
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
