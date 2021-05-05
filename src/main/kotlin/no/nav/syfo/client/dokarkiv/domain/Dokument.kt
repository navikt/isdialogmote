package no.nav.syfo.client.dokarkiv.domain

// TODO: Find appropriate type
enum class BrevkodeType(
    val value: String,
) {
    DIALOGMOTE_INNKALLING("OPPF_DM_INK_AT"),
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
