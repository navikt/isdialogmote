package no.nav.syfo.infrastructure.client.dokarkiv.domain

enum class BrevkodeType(
    val value: String,
) {
    DIALOGMOTE_AVLYSNING_AT("OPPF_DM_AVLYS_AT"),
    DIALOGMOTE_ENDRING_TID_STED_AT("OPPF_DM_ENDR_AT"),
    DIALOGMOTE_INNKALLING_AT("OPPF_DM_INK_AT"),
    DIALOGMOTE_REFERAT_AT("OPPF_DM_REF_AT"),
    DIALOGMOTE_AVLYSNING_AG("OPPF_DM_AVLYS_AG"),
    DIALOGMOTE_ENDRING_TID_STED_AG("OPPF_DM_ENDR_AG"),
    DIALOGMOTE_INNKALLING_AG("OPPF_DM_INK_AG"),
    DIALOGMOTE_REFERAT_AG("OPPF_DM_REF_AG"),
    DIALOGMOTE_AVLYSNING_BEH("OPPF_DM_AVLYS_BEH"),
    DIALOGMOTE_ENDRING_TID_STED_BEH("OPPF_DM_ENDR_BEH"),
    DIALOGMOTE_INNKALLING_BEH("OPPF_DM_INK_BEH"),
    DIALOGMOTE_REFERAT_BEH("OPPF_DM_REF_BEH"),
}

enum class DialogmoteDeltakerType(
    val value: String,
) {
    ARBEIDSTAKER("AT"),
    ARBEIDSGIVER("AG"),
    BEHANDLER("BEH"),
}

data class Dokument private constructor(
    val brevkode: String,
    val dokumentKategori: String? = null,
    val dokumentvarianter: List<Dokumentvariant>,
    val tittel: String? = null,
) {
    companion object {
        fun create(
            brevkode: BrevkodeType,
            dokumentKategori: String? = null,
            dokumentvarianter: List<Dokumentvariant>,
            tittel: String? = null,
        ) = Dokument(
            brevkode = brevkode.value,
            dokumentKategori = dokumentKategori,
            dokumentvarianter = dokumentvarianter,
            tittel = tittel,
        )
    }
}
