package no.nav.syfo.brev.arbeidstaker.brukernotifikasjon

import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig
import no.nav.syfo.application.ApplicationEnvironmentKafka
import no.nav.syfo.application.kafka.commonKafkaAivenProducerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import java.util.*

fun kafkaBrukernotifikasjonProducerConfig(
    applicationEnvironmentKafka: ApplicationEnvironmentKafka,
): Properties {
    return Properties().apply {
        putAll(commonKafkaAivenProducerConfig(applicationEnvironmentKafka))
        put(KafkaAvroSerializerConfig.USER_INFO_CONFIG, "${applicationEnvironmentKafka.aivenRegistryUser}:${applicationEnvironmentKafka.aivenRegistryPassword}")
        put(KafkaAvroSerializerConfig.BASIC_AUTH_CREDENTIALS_SOURCE, "USER_INFO")
        this[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = KafkaAvroSerializer::class.java.canonicalName
        this[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = KafkaAvroSerializer::class.java.canonicalName
    }
}
