package no.nav.syfo.dialogmelding.kafka

import no.nav.syfo.util.configuredJacksonMapper
import org.apache.kafka.common.serialization.Deserializer

val mapper = configuredJacksonMapper()

class KafkaDialogmeldingDeserializer : Deserializer<KafkaDialogmeldingDTO> {
    override fun deserialize(topic: String?, data: ByteArray?): KafkaDialogmeldingDTO =
        mapper.readValue(data, KafkaDialogmeldingDTO::class.java)
}
