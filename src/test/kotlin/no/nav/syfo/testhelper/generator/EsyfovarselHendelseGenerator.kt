package no.nav.syfo.testhelper.generator

import no.nav.syfo.brev.esyfovarsel.DialogmoteInnkallingNarmesteLederData
import no.nav.syfo.brev.esyfovarsel.HendelseType
import no.nav.syfo.brev.esyfovarsel.NarmesteLederHendelse
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.UserConstants.NARMESTELEDER_FNR
import no.nav.syfo.testhelper.UserConstants.OTHER_VIRKSOMHETSNUMMER_HAS_NARMESTELEDER
import no.nav.syfo.testhelper.UserConstants.VIRKSOMHETSNUMMER_HAS_NARMESTELEDER

fun generateAvlysningHendelse() =
    NarmesteLederHendelse(
        type = HendelseType.NL_DIALOGMOTE_AVLYST,
        data = DialogmoteInnkallingNarmesteLederData("narmesteLederNavn"),
        narmesteLederFnr = NARMESTELEDER_FNR.value,
        arbeidstakerFnr = ARBEIDSTAKER_FNR.value,
        orgnummer = VIRKSOMHETSNUMMER_HAS_NARMESTELEDER.value
    )

fun generateInkallingHendelse() =
    NarmesteLederHendelse(
        type = HendelseType.NL_DIALOGMOTE_INNKALT,
        narmesteLederFnr = NARMESTELEDER_FNR.value,
        data = DialogmoteInnkallingNarmesteLederData("narmesteLederNavn"),
        arbeidstakerFnr = ARBEIDSTAKER_FNR.value,
        orgnummer = VIRKSOMHETSNUMMER_HAS_NARMESTELEDER.value
    )

fun generateEndringHendelse() =
    NarmesteLederHendelse(
        type = HendelseType.NL_DIALOGMOTE_NYTT_TID_STED,
        narmesteLederFnr = NARMESTELEDER_FNR.value,
        data = DialogmoteInnkallingNarmesteLederData("narmesteLederNavn"),
        arbeidstakerFnr = ARBEIDSTAKER_FNR.value,
        orgnummer = VIRKSOMHETSNUMMER_HAS_NARMESTELEDER.value
    )

fun generateInkallingHendelseOtherVirksomhet() =
    NarmesteLederHendelse(
        type = HendelseType.NL_DIALOGMOTE_INNKALT,
        narmesteLederFnr = NARMESTELEDER_FNR.value,
        data = DialogmoteInnkallingNarmesteLederData("narmesteLederNavn"),
        arbeidstakerFnr = ARBEIDSTAKER_FNR.value,
        orgnummer = OTHER_VIRKSOMHETSNUMMER_HAS_NARMESTELEDER.value
    )

fun generateReferatHendelse() =
    NarmesteLederHendelse(
        type = HendelseType.NL_DIALOGMOTE_REFERAT,
        narmesteLederFnr = NARMESTELEDER_FNR.value,
        data = DialogmoteInnkallingNarmesteLederData("narmesteLederNavn"),
        arbeidstakerFnr = ARBEIDSTAKER_FNR.value,
        orgnummer = VIRKSOMHETSNUMMER_HAS_NARMESTELEDER.value
    )
