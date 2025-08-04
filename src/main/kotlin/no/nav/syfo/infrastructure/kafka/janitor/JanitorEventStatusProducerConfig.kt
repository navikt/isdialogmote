package no.nav.syfo.infrastructure.kafka.janitor

import no.nav.syfo.ApplicationEnvironmentKafka
import no.nav.syfo.infrastructure.kafka.JacksonKafkaSerializer
import no.nav.syfo.infrastructure.kafka.commonKafkaAivenProducerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import java.util.*

fun kafkaJanitorEventProducerConfig(
    applicationEnvironmentKafka: ApplicationEnvironmentKafka,
): Properties {
    return Properties().apply {
        putAll(commonKafkaAivenProducerConfig(applicationEnvironmentKafka))
        this[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java.canonicalName
        this[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = JacksonKafkaSerializer::class.java.canonicalName
    }
}
