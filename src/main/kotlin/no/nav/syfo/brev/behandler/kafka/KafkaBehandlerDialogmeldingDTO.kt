package no.nav.syfo.brev.behandler.kafka

data class KafkaBehandlerDialogmeldingDTO(
    val behandlerRef: String,
    val personIdent: String,
    val dialogmeldingUuid: String,
    val dialogmeldingRefParent: String?,
    val dialogmeldingRefConversation: String,
    val dialogmeldingType: String,
    val dialogmeldingKodeverk: String,
    val dialogmeldingKode: Int,
    val dialogmeldingTekst: String?,
    val dialogmeldingVedlegg: ByteArray? = null,
)
