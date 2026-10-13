package no.nav.syfo.api.dto

import no.nav.syfo.domain.dialogmote.DocumentComponentDTO
import no.nav.syfo.domain.dialogmote.TidStedDTO
import java.time.LocalDateTime

data class EndreDialogmoteDTO(
    override val sted: String,
    override val tid: LocalDateTime,
    override val videoLink: String?,
    val arbeidstaker: EndreTidStedBegrunnelseDTO,
    val arbeidsgiver: EndreTidStedBegrunnelseDTO,
    val behandler: EndreDialogmoteBehandlerDTO?,
) : TidStedDTO()

/**
 * begrunnelse/endringsdokument is the innkalling for a new behandler, or the endring for an unchanged one.
 * Identity fields are set when adding or replacing the behandler, avlysning when replacing.
 */
data class EndreDialogmoteBehandlerDTO(
    val begrunnelse: String,
    val endringsdokument: List<DocumentComponentDTO>,
    val behandlerRef: String? = null,
    val behandlerNavn: String? = null,
    val behandlerKontor: String? = null,
    val personIdent: String? = null,
    val avlysning: List<DocumentComponentDTO>? = null,
)
