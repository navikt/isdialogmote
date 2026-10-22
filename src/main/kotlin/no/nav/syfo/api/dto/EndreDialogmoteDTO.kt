package no.nav.syfo.api.dto

import no.nav.syfo.domain.Personident
import no.nav.syfo.domain.dialogmote.DocumentComponentDTO
import no.nav.syfo.domain.dialogmote.NewDialogmotedeltakerBehandler
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
    val fritekst: String,
    val moteDokument: List<DocumentComponentDTO>,
    val deltaker: EndreMoteNyBehandlerDTO?,
)

data class EndreMoteNyBehandlerDTO(
    val personIdent: String?,
    val behandlerRef: String,
    val behandlerNavn: String,
    val behandlerKontor: String,
) {
    fun toNewDialogmotedeltakerBehandler(fritekst: String) =
        NewDialogmotedeltakerBehandler(
            personident = personIdent?.let { Personident(it) },
            behandlerRef = behandlerRef,
            behandlerNavn = behandlerNavn,
            behandlerKontor = behandlerKontor,
            fritekstInnkalling = fritekst,
        )
}
