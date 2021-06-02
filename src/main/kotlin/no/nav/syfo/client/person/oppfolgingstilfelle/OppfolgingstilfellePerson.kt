package no.nav.syfo.client.person.oppfolgingstilfelle

import java.time.LocalDate

data class OppfolgingstilfellePerson(
    val fom: LocalDate,
    val tom: LocalDate,
)
