package no.nav.syfo.dialogmote.api.domain

import no.nav.syfo.dialogmote.domain.DocumentComponentDTO
import no.nav.syfo.dialogmote.domain.TidStedDTO
import java.time.LocalDateTime

data class EndreTidStedDialogmoteDTO(
    override val sted: String,
    override val tid: LocalDateTime,
    override val videoLink: String?,
    val arbeidstaker: EndreTidStedBegrunnelseDTO,
    val arbeidsgiver: EndreTidStedBegrunnelseDTO,
) : TidStedDTO()

data class EndreTidStedBegrunnelseDTO(
    val begrunnelse: String,
    val endringsdokument: List<DocumentComponentDTO>,
)
