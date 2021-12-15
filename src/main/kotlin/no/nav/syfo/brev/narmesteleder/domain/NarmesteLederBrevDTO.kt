package no.nav.syfo.brev.narmesteleder.domain

import no.nav.syfo.dialogmote.domain.DocumentComponentDTO
import java.time.LocalDateTime

data class NarmesteLederBrevDTO(
    val uuid: String,
    val deltakerUuid: String,
    val createdAt: LocalDateTime,
    val brevType: String,
    val lestDato: LocalDateTime?,
    val fritekst: String,
    val sted: String,
    val tid: LocalDateTime,
    val videoLink: String?,
    val document: List<DocumentComponentDTO>,
    val virksomhetsnummer: String,
    val svar: NarmesteLederBrevSvarDTO?,
)

data class NarmesteLederBrevSvarDTO(
    val svarTidspunkt: LocalDateTime,
    val svarType: String,
    val svarTekst: String?,
)
