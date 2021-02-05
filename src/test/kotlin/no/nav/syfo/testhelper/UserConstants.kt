package no.nav.syfo.testhelper

import no.nav.syfo.domain.PersonIdentNumber

object UserConstants {
    val ARBEIDSTAKER_FNR = PersonIdentNumber("12345678912")
    val ARBEIDSTAKER_2_FNR = PersonIdentNumber(ARBEIDSTAKER_FNR.value.replace("2", "1"))
    val ARBEIDSTAKER_ADRESSEBESKYTTET = PersonIdentNumber(ARBEIDSTAKER_FNR.value.replace("2", "6"))
    val ARBEIDSTAKER_IKKE_VARSEL = PersonIdentNumber(ARBEIDSTAKER_FNR.value.replace("2", "7"))

    val ARBEIDSTAKER_AKTORID = "10" + ARBEIDSTAKER_FNR.value
    const val VEILEDER_IDENT = "Z999999"
    const val ENHET_NR = "1000"

    const val PERSON_TLF = "12345678"
    const val PERSON_EMAIL = "test@nav.no"
}
