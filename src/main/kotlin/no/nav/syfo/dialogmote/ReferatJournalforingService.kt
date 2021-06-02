package no.nav.syfo.dialogmote

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.dialogmote.database.*
import no.nav.syfo.dialogmote.database.domain.*
import no.nav.syfo.dialogmote.domain.*
import no.nav.syfo.domain.PersonIdentNumber

class ReferatJournalforingService(
    private val database: DatabaseInterface
) {
    fun getDialogmoteReferatJournalforingList(): List<Pair<PersonIdentNumber, Referat>> {
        return database.getReferatWithoutJournalpostList().map { (personIdentNumber, pReferat) ->
            val andreDeltakere = database.getAndreDeltakereForReferatID(pReferat.id).map {
                it.toDialogmoteDeltakerAnnen()
            }
            Pair(personIdentNumber, pReferat.toReferat(andreDeltakere))
        }
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
