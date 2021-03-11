package no.nav.syfo.dialogmote.domain

import no.nav.syfo.client.pdfgen.model.*
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.Virksomhetsnummer
import java.time.LocalDateTime
import java.util.*

data class NewDialogmote(
    val status: DialogmoteStatus,
    val opprettetAv: String,
    val tildeltVeilederIdent: String,
    val tildeltEnhet: String,
    val arbeidstaker: NewDialogmotedeltakerArbeidstaker,
    val arbeidsgiver: NewDialogmotedeltakerArbeidsgiver,
    val tidSted: NewDialogmoteTidSted,
)

data class NewDialogmotePlanlagt(
    val planlagtMoteUuid: UUID,
    val status: DialogmoteStatus,
    val opprettetAv: String,
    val tildeltVeilederIdent: String,
    val tildeltEnhet: String,
    val arbeidstaker: NewDialogmotedeltakerArbeidstaker,
    val arbeidsgiver: NewDialogmotedeltakerArbeidsgiver,
    val tidSted: NewDialogmoteTidSted,
)

data class NewDialogmotedeltakerArbeidstaker(
    val personIdent: PersonIdentNumber,
)

data class NewDialogmotedeltakerArbeidsgiver(
    val virksomhetsnummer: Virksomhetsnummer,
    val lederNavn: String?,
    val lederEpost: String?,
)

data class NewDialogmoteTidSted(
    val sted: String,
    val tid: LocalDateTime,
)

fun NewDialogmote.toPdfModelInnkallingArbeidstaker() =
    PdfModelInnkallingArbeidstaker(
        innkalling = InnkallingArbeidstaker(
            tidOgSted = InnkallingArbeidstakerTidOgSted(
                sted = this.tidSted.sted,
            ),
        ),
    )

fun NewDialogmotePlanlagt.toPdfModelInnkallingArbeidstaker() =
    PdfModelInnkallingArbeidstaker(
        innkalling = InnkallingArbeidstaker(
            tidOgSted = InnkallingArbeidstakerTidOgSted(
                sted = this.tidSted.sted,
            ),
        ),
    )

fun NewDialogmoteTidSted.toPdfModelEndringTidStedArbeidstaker() =
    PdfModelEndringTidStedArbeidstaker(
        endring = EndringTidStedArbeidstaker(
            tidOgSted = EndringTidStedArbeidstakerTidOgSted(
                sted = this.sted,
            ),
        ),
    )
