package no.nav.syfo.brev.esyfovarsel

import java.util.*
import no.nav.syfo.application.ApplicationEnvironmentKafka
import no.nav.syfo.application.kafka.JacksonKafkaSerializer
import no.nav.syfo.application.kafka.commonKafkaAivenProducerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.serialization.StringSerializer

fun kafkaEsyfovarselConfig(
    applicationEnvironmentKafka: ApplicationEnvironmentKafka,
): Properties {
    return Properties().apply {
        putAll(commonKafkaAivenProducerConfig(applicationEnvironmentKafka))

        this[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        this[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = JacksonKafkaSerializer::class.java

        remove(SaslConfigs.SASL_MECHANISM)
        remove(SaslConfigs.SASL_JAAS_CONFIG)
    }
}
