package no.nav.su.oppgave

import io.ktor.util.KtorExperimentalAPI
import no.nav.common.KafkaEnvironment
import no.nav.su.meldinger.kafka.Topics.SØKNAD_TOPIC
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
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

        val kafkaProducer = KafkaProducer(producerProperties(), StringSerializer(), StringSerializer())

        private fun producerProperties(): MutableMap<String, Any>? {
            return HashMap<String, Any>().apply {
                put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, kafkaInstance.brokersURL)
                put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "PLAINTEXT")
                put(SaslConfigs.SASL_MECHANISM, "PLAIN")
                put(ProducerConfig.ACKS_CONFIG, "all")
                put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "1")
            }
        }

        val kafkaConsumer = KafkaConsumer(
                consumerProperties(),
                StringDeserializer(),
                StringDeserializer()).also {
            it.subscribe(listOf(SØKNAD_TOPIC))
        }

        private fun consumerProperties(): MutableMap<String, Any>? {
            return HashMap<String, Any>().apply {
                put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, kafkaInstance.brokersURL)
                put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "PLAINTEXT")
                put(SaslConfigs.SASL_MECHANISM, "PLAIN")
                put(ConsumerConfig.GROUP_ID_CONFIG, "test")
                put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
            }
        }
    }


}