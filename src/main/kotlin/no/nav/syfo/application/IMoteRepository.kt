package no.nav.syfo.application

import no.nav.syfo.domain.EnhetNr
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.dialogmote.DialogmoteTidSted
import no.nav.syfo.domain.dialogmote.DialogmotedeltakerArbeidsgiver
import no.nav.syfo.domain.dialogmote.DialogmotedeltakerArbeidstaker
import no.nav.syfo.domain.dialogmote.DialogmotedeltakerBehandler
import no.nav.syfo.infrastructure.database.model.PDialogmote
import java.util.UUID

interface IMoteRepository {
    fun getMote(moteUUID: UUID): PDialogmote

    fun getMoterFor(personIdent: PersonIdent): List<PDialogmote>

    fun getDialogmoteList(enhetNr: EnhetNr): List<PDialogmote>

    fun getUnfinishedMoterForEnhet(enhetNr: EnhetNr): List<PDialogmote>

    fun getUnfinishedMoterForVeileder(veilederIdent: String): List<PDialogmote>

    fun getMotedeltakerArbeidstaker(moteId: Int): DialogmotedeltakerArbeidstaker

    fun getMotedeltakerArbeidsgiver(moteId: Int): DialogmotedeltakerArbeidsgiver

    fun getMotedeltakerBehandler(moteId: Int): DialogmotedeltakerBehandler?

    fun getTidSted(moteId: Int): List<DialogmoteTidSted>
}
