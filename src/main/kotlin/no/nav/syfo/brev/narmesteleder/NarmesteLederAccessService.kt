package no.nav.syfo.brev.narmesteleder

import no.nav.syfo.client.narmesteleder.NarmesteLederClient
import no.nav.syfo.dialogmote.DialogmotedeltakerService
import no.nav.syfo.dialogmote.domain.Dialogmote
import no.nav.syfo.dialogmote.domain.NarmesteLederBrev
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.Virksomhetsnummer

class NarmesteLederAccessService(
    private val dialogmotedeltakerService: DialogmotedeltakerService,
    private val narmesteLederClient: NarmesteLederClient,
) {
    suspend fun filterMoterByNarmesteLederAccess(
        arbeidstakerPersonIdentNumber: PersonIdentNumber,
        callId: String,
        moteList: List<Dialogmote>,
        narmesteLederPersonIdentNumber: PersonIdentNumber,
    ): List<Dialogmote> {
        val virksomhetnummerListWhereNarmesteLederOfArbeidstaker = getVirksomhetnummerListWhereNarmesteLederOfArbeidstaker(
            arbeidstakerPersonIdentNumber = arbeidstakerPersonIdentNumber,
            callId = callId,
            narmesteLederPersonIdentNumber = narmesteLederPersonIdentNumber,
        )
        return moteList.filter { virksomhetnummerListWhereNarmesteLederOfArbeidstaker.contains(it.arbeidsgiver.virksomhetsnummer) }
    }

    suspend fun hasAccessToBrev(
        brev: NarmesteLederBrev,
        callId: String,
        narmesteLederPersonIdentNumber: PersonIdentNumber,
    ): Boolean {
        val dialogmoteDeltagerArbeidsgiver = dialogmotedeltakerService.getDialogmoteDeltakerArbeidsgiverById(
            motedeltakerArbeidsgiverId = brev.motedeltakerArbeidsgiverId,
        )

        val arbeidstakerPersonIdent = dialogmotedeltakerService.getDialogmoteDeltakerArbeidstaker(
            moteId = dialogmoteDeltagerArbeidsgiver.moteId,
        ).personIdent

        val virksomhetnummerListWhereNarmesteLederOfArbeidstaker = getVirksomhetnummerListWhereNarmesteLederOfArbeidstaker(
            arbeidstakerPersonIdentNumber = arbeidstakerPersonIdent,
            callId = callId,
            narmesteLederPersonIdentNumber = narmesteLederPersonIdentNumber,
        )
        return virksomhetnummerListWhereNarmesteLederOfArbeidstaker.contains(dialogmoteDeltagerArbeidsgiver.virksomhetsnummer)
    }

    suspend fun getVirksomhetnummerListWhereNarmesteLederOfArbeidstaker(
        arbeidstakerPersonIdentNumber: PersonIdentNumber,
        callId: String,
        narmesteLederPersonIdentNumber: PersonIdentNumber,
    ): List<Virksomhetsnummer> {
        val aktiveAnsatteRelasjoner = narmesteLederClient.getAktiveAnsatte(narmesteLederPersonIdentNumber, callId)
        return aktiveAnsatteRelasjoner.filter { nlrelasjon ->
            nlrelasjon.fnr == arbeidstakerPersonIdentNumber.value
        }.map { relasjon ->
            Virksomhetsnummer(relasjon.orgnummer)
        }.distinctBy { it.value }
    }
}
