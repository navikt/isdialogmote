package no.nav.syfo.infrastructure.kafka

import no.nav.syfo.api.authentication.configuredJacksonMapper
import org.apache.kafka.common.serialization.Serializer

val mapper = configuredJacksonMapper()

class JacksonKafkaSerializer : Serializer<Any> {
    override fun serialize(topic: String?, data: Any?): ByteArray = mapper.writeValueAsBytes(data)
    override fun close() {}
}
