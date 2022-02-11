package no.nav.syfo.client.person.oppfolgingstilfelle

import java.time.LocalDate

const val ARBEIDSGIVERPERIODE_DAYS = 16L

data class OppfolgingstilfellePerson(
    val fom: LocalDate,
    val tom: LocalDate,
)

fun OppfolgingstilfellePerson.isInactive(): Boolean =
    LocalDate.now().isAfter(this.tom.plusDays(ARBEIDSGIVERPERIODE_DAYS))
