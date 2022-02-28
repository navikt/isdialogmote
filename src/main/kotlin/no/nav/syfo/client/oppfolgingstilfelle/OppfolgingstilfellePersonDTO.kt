package no.nav.syfo.client.oppfolgingstilfelle

import java.time.LocalDate

data class OppfolgingstilfellePersonDTO(
    val oppfolgingstilfelleList: List<OppfolgingstilfelleDTO>,
    val personIdent: String,
)

data class OppfolgingstilfelleDTO(
    val arbeidstakerAtTilfelleEnd: Boolean,
    val start: LocalDate,
    val end: LocalDate,
    val virksomhetsnummerList: List<String>,
)

fun OppfolgingstilfellePersonDTO.toLatestOppfolgingstilfelle(): Oppfolgingstilfelle? =
    this.oppfolgingstilfelleList.maxByOrNull { oppfolgingstilfelle -> oppfolgingstilfelle.start }
        ?.let { oppfolgingstilfelleDTO ->
            Oppfolgingstilfelle(
                start = oppfolgingstilfelleDTO.start,
                end = oppfolgingstilfelleDTO.end,
            )
        }
