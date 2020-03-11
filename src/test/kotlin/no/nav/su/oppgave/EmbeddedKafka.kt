package no.nav.su.oppgave

import io.ktor.config.ApplicationConfig
import io.ktor.util.KtorExperimentalAPI
import no.nav.common.KafkaEnvironment
import no.nav.su.meldinger.kafka.Topics.SØKNAD_TOPIC
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import java.io.File
import java.util.*

@KtorExperimentalAPI
class EmbeddedKafka {
    companion object {
        val kafkaInstance = KafkaEnvironment(
                autoStart = false,
                noOfBrokers = 1,
                topicInfos = listOf(KafkaEnvironment.TopicInfo(name = SØKNAD_TOPIC, partitions = 1)),
                withSchemaRegistry = false,
                withSecurity = false,
                brokerConfigOverrides = Properties().apply {
                    this["auto.leader.rebalance.enable"] = "false"
                    this["group.initial.rebalance.delay.ms"] = "1" //Avoid waiting for new consumers to join group before first rebalancing (default 3000ms)
                }
        ).also {
            it.start()
        }

    }
}

@KtorExperimentalAPI
internal class KafkaConfigBuilder(
    private val env: ApplicationConfig
) {
    fun producerConfig() = kafkaBaseConfig().apply {
        put(ProducerConfig.ACKS_CONFIG, "all")
        put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "1")
    }

    private fun kafkaBaseConfig() = Properties().apply {
        put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, env.getProperty("kafka.bootstrap"))
        put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "PLAINTEXT")
        val username = env.getProperty("kafka.username")
        val password = env.getProperty("kafka.password")
        put(
            SaslConfigs.SASL_JAAS_CONFIG,
            "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"$username\" password=\"$password\";"
        )
        put(SaslConfigs.SASL_MECHANISM, "PLAIN")

        val truststorePath = env.getProperty("kafka.trustStorePath")
        val truststorePassword = env.getProperty("kafka.trustStorePassword")
        if (truststorePath != "" && truststorePassword != "")
            try {
                put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL")
                put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, File(truststorePath).absolutePath)
                put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, truststorePassword)
            } catch (ex: Exception) {
            }
    }
}