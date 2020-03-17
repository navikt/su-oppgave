package no.nav.su.oppgave

import io.ktor.application.Application
import io.ktor.config.ApplicationConfig
import io.ktor.util.KtorExperimentalAPI
import io.prometheus.client.CollectorRegistry
import no.nav.su.person.sts.StsConsumer

@KtorExperimentalAPI
internal fun Application.suoppgave(observers: List<OppgaveObserver> = emptyList()) {
    val collectorRegistry = CollectorRegistry.defaultRegistry
    installMetrics(collectorRegistry)
    naisRoutes(collectorRegistry)
    JournalførtSøknadConsumer(environment.config, oppgaveClient = velgOppgave(observers)).lesHendelser(this)
}

@KtorExperimentalAPI
private fun Application.velgOppgave(observers: List<OppgaveObserver>): Oppgave = when {
    fromEnvironment("oppgave.skarp") == "true" -> OppgaveClient(
            stsConsumer = StsConsumer(
                    baseUrl = fromEnvironment("sts.url"),
                    username = fromEnvironment("sts.username"),
                    password = fromEnvironment("sts.password")
            ),
            baseUrl = fromEnvironment("oppgave.url"),
            observers = observers
    )
    else -> DummyOppgave()
}

fun main(args: Array<String>) = io.ktor.server.netty.EngineMain.main(args)

@KtorExperimentalAPI
internal fun ApplicationConfig.getProperty(key: String): String = property(key).getString()

@KtorExperimentalAPI
fun Application.fromEnvironment(path: String): String = environment.config.property(path).getString()