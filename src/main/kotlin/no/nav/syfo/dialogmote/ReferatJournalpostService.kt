package no.nav.syfo.dialogmote

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.dialogmote.database.*
import no.nav.syfo.dialogmote.database.domain.toDialogmoteDeltakerAnnen
import no.nav.syfo.dialogmote.database.domain.toReferat
import no.nav.syfo.dialogmote.domain.Referat
import no.nav.syfo.domain.PersonIdentNumber

class ReferatJournalpostService(
    private val database: DatabaseInterface
) {
    fun getDialogmoteReferatJournalforingList(): List<Pair<PersonIdentNumber, Referat>> {
        return database.getReferatWithoutJournalpostList().map { (personIdentNumber, pReferat) ->
            val andreDeltakere = database.getAndreDeltakereForReferatID(pReferat.id).map {
                it.toDialogmoteDeltakerAnnen()
            }
            val motedeltakerArbeidstakerId = database.getMoteDeltakerArbeidstaker(pReferat.moteId).id
            val motedeltakerArbeidsgiverId = database.getMoteDeltakerArbeidsgiver(pReferat.moteId).id

            Pair(
                first = personIdentNumber,
                second = pReferat.toReferat(
                    andreDeltakere = andreDeltakere,
                    motedeltakerArbeidstakerId = motedeltakerArbeidstakerId,
                    motedeltakerArbeidsgiverId = motedeltakerArbeidsgiverId
                )
            )
        }
    }

    fun getDialogmoteReferatForJournalpostDistribusjonList(): List<Pair<Int, String?>> {
        return database.getReferatForFysiskBrevUtsending().map { Pair(it.id, it.journalpostId) }
    }

    fun updateBestillingsId(
        referatId: Int,
        bestillingsId: String,
    ) {
        database.updateReferatBrevBestillingsId(
            referatId = referatId,
            brevBestillingsId = bestillingsId
        )
    }

    fun updateJournalpostIdForReferat(
        referat: Referat,
        journalpostId: Int,
    ) {
        database.updateReferatJournalpostId(
            referatId = referat.id,
            journalpostId = journalpostId
        )
    }
}
