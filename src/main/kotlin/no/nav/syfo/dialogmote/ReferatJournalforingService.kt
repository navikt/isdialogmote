package no.nav.syfo.dialogmote

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.dialogmote.database.domain.toDialogmoteDeltakerAnnen
import no.nav.syfo.dialogmote.database.domain.toReferat
import no.nav.syfo.dialogmote.database.getAndreDeltakereForReferatID
import no.nav.syfo.dialogmote.database.getMoteDeltakerArbeidsgiver
import no.nav.syfo.dialogmote.database.getMoteDeltakerArbeidstaker
import no.nav.syfo.dialogmote.database.getReferatWithoutJournalpostList
import no.nav.syfo.dialogmote.database.updateReferatJournalpostId
import no.nav.syfo.dialogmote.domain.Referat
import no.nav.syfo.domain.PersonIdentNumber

class ReferatJournalforingService(
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
