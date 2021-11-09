package no.nav.syfo.brev.behandler

import no.nav.syfo.application.Environment
import no.nav.syfo.application.kafka.JacksonKafkaSerializer
import no.nav.syfo.application.kafka.commonKafkaAivenProducerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import java.util.*

fun kafkaBehandlerDialogmeldingProducerConfig(
    environment: Environment,
): Properties {
    return Properties().apply {
        putAll(commonKafkaAivenProducerConfig(environment))
        this[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java.canonicalName
        this[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = JacksonKafkaSerializer::class.java.canonicalName
    }
}
