package no.nav.syfo.testhelper

import no.nav.syfo.domain.*

object UserConstants {
    val ARBEIDSTAKER_FNR = PersonIdent("12345678912")
    val ARBEIDSTAKER_ANNEN_FNR = PersonIdent("12345678913")
    val ARBEIDSTAKER_TREDJE_FNR = PersonIdent("12345678914")
    val ARBEIDSTAKER_FJERDE_FNR = PersonIdent("12345678915")
    val ARBEIDSTAKER_IKKE_AKTIVT_FNR = PersonIdent("12345678925")
    val ARBEIDSTAKER_WITH_ERROR_FNR = PersonIdent("12345678666")
    val ARBEIDSTAKERNAVN = "Fornavn Etternavn"
    val ARBEIDSTAKER_VEILEDER_NO_ACCESS = PersonIdent(ARBEIDSTAKER_FNR.value.replace("2", "1"))
    val ARBEIDSTAKER_NO_JOURNALFORING = PersonIdent(ARBEIDSTAKER_FNR.value.replace("2", "3"))
    val ARBEIDSTAKER_IKKE_VARSEL = PersonIdent(ARBEIDSTAKER_FNR.value.replace("2", "7"))
    val ARBEIDSTAKER_DKIF_FEIL = PersonIdent(ARBEIDSTAKER_FNR.value.replace("2", "8"))
    val ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER = PersonIdent(ARBEIDSTAKER_FNR.value.replace("2", "8"))
    val ARBEIDSTAKER_INACTIVE_OPPFOLGINGSTILFELLE = PersonIdent(ARBEIDSTAKER_FNR.value.replace("2", "9"))
    val ARBEIDSTAKER_NO_BEHANDLENDE_ENHET = PersonIdent(ARBEIDSTAKER_FNR.value.replace("3", "1"))
    val ARBEIDSTAKER_NO_OPPFOLGINGSTILFELLE = PersonIdent(ARBEIDSTAKER_FNR.value.replace("3", "4"))

    const val VEILEDER_IDENT = "Z999999"
    const val VEILEDER_IDENT_2 = "Z999998"
    val ENHET_NR = EnhetNr("1000")
    val ENHET_NR_NO_ACCESS = EnhetNr(ENHET_NR.value.replace("1", "2"))

    val BEHANDLER_FNR = PersonIdent("19122002920")
    val BEHANDLER_ANNEN_FNR = PersonIdent(BEHANDLER_FNR.value.replace("2", "4"))
    val BEHANDLER_REF = "behref"
    val BEHANDLER_NAVN = "Navn Lege"
    val BEHANDLER_KONTOR = "Legekontoret"

    const val PERSON_TLF = "12345678"
    const val PERSON_EMAIL = "test@nav.no"
    const val PERSON_FORNAVN = "ULLEN"
    const val PERSON_MELLOMNAVN = "Mellomnavn"
    const val PERSON_ETTERNAVN = "Bamse"

    val NARMESTELEDER_FNR = PersonIdent("98765432101")
    val NARMESTELEDER_FNR_2 = PersonIdent("98765432102")
    val VIRKSOMHETSNUMMER_NO_NARMESTELEDER = Virksomhetsnummer("981111117")
    val VIRKSOMHETSNUMMER_HAS_NARMESTELEDER = Virksomhetsnummer("934567897")
    val OTHER_VIRKSOMHETSNUMMER_HAS_NARMESTELEDER = Virksomhetsnummer("922222223")

    val JOURNALPOST_ID_MOTTAKER_GONE = 129

    val AZUREAD_TOKEN = "tokenReturnedByAzureAd"

    val JWT_AZP = "syfomodiaperson"
}
