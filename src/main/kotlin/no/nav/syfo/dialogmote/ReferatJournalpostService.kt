package no.nav.syfo.dialogmote

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.dialogmote.database.*
import no.nav.syfo.dialogmote.database.domain.*
import no.nav.syfo.dialogmote.domain.DialogmotedeltakerBehandler
import no.nav.syfo.dialogmote.domain.Referat
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Virksomhetsnummer
import java.time.LocalDateTime

class ReferatJournalpostService(
    private val database: DatabaseInterface
) {
    fun getDialogmoteReferatJournalforingListArbeidstaker(): List<Pair<PersonIdent, Referat>> {
        return database.getFerdigstilteReferatWithoutJournalpostArbeidstakerList().map { (personIdent, pReferat) ->
            val andreDeltakere = database.getAndreDeltakereForReferatID(pReferat.id).map {
                it.toDialogmoteDeltakerAnnen()
            }
            val motedeltakerArbeidstakerId = database.getMoteDeltakerArbeidstaker(pReferat.moteId).id
            val motedeltakerArbeidsgiverId = database.getMoteDeltakerArbeidsgiver(pReferat.moteId).id

            Pair(
                first = personIdent,
                second = pReferat.toReferat(
                    andreDeltakere = andreDeltakere,
                    motedeltakerArbeidstakerId = motedeltakerArbeidstakerId,
                    motedeltakerArbeidsgiverId = motedeltakerArbeidsgiverId
                )
            )
        }
    }

    fun getDialogmoteReferatJournalforingListArbeidsgiver(): List<Triple<Virksomhetsnummer, PersonIdent, Referat>> {
        return database.getFerdigstilteReferatWithoutJournalpostArbeidsgiverList().map { (virksomhetsnummer, pReferat) ->
            val andreDeltakere = database.getAndreDeltakereForReferatID(pReferat.id).map {
                it.toDialogmoteDeltakerAnnen()
            }
            val motedeltakerArbeidstaker = database.getMoteDeltakerArbeidstaker(pReferat.moteId)
            val motedeltakerArbeidsgiverId = database.getMoteDeltakerArbeidsgiver(pReferat.moteId).id

            Triple(
                first = virksomhetsnummer,
                second = motedeltakerArbeidstaker.personIdent,
                third = pReferat.toReferat(
                    andreDeltakere = andreDeltakere,
                    motedeltakerArbeidstakerId = motedeltakerArbeidstaker.id,
                    motedeltakerArbeidsgiverId = motedeltakerArbeidsgiverId
                )
            )
        }
    }

    fun getDialogmoteReferatJournalforingListBehandler(): List<Triple<PersonIdent, DialogmotedeltakerBehandler, Referat>> {
        return database.getFerdigstilteReferatWithoutJournalpostBehandlerList().map { pReferat ->
            val andreDeltakere = database.getAndreDeltakereForReferatID(pReferat.id).map {
                it.toDialogmoteDeltakerAnnen()
            }
            val motedeltakerArbeidstaker = database.getMoteDeltakerArbeidstaker(pReferat.moteId)
            val motedeltakerArbeidsgiverId = database.getMoteDeltakerArbeidsgiver(pReferat.moteId).id
            val motedeltakerBehandler = database.getMoteDeltakerBehandler(pReferat.moteId)!!
                .toDialogmotedeltakerBehandler(emptyList())

            Triple(
                first = motedeltakerArbeidstaker.personIdent,
                second = motedeltakerBehandler,
                third = pReferat.toReferat(
                    andreDeltakere = andreDeltakere,
                    motedeltakerArbeidstakerId = motedeltakerArbeidstaker.id,
                    motedeltakerArbeidsgiverId = motedeltakerArbeidsgiverId
                )
            )
        }
    }

    fun getMotetidspunkt(moteId: Int): LocalDateTime? =
        database.getTidSted(moteId).maxByOrNull { it.createdAt }?.tid

    fun getDialogmoteReferatForJournalpostDistribusjonList(): List<Pair<Int, String?>> {
        return database.getReferatForFysiskBrevUtsending().map { Pair(it.id, it.journalpostIdArbeidstaker) }
    }

    fun updateBestillingsId(
        referatId: Int,
        bestillingsId: String?,
    ) {
        database.updateReferatBrevBestillingsId(
            referatId = referatId,
            brevBestillingsId = bestillingsId
        )
    }

    fun updateJournalpostIdArbeidstakerForReferat(
        referat: Referat,
        journalpostId: Int,
    ) {
        database.updateReferatJournalpostIdArbeidstaker(
            referatId = referat.id,
            journalpostId = journalpostId
        )
    }

    fun updateJournalpostIdArbeidsgiverForReferat(
        referat: Referat,
        journalpostId: Int,
    ) {
        database.updateReferatJournalpostIdArbeidsgiver(
            referatId = referat.id,
            journalpostId = journalpostId
        )
    }

    fun updateJournalpostIdBehandlerForReferat(
        referat: Referat,
        journalpostId: Int,
    ) {
        database.updateReferatJournalpostIdBehandler(
            referatId = referat.id,
            journalpostId = journalpostId
        )
    }
}
