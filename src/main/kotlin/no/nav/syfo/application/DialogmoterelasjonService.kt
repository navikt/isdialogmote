package no.nav.syfo.application

import no.nav.syfo.domain.dialogmote.Dialogmote
import no.nav.syfo.infrastructure.database.model.PDialogmote
import no.nav.syfo.infrastructure.database.model.toDialogmote

class DialogmoterelasjonService(
    private val moteRepository: IMoteRepository,
) {
    fun extendDialogmoteRelations(
        pDialogmote: PDialogmote,
    ): Dialogmote {
        val motedeltakerArbeidstaker = moteRepository.getMotedeltakerArbeidstaker(pDialogmote.id)
        val motedeltakerArbeidsgiver = moteRepository.getMotedeltakerArbeidsgiver(pDialogmote.id)
        val motedeltakerBehandler = moteRepository.getMotedeltakerBehandler(pDialogmote.id)
        val dialogmoteTidStedList = moteRepository.getTidSted(pDialogmote.id)
        val referatList = moteRepository.getReferatForMote(pDialogmote.uuid)
        return pDialogmote.toDialogmote(
            dialogmotedeltakerArbeidstaker = motedeltakerArbeidstaker,
            dialogmotedeltakerArbeidsgiver = motedeltakerArbeidsgiver,
            dialogmotedeltakerBehandler = motedeltakerBehandler,
            dialogmoteTidStedList = dialogmoteTidStedList,
            referatList = referatList,
        )
    }
}
