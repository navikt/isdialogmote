package no.nav.syfo.identhendelse.kafka

import no.nav.syfo.util.configuredJacksonMapper
import org.apache.kafka.common.serialization.Deserializer

val mapper = configuredJacksonMapper()

class KafkaIdenthendelseDeserializer : Deserializer<KafkaIdenthendelseDTO> {
    override fun deserialize(topic: String?, data: ByteArray?): KafkaIdenthendelseDTO =
        mapper.readValue(data, KafkaIdenthendelseDTO::class.java)
}
