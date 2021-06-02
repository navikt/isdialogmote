package no.nav.syfo.client.person.oppfolgingstilfelle

import java.time.LocalDate

data class OppfolgingstilfellePersonDTO(
    val fom: LocalDate,
    val tom: LocalDate,
)

fun OppfolgingstilfellePersonDTO.toOppfolgingstilfelle() =
    OppfolgingstilfellePerson(
        fom = this.fom,
        tom = this.tom,
    )
