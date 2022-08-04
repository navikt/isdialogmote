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
        tokenx: String? = null,
    ): List<Dialogmote> {
        return if (moteList.isEmpty()) {
            moteList
        } else {
            val virksomhetnummerListWhereNarmesteLederOfArbeidstaker =
                getVirksomhetnummerListWhereNarmesteLederOfArbeidstaker(
                    arbeidstakerPersonIdentNumber = arbeidstakerPersonIdentNumber,
                    callId = callId,
                    narmesteLederPersonIdentNumber = narmesteLederPersonIdentNumber,
                    tokenx = tokenx,
                )
            moteList.filter { virksomhetnummerListWhereNarmesteLederOfArbeidstaker.contains(it.arbeidsgiver.virksomhetsnummer) }
        }
    }

    suspend fun hasAccessToBrev(
        brev: NarmesteLederBrev,
        callId: String,
        tokenx: String? = null,
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
            tokenx = tokenx,
            narmesteLederPersonIdentNumber = narmesteLederPersonIdentNumber,
        )
        return virksomhetnummerListWhereNarmesteLederOfArbeidstaker.contains(dialogmoteDeltagerArbeidsgiver.virksomhetsnummer)
    }

    suspend fun getVirksomhetnummerListWhereNarmesteLederOfArbeidstaker(
        arbeidstakerPersonIdentNumber: PersonIdentNumber,
        callId: String,
        narmesteLederPersonIdentNumber: PersonIdentNumber,
        tokenx: String? = null,
    ): List<Virksomhetsnummer> {
        val aktiveAnsatteRelasjoner = narmesteLederClient.getAktiveAnsatte(
            narmesteLederIdent = narmesteLederPersonIdentNumber,
            tokenx = tokenx,
            callId = callId,
        )
        return aktiveAnsatteRelasjoner.filter { nlrelasjon ->
            nlrelasjon.arbeidstakerPersonIdentNumber == arbeidstakerPersonIdentNumber.value
        }.map { relasjon ->
            Virksomhetsnummer(relasjon.virksomhetsnummer)
        }.distinctBy { it.value }
    }
}
