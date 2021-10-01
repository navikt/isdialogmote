package no.nav.syfo.client.person.oppfolgingstilfelle

import java.time.LocalDate
import java.time.LocalDateTime

data class KOppfolgingstilfellePersonDTO(
    val aktorId: String,
    val tidslinje: List<KSyketilfelledagDTO>,
    val sisteDagIArbeidsgiverperiode: KSyketilfelledagDTO,
    val antallBrukteDager: Int,
    val oppbruktArbeidsgvierperiode: Boolean,
    val utsendelsestidspunkt: LocalDateTime,
)

fun KOppfolgingstilfellePersonDTO.toOppfolgingstilfellePerson() =
    OppfolgingstilfellePerson(
        fom = this.tidslinje.first().dag,
        tom = this.tidslinje.last().dag,
    )

data class KSyketilfellebitDTO(
    val id: String? = null,
    val aktorId: String,
    val orgnummer: String? = null,
    val opprettet: LocalDateTime,
    val inntruffet: LocalDateTime,
    val tags: List<String>,
    val ressursId: String,
    val fom: LocalDateTime,
    val tom: LocalDateTime,
)

data class KSyketilfelledagDTO(
    val dag: LocalDate,
    val prioritertSyketilfellebit: KSyketilfellebitDTO?,
)
