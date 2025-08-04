package no.nav.syfo.infrastructure.kafka

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import no.nav.syfo.ApplicationEnvironmentKafka
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.config.SslConfigs
import java.util.*

fun commonKafkaProducerConfig() = Properties().apply {
    this[ProducerConfig.ACKS_CONFIG] = "all"
    this[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = "true"
    this[ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION] = "1"
    this[ProducerConfig.MAX_BLOCK_MS_CONFIG] = "15000"
    this[ProducerConfig.RETRIES_CONFIG] = "100000"
}

fun commonKafkaAivenProducerConfig(applicationEnvironmentKafka: ApplicationEnvironmentKafka) = Properties().apply {
    putAll(commonKafkaAivenConfig(applicationEnvironmentKafka))
    putAll(commonKafkaProducerConfig())

    this[AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG] = applicationEnvironmentKafka.aivenSchemaRegistryUrl
}

fun commonKafkaAivenConfig(
    applicationEnvironmentKafka: ApplicationEnvironmentKafka,
) = Properties().apply {
    this[SaslConfigs.SASL_MECHANISM] = "PLAIN"
    this[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG] = applicationEnvironmentKafka.aivenBootstrapServers
    this[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG] = applicationEnvironmentKafka.aivenSecurityProtocol
    this[SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG] = ""
    this[SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG] = "jks"
    this[SslConfigs.SSL_KEYSTORE_TYPE_CONFIG] = "PKCS12"
    this[SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG] = applicationEnvironmentKafka.aivenTruststoreLocation
    this[SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG] = applicationEnvironmentKafka.aivenCredstorePassword
    this[SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG] = applicationEnvironmentKafka.aivenKeystoreLocation
    this[SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG] = applicationEnvironmentKafka.aivenCredstorePassword
    this[SslConfigs.SSL_KEY_PASSWORD_CONFIG] = applicationEnvironmentKafka.aivenCredstorePassword
}
