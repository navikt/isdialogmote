package no.nav.syfo.dialogmote

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.dialogmote.database.*
import no.nav.syfo.dialogmote.database.domain.*
import no.nav.syfo.dialogmote.domain.*
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.Virksomhetsnummer

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

    fun getDialogmotedeltakerArbeidsgiverVarselForJournalforingList(): List<Triple<Virksomhetsnummer, PersonIdentNumber, DialogmotedeltakerArbeidsgiverVarsel>> {
        return getDialogmotedeltakerArbeidsgiverVarselWithoutJournalpostList().filter { (_, _, arbeidsgiverVarsel) ->
            journalforingVarselTypeList.contains(arbeidsgiverVarsel.varselType)
        }
    }

    private fun getDialogmotedeltakerArbeidsgiverVarselWithoutJournalpostList(): List<Triple<Virksomhetsnummer, PersonIdentNumber, DialogmotedeltakerArbeidsgiverVarsel>> {
        val motedeltakerArbeidsgiverVarselList = database.getMotedeltakerArbeidsgiverVarselWithoutJournalpost()
        return motedeltakerArbeidsgiverVarselList.map {
            it.toDialogmotedeltakerArbeidsgiver()
        }.map { motedeltakerArbeidsgiverVarsel ->
            val motedeltakerArbeidsgiver =
                database.getMoteDeltakerArbeidsgiverById(motedeltakerArbeidsgiverVarsel.motedeltakerArbeidsgiverId)
                    .toDialogmotedeltakerArbeidsgiver(emptyList())
            val motedeltakerArbeidstaker =
                database.getMoteDeltakerArbeidstaker(motedeltakerArbeidsgiver.moteId)
                    .toDialogmotedeltakerArbeidstaker(emptyList())
            Triple(
                motedeltakerArbeidsgiver.virksomhetsnummer,
                motedeltakerArbeidstaker.personIdent,
                motedeltakerArbeidsgiverVarsel,
            )
        }
    }

    fun getDialogmotedeltakerBehandlerVarselForJournalforingList(): List<Triple<PersonIdentNumber, DialogmotedeltakerBehandler, DialogmotedeltakerBehandlerVarsel>> {
        return getDialogmotedeltakerBehandlerVarselWithoutJournalpostList().filter { (_, _, behandlerVarsel) ->
            journalforingVarselTypeList.contains(behandlerVarsel.varselType)
        }
    }

    private fun getDialogmotedeltakerBehandlerVarselWithoutJournalpostList(): List<Triple<PersonIdentNumber, DialogmotedeltakerBehandler, DialogmotedeltakerBehandlerVarsel>> {
        val motedeltakerBehandlerVarselList = database.getMotedeltakerBehandlerVarselWithoutJournalpost()
        return motedeltakerBehandlerVarselList.map {
            it.toDialogmotedeltakerBehandlerVarsel(emptyList())
        }.map { motedeltakerBehandlerVarsel ->
            val motedeltakerBehandler =
                database.getMotedeltakerBehandlerById(motedeltakerBehandlerVarsel.motedeltakerBehandlerId)
                    .toDialogmotedeltakerBehandler(emptyList())
            val motedeltakerArbeidstaker =
                database.getMoteDeltakerArbeidstaker(motedeltakerBehandler.moteId)
                    .toDialogmotedeltakerArbeidstaker(emptyList())
            Triple(
                motedeltakerArbeidstaker.personIdent,
                motedeltakerBehandler,
                motedeltakerBehandlerVarsel,
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

    fun updateArbeidstakerVarselJournalpostId(
        dialogmotedeltakerArbeidstakerVarsel: DialogmotedeltakerArbeidstakerVarsel,
        journalpostId: Int,
    ) {
        database.updateMotedeltakerArbeidstakerVarselJournalpostId(
            motedeltakerArbeidstakerVarselId = dialogmotedeltakerArbeidstakerVarsel.id,
            journalpostId = journalpostId
        )
    }

    fun updateArbeidsgiverVarselJournalpostId(
        dialogmotedeltakerArbeidsgiverVarsel: DialogmotedeltakerArbeidsgiverVarsel,
        journalpostId: Int,
    ) {
        database.updateMotedeltakerArbeidsgiverVarselJournalpostId(
            motedeltakerArbeidsgiverVarselId = dialogmotedeltakerArbeidsgiverVarsel.id,
            journalpostId = journalpostId
        )
    }

    fun updateBehandlerVarselJournalpostId(
        dialogmotedeltakerBehandlerVarsel: DialogmotedeltakerBehandlerVarsel,
        journalpostId: Int,
    ) {
        database.updateMotedeltakerBehandlerVarselJournalpostId(
            motedeltakerBehandlerVarselId = dialogmotedeltakerBehandlerVarsel.id,
            journalpostId = journalpostId
        )
    }

    private val journalforingVarselTypeList = listOf(
        MotedeltakerVarselType.AVLYST,
        MotedeltakerVarselType.INNKALT,
        MotedeltakerVarselType.NYTT_TID_STED,
    )
}
