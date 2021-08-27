package no.nav.syfo.dialogmote

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.dialogmote.database.*
import no.nav.syfo.dialogmote.database.domain.toDialogmotedeltakerArbeidstaker
import no.nav.syfo.dialogmote.domain.DialogmotedeltakerArbeidstakerVarsel
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.dialogmote.domain.MotedeltakerVarselType

class DialogmotedeltakerVarselJournalpostService(
    private val database: DatabaseInterface,
) {
    fun getDialogmotedeltakerArbeidstakerVarselForJournalforingList(): List<Pair<PersonIdentNumber, DialogmotedeltakerArbeidstakerVarsel>> {
        return getDialogmotedeltakerArbeidstakerVarselWithoutJournalpostList().filter { (_, arbeidstakerVarsel) ->
            journalforingVarselTypeList.contains(arbeidstakerVarsel.varselType)
        }
    }

    private fun getDialogmotedeltakerArbeidstakerVarselWithoutJournalpostList(): List<Pair<PersonIdentNumber, DialogmotedeltakerArbeidstakerVarsel>> {
        val motedeltakerArbeidtakerVarselList = database.getMotedeltakerArbeidstakerVarselWithoutJournalpost()
        return motedeltakerArbeidtakerVarselList.map {
            it.toDialogmotedeltakerArbeidstaker()
        }.map { motedeltakerArbeidstakerVarsel ->
            val motedeltakerArbeidstaker =
                database.getMotedeltakerArbeidstakerById(motedeltakerArbeidstakerVarsel.motedeltakerArbeidstakerId)
                    .toDialogmotedeltakerArbeidstaker(emptyList())
            Pair(
                motedeltakerArbeidstaker.personIdent,
                motedeltakerArbeidstakerVarsel,
            )
        }
    }

    fun getDialogmotedeltakerArbeidstakerVarselForJournalpostDistribusjonList(): List<DialogmotedeltakerArbeidstakerVarsel> {
        return database.getMotedeltakerArbeidstakerVarselForFysiskBrevUtsending()
            .map { it.toDialogmotedeltakerArbeidstaker() }
            .filter { journalforingVarselTypeList.contains(it.varselType) }
    }

    fun updateBestillingsId(
        dialogmotedeltakerArbeidstakerVarsel: DialogmotedeltakerArbeidstakerVarsel,
        bestillingsId: String,
    ) {
        database.updateMotedeltakerArbeidstakerBrevBestillingsId(
            motedeltakerArbeidstakerVarselId = dialogmotedeltakerArbeidstakerVarsel.id,
            brevBestillingsId = bestillingsId
        )
    }

    fun updateJournalpostId(
        dialogmotedeltakerArbeidstakerVarsel: DialogmotedeltakerArbeidstakerVarsel,
        journalpostId: Int,
    ) {
        database.updateMotedeltakerArbeidstakerVarselJournalpostId(
            motedeltakerArbeidstakerVarselId = dialogmotedeltakerArbeidstakerVarsel.id,
            journalpostId = journalpostId
        )
    }

    private val journalforingVarselTypeList = listOf(
        MotedeltakerVarselType.AVLYST,
        MotedeltakerVarselType.INNKALT,
        MotedeltakerVarselType.NYTT_TID_STED,
    )
}
