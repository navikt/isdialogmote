package no.nav.syfo.identhendelse.kafka

// Basert på https://github.com/navikt/pdl/blob/master/libs/contract-pdl-avro/src/main/avro/no/nav/person/pdl/aktor/AktorV2.avdl

data class KafkaIdenthendelseDTO(
    val identifikatorer: List<Identifikator>,
)

data class Identifikator(
    val idnummer: String,
    val type: IdentType,
    val gjeldende: Boolean,
)

enum class IdentType {
    FOLKEREGISTERIDENT,
    AKTORID,
    NPID,
}
