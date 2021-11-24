package no.nav.syfo.brev.arbeidstaker.brukernotifikasjon

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import io.confluent.kafka.serializers.KafkaAvroSerializer
import no.nav.syfo.application.Environment
import no.nav.syfo.application.kafka.commonKafkaProducerConfig
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SaslConfigs
import java.util.*

fun kafkaBrukernotifikasjonProducerConfig(
    environment: Environment,
): Properties {
    return Properties().apply {
        putAll(commonKafkaProducerConfig())
        this[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = KafkaAvroSerializer::class.java.canonicalName
        this[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = KafkaAvroSerializer::class.java.canonicalName
        this[AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG] = environment.kafka.schemaRegistryUrl
        this[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG] = environment.kafka.bootstrapServers
        this[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG] = "SASL_SSL"
        this[SaslConfigs.SASL_MECHANISM] = "PLAIN"
        this[SaslConfigs.SASL_JAAS_CONFIG] =
            "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"${environment.serviceuserUsername}\" password=\"${environment.serviceuserPassword}\";"
    }
}
