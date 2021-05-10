package no.nav.syfo.dialogmote.domain

import no.nav.syfo.client.pdfgen.model.*
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.Virksomhetsnummer
import java.time.LocalDateTime

data class NewDialogmote(
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
    val fritekstInnkalling: String? = "",
)

data class NewDialogmotedeltakerArbeidsgiver(
    val virksomhetsnummer: Virksomhetsnummer,
    val fritekstInnkalling: String? = "",
    val lederNavn: String?,
    val lederEpost: String?,
)

data class NewDialogmoteTidSted(
    val sted: String,
    val tid: LocalDateTime,
    val videoLink: String? = "",
)

fun NewDialogmote.toPdfModelInnkallingArbeidstaker() =
    PdfModelInnkallingArbeidstaker(
        innkalling = InnkallingArbeidstaker(
            tidOgSted = InnkallingArbeidstakerTidOgSted(
                sted = this.tidSted.sted,
                videoLink = this.tidSted.videoLink,
            ),
        ),
    )

fun NewDialogmote.toPdfModelInnkallingArbeidsgiver() =
    PdfModelInnkallingArbeidsgiver(
        innkalling = InnkallingArbeidsgiver(
            tidOgSted = InnkallingArbeidsgiverTidOgSted(
                sted = this.tidSted.sted,
                videoLink = this.tidSted.videoLink,
            ),
        ),
    )

fun NewDialogmoteTidSted.toPdfModelEndringTidStedArbeidstaker() =
    PdfModelEndringTidStedArbeidstaker(
        endring = EndringTidStedArbeidstaker(
            tidOgSted = EndringTidStedArbeidstakerTidOgSted(
                sted = this.sted,
                videoLink = this.videoLink,
            ),
        ),
    )

fun NewDialogmoteTidSted.toPdfModelEndringTidStedArbeidsgiver() =
    PdfModelEndringTidStedArbeidsgiver(
        endring = EndringTidStedArbeidsgiver(
            tidOgSted = EndringTidStedArbeidsgiverTidOgSted(
                sted = this.sted,
                videoLink = this.videoLink,
            ),
        ),
    )
