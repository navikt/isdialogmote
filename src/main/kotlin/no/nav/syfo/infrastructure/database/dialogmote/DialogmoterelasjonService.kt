package no.nav.syfo.infrastructure.database.dialogmote

import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.domain.dialogmote.Dialogmote
import no.nav.syfo.domain.dialogmote.DialogmoteTidSted
import no.nav.syfo.domain.dialogmote.DialogmotedeltakerAnnen
import no.nav.syfo.domain.dialogmote.Referat
import no.nav.syfo.infrastructure.database.dialogmote.database.domain.PDialogmote
import no.nav.syfo.infrastructure.database.dialogmote.database.domain.toDialogmote
import no.nav.syfo.infrastructure.database.dialogmote.database.domain.toDialogmoteDeltakerAnnen
import no.nav.syfo.infrastructure.database.dialogmote.database.domain.toDialogmoteTidSted
import no.nav.syfo.infrastructure.database.dialogmote.database.domain.toReferat
import no.nav.syfo.infrastructure.database.dialogmote.database.getAndreDeltakereForReferatID
import no.nav.syfo.infrastructure.database.dialogmote.database.getMoteDeltakerArbeidsgiver
import no.nav.syfo.infrastructure.database.dialogmote.database.getMoteDeltakerArbeidstaker
import no.nav.syfo.infrastructure.database.dialogmote.database.getReferatForMote
import no.nav.syfo.infrastructure.database.dialogmote.database.getTidSted
import java.util.UUID

class DialogmoterelasjonService(
    private val dialogmotedeltakerService: DialogmotedeltakerService,
    private val database: DatabaseInterface,
) {
    fun extendDialogmoteRelations(
        pDialogmote: PDialogmote,
    ): Dialogmote {
        val motedeltakerArbeidstaker = dialogmotedeltakerService.getDialogmoteDeltakerArbeidstaker(pDialogmote.id)
        val motedeltakerArbeidsgiver = dialogmotedeltakerService.getDialogmoteDeltakerArbeidsgiver(pDialogmote.id)
        val motedeltakerBehandler = dialogmotedeltakerService.getDialogmoteDeltakerBehandler(pDialogmote.id)
        val dialogmoteTidStedList = getDialogmoteTidStedList(pDialogmote.id)
        val referatList = getReferatForMote(pDialogmote.uuid)
        return pDialogmote.toDialogmote(
            dialogmotedeltakerArbeidstaker = motedeltakerArbeidstaker,
            dialogmotedeltakerArbeidsgiver = motedeltakerArbeidsgiver,
            dialogmotedeltakerBehandler = motedeltakerBehandler,
            dialogmoteTidStedList = dialogmoteTidStedList,
            referatList = referatList,
        )
    }

    private fun getDialogmoteTidStedList(
        moteId: Int
    ): List<DialogmoteTidSted> {
        return database.getTidSted(moteId).map {
            it.toDialogmoteTidSted()
        }
    }

    private fun getReferatForMote(
        moteUUID: UUID
    ): List<Referat> {
        return database.getReferatForMote(moteUUID).map { pReferat ->
            val andreDeltakere = getAndreDeltakere(pReferat.id)
            val motedeltakerArbeidstakerId = database.getMoteDeltakerArbeidstaker(pReferat.moteId).id
            val motedeltakerArbeidsgiverId = database.getMoteDeltakerArbeidsgiver(pReferat.moteId).id

            pReferat.toReferat(
                andreDeltakere = andreDeltakere,
                motedeltakerArbeidstakerId = motedeltakerArbeidstakerId,
                motedeltakerArbeidsgiverId = motedeltakerArbeidsgiverId
            )
        }
    }

    fun getAndreDeltakere(
        referatId: Int
    ): List<DialogmotedeltakerAnnen> {
        return database.getAndreDeltakereForReferatID(referatId).map { pMotedeltakerAnnen ->
            pMotedeltakerAnnen.toDialogmoteDeltakerAnnen()
        }
    }
}
