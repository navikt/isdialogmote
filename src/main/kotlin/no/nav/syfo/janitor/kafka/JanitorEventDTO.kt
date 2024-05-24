package no.nav.syfo.janitor.kafka

data class JanitorEventDTO(
    val referenceUUID: String,
    val navident: String,
    val eventUUID: String,
    val personident: String,
    val action: String,
)

enum class JanitorAction {
    LUKK_DIALOGMOTE
}
