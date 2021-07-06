package no.nav.syfo.client.narmesteleder

import java.io.Serializable
import java.time.LocalDate
import java.time.OffsetDateTime

data class NarmesteLederDTO(
    val fnr: String,
    val orgnummer: String,
    val narmesteLederFnr: String,
    val narmesteLederTelefonnummer: String,
    val narmesteLederEpost: String? = null,
    val aktivFom: LocalDate,
    val aktivTom: LocalDate? = null,
    val arbeidsgiverForskutterer: Boolean? = null,
    val tilganger: List<Tilgang>,
    val timestamp: OffsetDateTime,
    val navn: String?,
) : Serializable

enum class Tilgang {
    SYKMELDING,
    SYKEPENGESOKNAD,
    MOTE,
    OPPFOLGINGSPLAN
}
