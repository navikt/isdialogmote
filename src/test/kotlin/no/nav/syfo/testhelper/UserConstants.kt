package no.nav.syfo.testhelper

import no.nav.syfo.domain.PersonIdentNumber

object UserConstants {
    val ARBEIDSTAKER_FNR = PersonIdentNumber("12345678912")
    val ARBEIDSTAKER_2_FNR = PersonIdentNumber(ARBEIDSTAKER_FNR.value.replace("2", "1"))
}
