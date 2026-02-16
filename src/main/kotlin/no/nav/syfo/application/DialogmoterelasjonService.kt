package no.nav.syfo.application

import no.nav.syfo.domain.dialogmote.Dialogmote
import no.nav.syfo.domain.dialogmote.DialogmotedeltakerAnnen
import no.nav.syfo.domain.dialogmote.Referat
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.getAndreDeltakereForReferatID
import no.nav.syfo.infrastructure.database.getMoteDeltakerArbeidsgiver
import no.nav.syfo.infrastructure.database.getMoteDeltakerArbeidstaker
import no.nav.syfo.infrastructure.database.getReferatForMote
import no.nav.syfo.infrastructure.database.model.PDialogmote
import no.nav.syfo.infrastructure.database.model.toDialogmote
import no.nav.syfo.infrastructure.database.model.toDialogmoteDeltakerAnnen
import no.nav.syfo.infrastructure.database.model.toReferat
import java.util.*

class DialogmoterelasjonService(
    private val dialogmotedeltakerService: DialogmotedeltakerService,
    private val database: DatabaseInterface,
    private val moteRepository: IMoteRepository,
) {
    fun extendDialogmoteRelations(
        pDialogmote: PDialogmote,
    ): Dialogmote {
        val motedeltakerArbeidstaker = dialogmotedeltakerService.getDialogmoteDeltakerArbeidstaker(pDialogmote.id)
        val motedeltakerArbeidsgiver = dialogmotedeltakerService.getDialogmoteDeltakerArbeidsgiver(pDialogmote.id)
        val motedeltakerBehandler = dialogmotedeltakerService.getDialogmoteDeltakerBehandler(pDialogmote.id)
        val dialogmoteTidStedList = moteRepository.getTidSted(pDialogmote.id)
        val referatList = getReferatForMote(pDialogmote.uuid)
        return pDialogmote.toDialogmote(
            dialogmotedeltakerArbeidstaker = motedeltakerArbeidstaker,
            dialogmotedeltakerArbeidsgiver = motedeltakerArbeidsgiver,
            dialogmotedeltakerBehandler = motedeltakerBehandler,
            dialogmoteTidStedList = dialogmoteTidStedList,
            referatList = referatList,
        )
    }

    private fun getReferatForMote(
        moteUUID: UUID,
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
        referatId: Int,
    ): List<DialogmotedeltakerAnnen> {
        return database.getAndreDeltakereForReferatID(referatId).map { pMotedeltakerAnnen ->
            pMotedeltakerAnnen.toDialogmoteDeltakerAnnen()
        }
    }
}
