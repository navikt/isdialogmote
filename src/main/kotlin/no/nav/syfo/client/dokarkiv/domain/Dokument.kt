package no.nav.syfo.client.dokarkiv.domain

enum class BrevkodeType(
    val value: String,
) {
    DIALOGMOTE_AVLYSNING("OPPF_DM_AVLYS_AT"),
    DIALOGMOTE_ENDRING_TID_STED("OPPF_DM_ENDR_AT"),
    DIALOGMOTE_INNKALLING("OPPF_DM_INK_AT"),
    DIALOGMOTE_REFERAT("OPPF_DM_REF_AT"),
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
