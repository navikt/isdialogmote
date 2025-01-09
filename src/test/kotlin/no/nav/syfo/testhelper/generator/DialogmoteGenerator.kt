package no.nav.syfo.testhelper.generator

import no.nav.syfo.dialogmote.domain.*
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.UserConstants.VIRKSOMHETSNUMMER_HAS_NARMESTELEDER
import java.time.LocalDateTime
import java.util.*

val DIALOGMOTE_TIDSPUNKT_FIXTURE = LocalDateTime.now().plusDays(30L)

fun generateNewDialogmoteTidSted() = NewDialogmoteTidSted(
    sted = "This is a very lang text that has a lot of characters and describes where the meeting will take place.",
    tid = DIALOGMOTE_TIDSPUNKT_FIXTURE,
    videoLink = "https://meet.google.com/xyz"
)

fun generateMotedeltakerArbeidstaker(
    personIdent: PersonIdent,
) = NewDialogmotedeltakerArbeidstaker(
    personIdent = personIdent,
    fritekstInnkalling = "Ipsum lorum arbeidstaker"
)

fun generateMotedeltakerArbeidsgiver() = NewDialogmotedeltakerArbeidsgiver(
    virksomhetsnummer = VIRKSOMHETSNUMMER_HAS_NARMESTELEDER,
    fritekstInnkalling = "Ipsum lorum arbeidsgiver"
)

fun generateMotedeltakerBehandler() = NewDialogmotedeltakerBehandler(
    personIdent = UserConstants.BEHANDLER_FNR,
    behandlerRef = "1234",
    behandlerNavn = UserConstants.BEHANDLER_NAVN,
    behandlerKontor = UserConstants.BEHANDLER_KONTOR,
)

fun generateNewDialogmote(
    personIdent: PersonIdent,
    status: DialogmoteStatus = DialogmoteStatus.INNKALT,
): NewDialogmote = NewDialogmote(
    status = status,
    opprettetAv = UserConstants.VEILEDER_IDENT,
    tildeltVeilederIdent = UserConstants.VEILEDER_IDENT,
    tildeltEnhet = UserConstants.ENHET_NR.value,
    arbeidstaker = generateMotedeltakerArbeidstaker(personIdent),
    arbeidsgiver = generateMotedeltakerArbeidsgiver(),
    tidSted = generateNewDialogmoteTidSted()
)

fun generateNewDialogmoteWithBehandler(
    personIdent: PersonIdent,
    status: DialogmoteStatus = DialogmoteStatus.INNKALT,
): NewDialogmote = NewDialogmote(
    status = status,
    opprettetAv = UserConstants.VEILEDER_IDENT,
    tildeltVeilederIdent = UserConstants.VEILEDER_IDENT,
    tildeltEnhet = UserConstants.ENHET_NR.value,
    arbeidstaker = generateMotedeltakerArbeidstaker(personIdent),
    arbeidsgiver = generateMotedeltakerArbeidsgiver(),
    tidSted = generateNewDialogmoteTidSted(),
    behandler = generateMotedeltakerBehandler()
)
fun generateDialogmotedeltakerArbeidsgiver() = DialogmotedeltakerArbeidsgiver(
    id = 1,
    uuid = UUID.randomUUID(),
    createdAt = LocalDateTime.now().minusMonths(6L),
    updatedAt = LocalDateTime.now().minusMonths(6L),
    moteId = 1,
    virksomhetsnummer = VIRKSOMHETSNUMMER_HAS_NARMESTELEDER,
    type = DialogmotedeltakerType.ARBEIDSGIVER,
    varselList = listOf(
        DialogmotedeltakerArbeidsgiverVarsel(
            id = 1,
            uuid = UUID.randomUUID(),
            createdAt = LocalDateTime.now().minusMonths(6L),
            updatedAt = LocalDateTime.now().minusMonths(6L),
            motedeltakerArbeidsgiverId = 1,
            varselType = MotedeltakerVarselType.INNKALT,
            pdfId = 1,
            status = "status",
            lestDatoArbeidsgiver = null,
            fritekst = "tekst",
            document = emptyList(),
            svarType = DialogmoteSvarType.KOMMER,
            svarTekst = "tekst",
            svarTidspunkt = LocalDateTime.now().minusMonths(5L)
        ),
        DialogmotedeltakerArbeidsgiverVarsel(
            id = 1,
            uuid = UUID.randomUUID(),
            createdAt = LocalDateTime.now().minusMonths(3L),
            updatedAt = LocalDateTime.now().minusMonths(3L),
            motedeltakerArbeidsgiverId = 1,
            varselType = MotedeltakerVarselType.INNKALT,
            pdfId = 1,
            status = "status",
            lestDatoArbeidsgiver = null,
            fritekst = "tekst",
            document = emptyList(),
            svarType = DialogmoteSvarType.KOMMER,
            svarTekst = "tekst",
            svarTidspunkt = LocalDateTime.now().minusMonths(5L)
        )
    )
)

fun generateDialogmotedeltakerArbeidstaker() = DialogmotedeltakerArbeidstaker(
    id = 1,
    uuid = UUID.randomUUID(),
    createdAt = LocalDateTime.now().minusMonths(6L),
    updatedAt = LocalDateTime.now().minusMonths(6L),
    moteId = 1,
    personIdent = UserConstants.ARBEIDSTAKER_FNR,
    type = DialogmotedeltakerType.ARBEIDSTAKER,
    emptyList()
)

fun generateReferat(monthsPrior: Long) = Referat(
    id = 1,
    uuid = UUID.randomUUID(),
    createdAt = LocalDateTime.now().minusMonths(monthsPrior),
    updatedAt = LocalDateTime.now().minusMonths(monthsPrior),
    moteId = 1,
    motedeltakerArbeidstakerId = 1,
    motedeltakerArbeidsgiverId = 1,
    digitalt = true,
    situasjon = "situasjon",
    konklusjon = "konklusjon",
    arbeidstakerOppgave = "AToppgave",
    arbeidsgiverOppgave = "AGoppgave",
    veilederOppgave = null,
    behandlerOppgave = null,
    narmesteLederNavn = "NLnavn",
    document = emptyList(),
    pdfId = null,
    journalpostIdArbeidstaker = null,
    lestDatoArbeidstaker = null,
    lestDatoArbeidsgiver = null,
    andreDeltakere = emptyList(),
    brevBestillingsId = null,
    brevBestiltTidspunkt = null,
    ferdigstilt = true,
    begrunnelseEndring = null
)

fun generateDialogmote() = Dialogmote(
    id = 1,
    uuid = UUID.randomUUID(),
    createdAt = LocalDateTime.now().minusMonths(6L),
    updatedAt = LocalDateTime.now().minusMonths(6L),
    status = DialogmoteStatus.FERDIGSTILT,
    opprettetAv = "Z123456",
    tildeltVeilederIdent = "Z123456",
    tildeltEnhet = "3040",
    arbeidstaker = generateDialogmotedeltakerArbeidstaker(),
    arbeidsgiver = generateDialogmotedeltakerArbeidsgiver(),
    null,
    listOf(
        DialogmoteTidSted(
            id = 1,
            uuid = UUID.randomUUID(),
            createdAt = LocalDateTime.now().minusMonths(6L),
            updatedAt = LocalDateTime.now().minusMonths(6L),
            moteId = 1,
            sted = "sted",
            tid = LocalDateTime.now().plusDays(10L),
            videoLink = null
        ),
        DialogmoteTidSted(
            id = 1,
            uuid = UUID.randomUUID(),
            createdAt = LocalDateTime.now().minusMonths(3L),
            updatedAt = LocalDateTime.now().minusMonths(3L),
            moteId = 1,
            sted = "sted",
            tid = LocalDateTime.now().plusDays(10L),
            videoLink = null
        )
    ),
    listOf(
        generateReferat(6L),
        generateReferat(3L),
    )
)
