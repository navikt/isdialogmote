package no.nav.syfo.infrastructure.kafka.behandler

data class KafkaBehandlerDialogmeldingDTO(
    val behandlerRef: String,
    val personident: String,
    val dialogmeldingUuid: String,
    val dialogmeldingRefParent: String?,
    val dialogmeldingRefConversation: String,
    val dialogmeldingType: String,
    val dialogmeldingKodeverk: String,
    val dialogmeldingKode: Int,
    val dialogmeldingTekst: String?,
    val dialogmeldingVedlegg: ByteArray? = null,
    val kilde: String?,
)
