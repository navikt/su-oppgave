package no.nav.su.oppgave

import io.ktor.config.ApplicationConfig
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import no.nav.su.meldinger.kafka.Meldingsleser
import no.nav.su.meldinger.kafka.soknad.NySøknadMedJournalId

@KtorExperimentalAPI
internal class JournalførtSøknadConsumer(env: ApplicationConfig) {
    private val meldingsleser = Meldingsleser(env.kafkaMiljø(), Metrics)
    fun lesHendelser(scope: CoroutineScope) {
        scope.launch {
            while (isActive) {
                meldingsleser.lesMelding<NySøknadMedJournalId> {
                    println("should do something with $it")
                }
            }
        }
    }
}
