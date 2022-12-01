package no.nav.syfo.testhelper.generator

import no.nav.syfo.dialogmelding.domain.DialogmeldingSvar
import no.nav.syfo.dialogmelding.domain.ForesporselType
import no.nav.syfo.dialogmelding.domain.InnkallingDialogmoteSvar
import no.nav.syfo.dialogmelding.domain.SvarType
import no.nav.syfo.testhelper.UserConstants
import java.time.LocalDateTime

fun generateDialogmoteSvar() = DialogmeldingSvar(
    arbeidstakerPersonIdent = UserConstants.ARBEIDSTAKER_FNR,
    behandlerPersonIdent = UserConstants.BEHANDLER_FNR,
    innkallingDialogmoteSvar = InnkallingDialogmoteSvar(
        foresporselType = ForesporselType.INNKALLING,
        svarType = SvarType.KAN_IKKE_KOMME,
        svarTekst = "tekst",
    ),
    conversationRef = "123456789",
    parentRef = null,
    opprettetTidspunkt = LocalDateTime.now(),
)
