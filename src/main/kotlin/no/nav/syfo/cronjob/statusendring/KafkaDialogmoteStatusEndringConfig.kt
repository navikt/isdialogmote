package no.nav.syfo.cronjob.statusendring

import io.confluent.kafka.serializers.*
import no.nav.syfo.application.Environment
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.config.SslConfigs
import java.util.*

fun kafkaDialogmoteStatusEndringProducerConfig(
    environment: Environment,
): Properties {
    return Properties().apply {
        putAll(commonKafkaAivenConfig(environment))
        this[ProducerConfig.ACKS_CONFIG] = "all"
        this[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = "true"
        this[ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION] = "1"
        this[ProducerConfig.MAX_BLOCK_MS_CONFIG] = "15000"
        this[ProducerConfig.RETRIES_CONFIG] = "100000"
        this[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = KafkaAvroSerializer::class.java.canonicalName
        this[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = KafkaAvroSerializer::class.java.canonicalName
        this[KafkaAvroSerializerConfig.USER_INFO_CONFIG] = "${environment.kafkaAivenRegistryUser}:${environment.kafkaAivenRegistryPassword}"
        this[KafkaAvroSerializerConfig.BASIC_AUTH_CREDENTIALS_SOURCE] = "USER_INFO"
    }
}

private fun commonKafkaAivenConfig(environment: Environment) = Properties().apply {
    this[SaslConfigs.SASL_MECHANISM] = "PLAIN"
    this[AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG] = environment.kafkaAivenSchemaRegistryUrl
    this[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG] = environment.kafkaAivenBootstrapServers
    this[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG] = environment.kafkaAivenSecurityProtocol
    this[SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG] = ""
    this[SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG] = "jks"
    this[SslConfigs.SSL_KEYSTORE_TYPE_CONFIG] = "PKCS12"
    this[SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG] = environment.KafkaAivenTruststoreLocation
    this[SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG] = environment.KafkaAivenCredstorePassword
    this[SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG] = environment.KafkaAivenKeystoreLocation
    this[SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG] = environment.KafkaAivenCredstorePassword
    this[SslConfigs.SSL_KEY_PASSWORD_CONFIG] = environment.KafkaAivenCredstorePassword
}
