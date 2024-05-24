package no.nav.syfo.janitor.kafka

data class JanitorEventStatusDTO(
    val eventUUID: String,
    val status: JanitorEventStatus,
)

enum class JanitorEventStatus {
    CREATED,
    OK,
    FAILED,
}
