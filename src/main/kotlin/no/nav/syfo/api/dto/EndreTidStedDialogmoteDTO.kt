package no.nav.syfo.api.dto

import no.nav.syfo.domain.dialogmote.DocumentComponentDTO
import no.nav.syfo.domain.dialogmote.TidStedDTO
import java.time.LocalDateTime

data class EndreTidStedDialogmoteDTO(
    override val sted: String,
    override val tid: LocalDateTime,
    override val videoLink: String?,
    val arbeidstaker: EndreTidStedBegrunnelseDTO,
    val arbeidsgiver: EndreTidStedBegrunnelseDTO,
    val behandler: EndreTidStedBegrunnelseDTO?,
) : TidStedDTO()

data class EndreTidStedBegrunnelseDTO(
    val begrunnelse: String,
    val endringsdokument: List<DocumentComponentDTO>,
)
