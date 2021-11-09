package no.nav.syfo.dialogmote.domain

data class DocumentComponentDTO(
    val type: DocumentComponentType,
    val key: String? = null,
    val title: String?,
    val texts: List<String>,
)

enum class DocumentComponentType {
    HEADER,
    PARAGRAPH,
    LINK,
}

fun List<DocumentComponentDTO>.serialize(): String {
    return buildString {
        this@serialize.forEach { documentComponentDTO ->
            documentComponentDTO.title?.let {
                appendLine(it)
            }
            documentComponentDTO.texts.forEach {
                appendLine(it)
            }
            appendLine()
        }
    }
}
