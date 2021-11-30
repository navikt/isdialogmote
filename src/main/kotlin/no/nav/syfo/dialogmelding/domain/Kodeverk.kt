package no.nav.syfo.dialogmelding.domain

object Kodeverk {
    const val PREFIKS = "2.16.578.1.12.4.1.1."

    object ForesporselInnkallingDialogmote {
        const val KODE = "${PREFIKS}8125"
        const val INNKALLING = "1"
        const val ENDRING = "2"
    }

    object SvarInnkallingDialogmote {
        const val KODE = "${PREFIKS}8126"
        const val KOMMER = "1"
        const val NYTT_TIDSPUNKT = "2"
        const val KAN_IKKE_KOMME = "3"
    }
}
