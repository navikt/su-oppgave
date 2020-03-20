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

private const val TEMA_SU_UFØR_FLYKTNING = "ab0431"
private const val TYPE_FØRSTEGANGSSØKNAD = "ae0244"

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
                        "tildeltEnhetsnr": "0219",
                        "journalpostId": ${nySøknadMedJournalId.journalId},
                        "saksreferanse": ${nySøknadMedJournalId.gsakId},
                        "aktoerId": ${nySøknadMedJournalId.aktørId}, 
                        "tema": "SUP",
                        "oppgavetype": "ATT",
                        "behandlingstema": "$TEMA_SU_UFØR_FLYKTNING", 
                        "behandlingstype": "$TYPE_FØRSTEGANGSSØKNAD", 
                        "aktivDato": ${LocalDate.now()},
                        "fristFerdigstillelse": ${LocalDate.now().plusDays(30)},
                        "prioritet": "HOY"
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
