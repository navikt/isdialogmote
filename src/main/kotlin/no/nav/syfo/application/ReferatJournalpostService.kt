package no.nav.syfo.application

import java.time.LocalDateTime
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.dialogmote.database.domain.toDialogmoteDeltakerAnnen
import no.nav.syfo.infrastructure.database.dialogmote.database.domain.toDialogmotedeltakerBehandler
import no.nav.syfo.infrastructure.database.dialogmote.database.domain.toReferat
import no.nav.syfo.domain.dialogmote.DialogmotedeltakerBehandler
import no.nav.syfo.domain.dialogmote.Referat
import no.nav.syfo.domain.dialogmote.ReferatForJournalpostDistribusjon
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Virksomhetsnummer
import no.nav.syfo.infrastructure.database.dialogmote.database.getAndreDeltakereForReferatID
import no.nav.syfo.infrastructure.database.dialogmote.database.getFerdigstilteReferatWithoutJournalpostArbeidsgiverList
import no.nav.syfo.infrastructure.database.dialogmote.database.getFerdigstilteReferatWithoutJournalpostArbeidstakerList
import no.nav.syfo.infrastructure.database.dialogmote.database.getFerdigstilteReferatWithoutJournalpostBehandlerList
import no.nav.syfo.infrastructure.database.dialogmote.database.getMoteDeltakerArbeidsgiver
import no.nav.syfo.infrastructure.database.dialogmote.database.getMoteDeltakerArbeidstaker
import no.nav.syfo.infrastructure.database.dialogmote.database.getMoteDeltakerBehandler
import no.nav.syfo.infrastructure.database.dialogmote.database.getReferatForFysiskBrevUtsending
import no.nav.syfo.infrastructure.database.dialogmote.database.getTidSted
import no.nav.syfo.infrastructure.database.dialogmote.database.updateReferatBrevBestilt
import no.nav.syfo.infrastructure.database.dialogmote.database.updateReferatJournalpostIdArbeidsgiver
import no.nav.syfo.infrastructure.database.dialogmote.database.updateReferatJournalpostIdArbeidstaker
import no.nav.syfo.infrastructure.database.dialogmote.database.updateReferatJournalpostIdBehandler

class ReferatJournalpostService(
    private val database: DatabaseInterface,
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
        return database.getFerdigstilteReferatWithoutJournalpostArbeidsgiverList()
            .map { (virksomhetsnummer, pReferat) ->
                val andreDeltakere = database.getAndreDeltakereForReferatID(pReferat.id)
                    .map {
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

    fun getDialogmoteReferatForJournalpostDistribusjonList(): List<ReferatForJournalpostDistribusjon> {
        return database.getReferatForFysiskBrevUtsending()
            .map { pReferat ->
                val motedeltakerArbeidstaker = database.getMoteDeltakerArbeidstaker(pReferat.moteId)
                ReferatForJournalpostDistribusjon(
                    pReferat.id,
                    motedeltakerArbeidstaker.personIdent,
                    pReferat.journalpostIdArbeidstaker,
                    getMotetidspunkt(pReferat.moteId)
                )
            }
    }

    fun updateBrevBestilt(
        referatId: Int,
    ) {
        database.updateReferatBrevBestilt(
            referatId = referatId,
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
