package no.nav.syfo.dialogmote.domain

data class DocumentComponentDTO(
    val type: DocumentComponentType,
    val title: String?,
    val texts: List<String>,
)

enum class DocumentComponentType {
    HEADER,
    PARAGRAPH,
    LINK,
}
