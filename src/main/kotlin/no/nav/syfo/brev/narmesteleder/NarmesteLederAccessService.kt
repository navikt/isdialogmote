package no.nav.syfo.brev.narmesteleder

import no.nav.syfo.client.narmesteleder.NarmesteLederClient
import no.nav.syfo.dialogmote.DialogmotedeltakerService
import no.nav.syfo.dialogmote.domain.Dialogmote
import no.nav.syfo.dialogmote.domain.NarmesteLederBrev
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Virksomhetsnummer

class NarmesteLederAccessService(
    private val dialogmotedeltakerService: DialogmotedeltakerService,
    private val narmesteLederClient: NarmesteLederClient,
) {
    suspend fun filterMoterByNarmesteLederAccess(
        arbeidstakerPersonIdent: PersonIdent,
        callId: String,
        moteList: List<Dialogmote>,
        narmesteLederPersonIdent: PersonIdent,
        tokenx: String,
    ): List<Dialogmote> {
        return if (moteList.isEmpty()) {
            moteList
        } else {
            val virksomhetnummerListWhereNarmesteLederOfArbeidstaker =
                getVirksomhetnummerListWhereNarmesteLederOfArbeidstaker(
                    arbeidstakerPersonIdent = arbeidstakerPersonIdent,
                    callId = callId,
                    narmesteLederPersonIdent = narmesteLederPersonIdent,
                    tokenx = tokenx,
                )
            moteList.filter { virksomhetnummerListWhereNarmesteLederOfArbeidstaker.contains(it.arbeidsgiver.virksomhetsnummer) }
        }
    }

    suspend fun hasAccessToBrev(
        brev: NarmesteLederBrev,
        callId: String,
        tokenx: String,
        narmesteLederPersonIdent: PersonIdent,
    ): Boolean {
        val dialogmoteDeltagerArbeidsgiver = dialogmotedeltakerService.getDialogmoteDeltakerArbeidsgiverById(
            motedeltakerArbeidsgiverId = brev.motedeltakerArbeidsgiverId,
        )

        val arbeidstakerPersonIdent = dialogmotedeltakerService.getDialogmoteDeltakerArbeidstaker(
            moteId = dialogmoteDeltagerArbeidsgiver.moteId,
        ).personIdent

        val virksomhetnummerListWhereNarmesteLederOfArbeidstaker = getVirksomhetnummerListWhereNarmesteLederOfArbeidstaker(
            arbeidstakerPersonIdent = arbeidstakerPersonIdent,
            callId = callId,
            tokenx = tokenx,
            narmesteLederPersonIdent = narmesteLederPersonIdent,
        )
        return virksomhetnummerListWhereNarmesteLederOfArbeidstaker.contains(dialogmoteDeltagerArbeidsgiver.virksomhetsnummer)
    }

    suspend fun getVirksomhetnummerListWhereNarmesteLederOfArbeidstaker(
        arbeidstakerPersonIdent: PersonIdent,
        callId: String,
        narmesteLederPersonIdent: PersonIdent,
        tokenx: String,
    ): List<Virksomhetsnummer> {
        val aktiveAnsatteRelasjoner = narmesteLederClient.getAktiveAnsatte(
            narmesteLederIdent = narmesteLederPersonIdent,
            tokenx = tokenx,
            callId = callId,
        )
        return aktiveAnsatteRelasjoner.filter { nlrelasjon ->
            nlrelasjon.arbeidstakerPersonIdent == arbeidstakerPersonIdent.value
        }.map { relasjon ->
            Virksomhetsnummer(relasjon.virksomhetsnummer)
        }.distinctBy { it.value }
    }
}
