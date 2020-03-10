package no.nav.su.oppgave

import io.prometheus.client.Counter

object Metrics {
    private const val prefix = "su_oppgave_"
    private val messagesRead = Counter.build("${prefix}messages_read_total", "Antall meldinger lest fra kafka").register()
    private val messagesProcessed = Counter.build("${prefix}messages_processed_total", "Antall meldinger prosessert").register()

    fun messageRead() = messagesRead.inc()
    fun messageProcessed() = messagesProcessed.inc()
}