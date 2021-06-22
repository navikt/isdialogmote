package no.nav.syfo.varsel.narmesteleder.domain

import no.nav.syfo.dialogmote.domain.DocumentComponentDTO
import java.time.LocalDateTime

data class NarmestelederBrevDTO(
    val uuid: String,
    val deltakerUuid: String,
    val createdAt: LocalDateTime,
    val varselType: String,
    val lestDato: LocalDateTime?,
    val fritekst: String,
    val sted: String,
    val tid: LocalDateTime,
    val videoLink: String?,
    val document: List<DocumentComponentDTO>,
    val virksomhetsnummer: String,
)
