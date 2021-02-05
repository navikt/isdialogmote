package no.nav.syfo.client.moteplanlegger.domain

import java.time.LocalDateTime

data class PlanlagtMoteDeltakerDTO(
    val deltakerUuid: String,
    val navn: String? = null,
    val fnr: String? = null,
    val orgnummer: String? = null,
    val epost: String? = null,
    val type: String,
    val svartidspunkt: LocalDateTime? = null,
    val svar: List<PlanlagtTidOgStedDTO>,
)
