package no.nav.syfo.application

import no.nav.syfo.domain.EnhetNr
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Virksomhetsnummer
import no.nav.syfo.domain.dialogmote.Dialogmote
import no.nav.syfo.domain.dialogmote.DialogmoteTidSted
import no.nav.syfo.domain.dialogmote.DialogmotedeltakerArbeidsgiver
import no.nav.syfo.domain.dialogmote.DialogmotedeltakerArbeidstaker
import no.nav.syfo.domain.dialogmote.DialogmotedeltakerBehandler
import no.nav.syfo.domain.dialogmote.Referat
import java.util.UUID

interface IMoteRepository {
    fun getMote(moteUUID: UUID): Dialogmote
    fun getMoterFor(personIdent: PersonIdent): List<Dialogmote>
    fun getDialogmoteList(enhetNr: EnhetNr): List<Dialogmote>
    fun getUnfinishedMoterForEnhet(enhetNr: EnhetNr): List<Dialogmote>
    fun getUnfinishedMoterForVeileder(veilederIdent: String): List<Dialogmote>
    fun getMotedeltakerArbeidstaker(moteId: Int): DialogmotedeltakerArbeidstaker
    fun getMotedeltakerArbeidsgiver(moteId: Int): DialogmotedeltakerArbeidsgiver
    fun getMotedeltakerBehandler(moteId: Int): DialogmotedeltakerBehandler?
    fun getTidSted(moteId: Int): List<DialogmoteTidSted>
    fun getReferatForMote(moteUUID: UUID): List<Referat>
    fun getReferat(referatUUID: UUID): Referat?
    fun getFerdigstilteReferatWithoutJournalpostArbeidstakerList(): List<Pair<PersonIdent, Referat>>
    fun getFerdigstilteReferatWithoutJournalpostArbeidsgiverList(): List<Triple<Virksomhetsnummer, PersonIdent, Referat>>
}
