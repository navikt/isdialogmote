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

    val ARBEIDSTAKER_AKTORID = AktorId("10" + ARBEIDSTAKER_FNR.value)
    val ARBEIDSTAKER_ANNEN_AKTORID = AktorId("10" + ARBEIDSTAKER_ANNEN_FNR.value)
    val ARBEIDSTAKER_NO_JOURNALFORING_AKTORID = AktorId("10" + ARBEIDSTAKER_NO_JOURNALFORING.value)
    val ARBEIDSTAKER_IKKE_VARSEL_AKTORID = AktorId("10" + ARBEIDSTAKER_IKKE_VARSEL.value)
    const val VEILEDER_IDENT = "Z999999"
    const val VEILEDER_IDENT_2 = "Z999998"
    val ENHET_NR = EnhetNr("1000")
    val ENHET_NR_NO_ACCESS = EnhetNr(ENHET_NR.value.replace("1", "2"))

    val BEHANDLER_FNR = PersonIdentNumber("19122002920")
    val BEHANDLER_REF = "behref"
    val BEHANDLER_NAVN = "Navn Lege"
    val BEHANDLER_KONTOR = "Legekontoret"

    const val PERSON_TLF = "12345678"
    const val PERSON_EMAIL = "test@nav.no"
    const val PERSON_FORNAVN = "ULLEN"
    const val PERSON_MELLOMNAVN = "Mellomnavn"
    const val PERSON_ETTERNAVN = "Bamse"

    val NARMESTELEDER_FNR = PersonIdentNumber("98765432101")
    val NARMESTELEDER_FNR_2 = PersonIdentNumber("98765432102")
    val VIRKSOMHETSNUMMER_HAS_NARMESTELEDER = Virksomhetsnummer("912345678")
    val OTHER_VIRKSOMHETSNUMMER_HAS_NARMESTELEDER = Virksomhetsnummer("922222222")

    val AZUREAD_TOKEN = "tokenReturnedByAzureAd"

    val JWT_AZP = "syfomodiaperson"
}
