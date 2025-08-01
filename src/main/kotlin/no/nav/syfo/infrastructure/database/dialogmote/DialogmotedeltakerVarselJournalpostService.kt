package no.nav.syfo.infrastructure.database.dialogmote

import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.dialogmote.database.domain.toDialogmotedeltakerArbeidsgiver
import no.nav.syfo.infrastructure.database.dialogmote.database.domain.toDialogmotedeltakerArbeidstaker
import no.nav.syfo.infrastructure.database.dialogmote.database.domain.toDialogmotedeltakerBehandler
import no.nav.syfo.infrastructure.database.dialogmote.database.domain.toDialogmotedeltakerBehandlerVarsel
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Virksomhetsnummer
import no.nav.syfo.domain.dialogmote.DialogmotedeltakerArbeidsgiverVarsel
import no.nav.syfo.domain.dialogmote.DialogmotedeltakerArbeidstakerVarsel
import no.nav.syfo.domain.dialogmote.DialogmotedeltakerBehandler
import no.nav.syfo.domain.dialogmote.DialogmotedeltakerBehandlerVarsel
import no.nav.syfo.domain.dialogmote.MotedeltakerVarselType
import no.nav.syfo.infrastructure.database.dialogmote.database.getMoteDeltakerArbeidsgiverById
import no.nav.syfo.infrastructure.database.dialogmote.database.getMoteDeltakerArbeidstaker
import no.nav.syfo.infrastructure.database.dialogmote.database.getMotedeltakerArbeidsgiverVarselWithoutJournalpost
import no.nav.syfo.infrastructure.database.dialogmote.database.getMotedeltakerArbeidstakerById
import no.nav.syfo.infrastructure.database.dialogmote.database.getMotedeltakerArbeidstakerVarselForFysiskBrevUtsending
import no.nav.syfo.infrastructure.database.dialogmote.database.getMotedeltakerArbeidstakerVarselWithoutJournalpost
import no.nav.syfo.infrastructure.database.dialogmote.database.getMotedeltakerBehandlerById
import no.nav.syfo.infrastructure.database.dialogmote.database.getMotedeltakerBehandlerVarselWithoutJournalpost
import no.nav.syfo.infrastructure.database.dialogmote.database.getTidSted
import no.nav.syfo.infrastructure.database.dialogmote.database.updateMotedeltakerArbeidsgiverVarselJournalpostId
import no.nav.syfo.infrastructure.database.dialogmote.database.updateMotedeltakerArbeidstakerBrevBestilt
import no.nav.syfo.infrastructure.database.dialogmote.database.updateMotedeltakerArbeidstakerVarselJournalpostId
import no.nav.syfo.infrastructure.database.dialogmote.database.updateMotedeltakerBehandlerVarselJournalpostId
import java.time.LocalDateTime

class DialogmotedeltakerVarselJournalpostService(
    private val database: DatabaseInterface,
) {
    fun getDialogmotedeltakerArbeidstakerVarselForJournalforingList(): List<Pair<PersonIdent, DialogmotedeltakerArbeidstakerVarsel>> {
        return getDialogmotedeltakerArbeidstakerVarselWithoutJournalpostList().filter { (_, arbeidstakerVarsel) ->
            journalforingVarselTypeList.contains(arbeidstakerVarsel.varselType)
        }
    }

    private fun getDialogmotedeltakerArbeidstakerVarselWithoutJournalpostList(): List<Pair<PersonIdent, DialogmotedeltakerArbeidstakerVarsel>> {
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

    fun getDialogmotedeltakerArbeidsgiverVarselForJournalforingList(): List<Triple<Virksomhetsnummer, PersonIdent, DialogmotedeltakerArbeidsgiverVarsel>> {
        return getDialogmotedeltakerArbeidsgiverVarselWithoutJournalpostList().filter { (_, _, arbeidsgiverVarsel) ->
            journalforingVarselTypeList.contains(arbeidsgiverVarsel.varselType)
        }
    }

    private fun getDialogmotedeltakerArbeidsgiverVarselWithoutJournalpostList(): List<Triple<Virksomhetsnummer, PersonIdent, DialogmotedeltakerArbeidsgiverVarsel>> {
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

    fun getDialogmotedeltakerBehandlerVarselForJournalforingList(): List<Triple<PersonIdent, DialogmotedeltakerBehandler, DialogmotedeltakerBehandlerVarsel>> {
        return getDialogmotedeltakerBehandlerVarselWithoutJournalpostList().filter { (_, _, behandlerVarsel) ->
            journalforingVarselTypeList.contains(behandlerVarsel.varselType)
        }
    }

    private fun getDialogmotedeltakerBehandlerVarselWithoutJournalpostList(): List<Triple<PersonIdent, DialogmotedeltakerBehandler, DialogmotedeltakerBehandlerVarsel>> {
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

    fun getDialogmotedeltakerArbeidstakerVarselForJournalpostDistribusjonList(): List<Triple<PersonIdent, DialogmotedeltakerArbeidstakerVarsel, LocalDateTime>> {
        return database.getMotedeltakerArbeidstakerVarselForFysiskBrevUtsending()
            .map { it.toDialogmotedeltakerArbeidstaker() }
            .filter { journalforingVarselTypeList.contains(it.varselType) }
            .map { motedeltakerArbeidstakerVarsel ->
                val motedeltakerArbeidstaker =
                    database.getMotedeltakerArbeidstakerById(motedeltakerArbeidstakerVarsel.motedeltakerArbeidstakerId)
                val tidspunkt =
                    database.getTidSted(motedeltakerArbeidstaker.moteId)
                        .last()
                        .tid
                Triple(
                    motedeltakerArbeidstaker.personIdent,
                    motedeltakerArbeidstakerVarsel,
                    tidspunkt
                )
            }
    }

    fun updateBrevBestilt(
        dialogmotedeltakerArbeidstakerVarsel: DialogmotedeltakerArbeidstakerVarsel,
    ) {
        database.updateMotedeltakerArbeidstakerBrevBestilt(
            motedeltakerArbeidstakerVarselId = dialogmotedeltakerArbeidstakerVarsel.id,
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
