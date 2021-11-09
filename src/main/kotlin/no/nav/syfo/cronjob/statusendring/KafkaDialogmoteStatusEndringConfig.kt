package no.nav.syfo.cronjob.statusendring

import io.confluent.kafka.serializers.*
import no.nav.syfo.application.Environment
import no.nav.syfo.application.kafka.commonKafkaAivenProducerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import java.util.*

fun kafkaDialogmoteStatusEndringProducerConfig(
    environment: Environment,
): Properties {
    return Properties().apply {
        putAll(commonKafkaAivenProducerConfig(environment))
        this[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = KafkaAvroSerializer::class.java.canonicalName
        this[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = KafkaAvroSerializer::class.java.canonicalName
        this[KafkaAvroSerializerConfig.USER_INFO_CONFIG] = "${environment.kafkaAivenRegistryUser}:${environment.kafkaAivenRegistryPassword}"
        this[KafkaAvroSerializerConfig.BASIC_AUTH_CREDENTIALS_SOURCE] = "USER_INFO"
    }
}
