package no.nav.syfo.brev.arbeidstaker.domain

import no.nav.syfo.dialogmote.domain.DocumentComponentDTO
import java.time.LocalDateTime

data class ArbeidstakerBrevDTO(
    val uuid: String,
    val deltakerUuid: String,
    val createdAt: LocalDateTime,
    val brevType: String,
    val digitalt: Boolean,
    val lestDato: LocalDateTime?,
    val fritekst: String,
    val sted: String,
    val tid: LocalDateTime,
    val videoLink: String?,
    val document: List<DocumentComponentDTO>,
    val virksomhetsnummer: String,
    val svar: ArbeidstakerBrevSvarDTO?,
)

data class ArbeidstakerBrevSvarDTO(
    val svarTidspunkt: LocalDateTime,
    val svarType: String,
    val svarTekst: String?,
)
