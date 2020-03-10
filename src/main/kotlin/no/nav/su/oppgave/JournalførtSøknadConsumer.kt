package no.nav.su.oppgave

import io.ktor.application.Application
import io.ktor.config.ApplicationConfig
import io.ktor.http.HttpHeaders.XCorrelationId
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import no.nav.su.oppgave.Metrics.messageProcessed
import no.nav.su.oppgave.Metrics.messageRead
import no.nav.su.meldinger.kafka.Topics.SØKNAD_TOPIC
import no.nav.su.meldinger.kafka.headersAsString
import no.nav.su.meldinger.kafka.soknad.NySøknadMedJournalId
import no.nav.su.meldinger.kafka.soknad.NySøknadMedSkyggesak
import no.nav.su.meldinger.kafka.soknad.SøknadMelding
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.*

val LOG = LoggerFactory.getLogger(Application::class.java)

@KtorExperimentalAPI
internal class JournalførtSøknadConsumer(env: ApplicationConfig) {
    private val LOG = LoggerFactory.getLogger(JournalførtSøknadConsumer::class.java)
    private val kafkaConfig = KafkaConfigBuilder(env)
    private val kafkaConsumer = KafkaConsumer(
            kafkaConfig.consumerConfig(),
            StringDeserializer(),
            StringDeserializer()
    ).also {
        it.subscribe(listOf(SØKNAD_TOPIC))
    }

    fun lesHendelser(scope: CoroutineScope) {
        scope.launch {
            while (isActive) {
                kafkaConsumer.poll(Duration.of(100, ChronoUnit.MILLIS))
                        .onEach {
                            it.logMessage()
                            messageRead()
                        }
                        .filter { SøknadMelding.fromConsumerRecord(it) is NySøknadMedJournalId }
                        .map { SøknadMelding.fromConsumerRecord(it) as NySøknadMedJournalId }
                        .forEach { message ->
                            messageProcessed()
                        }
            }
        }
    }
}

private fun ConsumerRecord<String, String>.logMessage() {
    LOG.info("Polled message: topic:${this.topic()}, key:${this.key()}, value:${this.value()}: $XCorrelationId:${this.headersAsString()[XCorrelationId]}")
}