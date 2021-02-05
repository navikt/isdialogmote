package no.nav.syfo.client.narmesteleder

import java.io.Serializable
import java.time.LocalDate

data class NarmesteLederDTO(
    val navn: String,
    val id: Long? = null,
    val aktoerId: String,
    val tlf: String? = null,
    val epost: String? = null,
    val aktiv: Boolean? = null,
    val erOppgitt: Boolean? = null,
    val fomDato: LocalDate,
    val orgnummer: String,
    val organisasjonsnavn: String,
    val aktivTom: LocalDate? = null,
    val arbeidsgiverForskuttererLoenn: Boolean? = null
) : Serializable
