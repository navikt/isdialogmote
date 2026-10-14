package no.nav.syfo.api.dto

import no.nav.syfo.domain.dialogmote.DocumentComponentDTO
import no.nav.syfo.domain.dialogmote.TidStedDTO
import java.time.LocalDateTime

data class EndreDialogmoteDTO(
    override val sted: String,
    override val tid: LocalDateTime,
    override val videoLink: String?,
    val arbeidstaker: EndreMoteBegrunnelseDTO,
    val arbeidsgiver: EndreMoteBegrunnelseDTO,
    val behandler: EndreMoteBegrunnelseBehandlerDTO?,
) : TidStedDTO()

data class EndreMoteBegrunnelseBehandlerDTO(
    val begrunnelse: String,
    val dokument: List<DocumentComponentDTO>,
    val deltaker: NewDialogmotedeltakerBehandlerDTO?,
)
