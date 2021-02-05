package no.nav.syfo.client.moteplanlegger.domain

import java.time.LocalDateTime

data class PlanlagtTidOgStedDTO(
    val id: Long,
    val tid: LocalDateTime,
    val created: LocalDateTime,
    val sted: String,
    val valgt: Boolean,
)
