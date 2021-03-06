package no.nav.syfo.testhelper

import no.nav.syfo.domain.*

object UserConstants {
    val ARBEIDSTAKER_FNR = PersonIdentNumber("12345678912")
    val ARBEIDSTAKER_ANNEN_FNR = PersonIdentNumber("12345678913")
    val ARBEIDSTAKER_VEILEDER_NO_ACCESS = PersonIdentNumber(ARBEIDSTAKER_FNR.value.replace("2", "1"))
    val ARBEIDSTAKER_NO_JOURNALFORING = PersonIdentNumber(ARBEIDSTAKER_FNR.value.replace("2", "3"))
    val ARBEIDSTAKER_ADRESSEBESKYTTET = PersonIdentNumber(ARBEIDSTAKER_FNR.value.replace("2", "6"))
    val ARBEIDSTAKER_IKKE_VARSEL = PersonIdentNumber(ARBEIDSTAKER_FNR.value.replace("2", "7"))
    val ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER = PersonIdentNumber(ARBEIDSTAKER_FNR.value.replace("2", "8"))

    val ARBEIDSTAKER_AKTORID = "10" + ARBEIDSTAKER_FNR.value
    const val VEILEDER_IDENT = "Z999999"
    const val VEILEDER_IDENT_2 = "Z999998"
    val ENHET_NR = EnhetNr("1000")
    val ENHET_NR_NO_ACCESS = EnhetNr(ENHET_NR.value.replace("1", "2"))

    const val PERSON_TLF = "12345678"
    const val PERSON_EMAIL = "test@nav.no"

    val VIRKSOMHETSNUMMER_HAS_NARMESTELEDER = Virksomhetsnummer("912345678")
}
