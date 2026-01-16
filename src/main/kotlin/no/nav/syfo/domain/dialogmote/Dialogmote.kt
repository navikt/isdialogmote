package no.nav.syfo.domain.dialogmote

import no.nav.syfo.application.exception.ConflictException
import no.nav.syfo.domain.ArbeidstakerBrevDTO
import no.nav.syfo.domain.NarmesteLederBrevDTO
import no.nav.syfo.util.isAfterOrEqual
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

data class Dialogmote(
    val id: Int,
    val uuid: UUID,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val status: Status,
    val opprettetAv: String,
    val tildeltVeilederIdent: String,
    val tildeltEnhet: String,
    val arbeidstaker: DialogmotedeltakerArbeidstaker,
    val arbeidsgiver: DialogmotedeltakerArbeidsgiver,
    val behandler: DialogmotedeltakerBehandler?,
    val tidStedList: List<DialogmoteTidSted>,
    val referatList: List<Referat>,
) {
    enum class Status {
        INNKALT,
        AVLYST,
        FERDIGSTILT,
        NYTT_TID_STED,
        LUKKET,
    }

    fun sistMoteTidSted(): DialogmoteTidSted? {
        return tidStedList.maxBy { it.tid }
    }

    fun endreFerdigstiltReferat(): Dialogmote {
        if (this.status != Status.FERDIGSTILT) {
            throw ConflictException("Failed to Endre Ferdigstilt Dialogmote, not Ferdigstilt")
        }
        return this.copy(
            updatedAt = LocalDateTime.now(),
        )
    }

    fun avlysInnkalling(): Dialogmote {
        if (this.status == Status.FERDIGSTILT) {
            throw ConflictException("Failed to Avlys Dialogmote: already Ferdigstilt")
        }
        if (this.status == Status.AVLYST) {
            throw ConflictException("Failed to Avlys Dialogmote: already Avlyst")
        }
        return this.copy(
            status = Status.AVLYST,
            updatedAt = LocalDateTime.now(),
        )
    }

    fun nyttTidSted(): Dialogmote {
        if (this.status == Status.FERDIGSTILT) {
            throw ConflictException("Failed to change tid/sted, already Ferdigstilt")
        }
        if (this.status == Status.AVLYST) {
            throw ConflictException("Failed to change tid/sted, already Avlyst")
        }
        return this.copy(
            status = Status.NYTT_TID_STED,
            updatedAt = LocalDateTime.now(),
        )
    }

    fun ferdigstill(): Dialogmote {
        if (this.status == Status.FERDIGSTILT) {
            throw ConflictException("Failed to Ferdigstille Dialogmote, already Ferdigstilt")
        }
        if (this.status == Status.AVLYST) {
            throw ConflictException("Failed to Ferdigstille Dialogmote, already Avlyst")
        }
        return this.copy(
            status = Status.FERDIGSTILT,
            updatedAt = LocalDateTime.now(),
        )
    }

    fun isActive(): Boolean =
        this.status == Status.INNKALT || this.status == Status.NYTT_TID_STED
}

fun List<Dialogmote>.toArbeidstakerBrevDTOList(): List<ArbeidstakerBrevDTO> {
    return this.map { dialogmote ->
        val brevList = mutableListOf<ArbeidstakerBrevDTO>()
        dialogmote.referatList.ferdigstilte().map {
            brevList.add(
                it.toArbeidstakerBrevDTO(
                    dialogmoteTidSted = dialogmote.tidStedList.latest()!!,
                    deltakerUuid = dialogmote.arbeidstaker.uuid,
                    virksomhetsnummer = dialogmote.arbeidsgiver.virksomhetsnummer,
                )
            )
        }
        brevList.addAll(
            dialogmote.arbeidstaker.varselList.map {
                it.toArbeidstakerBrevDTO(
                    dialogmoteTidSted = dialogmote.tidStedList.latest()!!,
                    deltakerUuid = dialogmote.arbeidstaker.uuid,
                    virksomhetsnummer = dialogmote.arbeidsgiver.virksomhetsnummer,
                )
            }
        )
        brevList
    }.flatten()
}

fun List<Dialogmote>.toNarmesteLederBrevDTOList(): List<NarmesteLederBrevDTO> {
    return this.map { dialogmote ->
        val brevList = mutableListOf<NarmesteLederBrevDTO>()
        dialogmote.referatList.ferdigstilte().map {
            brevList.add(
                it.toNarmesteLederBrevDTO(
                    dialogmoteTidSted = dialogmote.tidStedList.latest()!!,
                    deltakerUuid = dialogmote.arbeidsgiver.uuid,
                    virksomhetsnummer = dialogmote.arbeidsgiver.virksomhetsnummer,
                )
            )
        }
        brevList.addAll(
            dialogmote.arbeidsgiver.varselList.map {
                it.toNarmesteLederBrevDTO(
                    dialogmoteTidSted = dialogmote.tidStedList.latest()!!,
                    deltakerUuid = dialogmote.arbeidsgiver.uuid,
                    virksomhetsnummer = dialogmote.arbeidsgiver.virksomhetsnummer,
                )
            }
        )
        brevList
    }.flatten()
}

fun List<Dialogmote>.anyActive(): Boolean = this.any { it.isActive() }

fun List<Dialogmote>.removeBrevBeforeDate(date: LocalDate): List<Dialogmote> {
    return this.map {
        it.copy(
            arbeidsgiver = it.arbeidsgiver.copy(
                varselList = it.arbeidsgiver.varselList.filter {
                    it.createdAt.toLocalDate().isAfterOrEqual(date)
                }
            ),
            referatList = it.referatList.filter { it.createdAt.toLocalDate().isAfterOrEqual(date) }
        )
    }
}
