package no.nav.syfo.janitor.kafka

import no.nav.syfo.util.configuredJacksonMapper
import org.apache.kafka.common.serialization.Deserializer

val mapper = configuredJacksonMapper()

class JanitorEventDeserializer : Deserializer<JanitorEventDTO> {
    override fun deserialize(topic: String?, data: ByteArray?): JanitorEventDTO =
        mapper.readValue(data, JanitorEventDTO::class.java)
}
