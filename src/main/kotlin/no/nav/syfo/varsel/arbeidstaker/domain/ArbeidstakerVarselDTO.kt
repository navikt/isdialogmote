package no.nav.syfo.varsel.arbeidstaker.domain

import no.nav.syfo.dialogmote.domain.DocumentComponentDTO
import java.time.LocalDateTime

data class ArbeidstakerVarselDTO(
    val uuid: String,
    val deltakerUuid: String,
    val createdAt: LocalDateTime,
    val varselType: String,
    val digitalt: Boolean,
    val lestDato: LocalDateTime?,
    val fritekst: String,
    val sted: String,
    val tid: LocalDateTime,
    val videoLink: String?,
    val document: List<DocumentComponentDTO>,
    val virksomhetsnummer: String,
)
