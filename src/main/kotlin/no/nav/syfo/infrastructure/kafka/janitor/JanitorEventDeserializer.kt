package no.nav.syfo.infrastructure.kafka.janitor

import no.nav.syfo.api.authentication.configuredJacksonMapper
import org.apache.kafka.common.serialization.Deserializer

val mapper = configuredJacksonMapper()

class JanitorEventDeserializer : Deserializer<JanitorEventDTO> {
    override fun deserialize(topic: String?, data: ByteArray?): JanitorEventDTO =
        mapper.readValue(data, JanitorEventDTO::class.java)
}
