package no.nav.syfo.infrastructure.client.dialogmelding

data class BehandlerDTO(
    val type: String?,
    val behandlerRef: String,
    val kategori: String,
    val fnr: String?,
    val hprId: Int?,
    val herId: Int?,
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
    val orgnummer: String?,
    val kontor: String?,
    val kontorHerId: Int?,
    val kontorDialogmeldingmeldingEnabled: Boolean,
    val adresse: String?,
    val postnummer: String?,
    val poststed: String?,
    val telefon: String?,
    val invalidated: Boolean,
)
