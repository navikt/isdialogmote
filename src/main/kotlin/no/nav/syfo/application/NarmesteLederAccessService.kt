package no.nav.syfo.application

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Virksomhetsnummer
import no.nav.syfo.domain.dialogmote.Dialogmote
import no.nav.syfo.domain.dialogmote.NarmesteLederBrev
import no.nav.syfo.domain.dialogmote.removeBrevBeforeDate
import no.nav.syfo.infrastructure.client.narmesteleder.NarmesteLederClient
import no.nav.syfo.infrastructure.client.oppfolgingstilfelle.OppfolgingstilfelleClient
import no.nav.syfo.infrastructure.client.oppfolgingstilfelle.findOppfolgingstilfelleByDate
import no.nav.syfo.infrastructure.database.dialogmote.DialogmotedeltakerService
import no.nav.syfo.util.getGracePeriodStartDate
import java.time.LocalDate

class NarmesteLederAccessService(
    private val dialogmotedeltakerService: DialogmotedeltakerService,
    private val narmesteLederClient: NarmesteLederClient,
    private val oppfolgingstilfelleClient: OppfolgingstilfelleClient,
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

    private suspend fun getVirksomhetnummerListWhereNarmesteLederOfArbeidstaker(
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
            nlrelasjon.arbeidstakerPersonIdentNumber == arbeidstakerPersonIdent.value
        }.map { relasjon ->
            Virksomhetsnummer(relasjon.virksomhetsnummer)
        }.distinctBy { it.value }
    }

    private suspend fun getValidityPeriodStartDateForBrev(
        arbeidstakerPersonIdentNumber: PersonIdent,
        narmesteLederPersonIdentNumber: PersonIdent,
        tokenx: String,
        virksomhetsnummer: Virksomhetsnummer,
        callId: String,
    ): LocalDate {
        val oppfolgingstilfelleList = oppfolgingstilfelleClient.oppfolgingstilfelleTokenx(
            arbeidstakerPersonIdentNumber = arbeidstakerPersonIdentNumber,
            narmesteLederPersonIdentNumber = narmesteLederPersonIdentNumber,
            tokenx = tokenx,
            virksomhetsnummer = virksomhetsnummer,
            callId = callId
        )

        val gracePeriodStartDate = getGracePeriodStartDate()

        return oppfolgingstilfelleList?.findOppfolgingstilfelleByDate(gracePeriodStartDate)?.start
            ?: gracePeriodStartDate
    }

    suspend fun removeExpiredBrevInDialogmoter(
        moteList: List<Dialogmote>,
        narmesteLederPersonIdentNumber: PersonIdent,
        arbeidstakerPersonIdentNumber: PersonIdent,
        tokenx: String,
        callId: String,
    ): List<Dialogmote> {
        if (moteList.isEmpty()) {
            return emptyList()
        }

        val virksomhetsnummer = moteList[0].arbeidsgiver.virksomhetsnummer

        val startDate = getValidityPeriodStartDateForBrev(
            arbeidstakerPersonIdentNumber = arbeidstakerPersonIdentNumber,
            narmesteLederPersonIdentNumber = narmesteLederPersonIdentNumber,
            tokenx = tokenx,
            virksomhetsnummer = virksomhetsnummer,
            callId = callId
        )

        return moteList.removeBrevBeforeDate(startDate)
    }

    suspend fun isBrevExpired(
        brev: NarmesteLederBrev,
        callId: String,
        tokenx: String,
        narmesteLederPersonIdentNumber: PersonIdent,
    ): Boolean {
        val dialogmoteDeltagerArbeidsgiver = dialogmotedeltakerService.getDialogmoteDeltakerArbeidsgiverById(
            motedeltakerArbeidsgiverId = brev.motedeltakerArbeidsgiverId,
        )

        val arbeidstakerPersonIdent = dialogmotedeltakerService.getDialogmoteDeltakerArbeidstaker(
            moteId = dialogmoteDeltagerArbeidsgiver.moteId,
        ).personIdent

        val startDate = getValidityPeriodStartDateForBrev(
            arbeidstakerPersonIdentNumber = arbeidstakerPersonIdent,
            narmesteLederPersonIdentNumber = narmesteLederPersonIdentNumber,
            tokenx = tokenx,
            virksomhetsnummer = dialogmoteDeltagerArbeidsgiver.virksomhetsnummer,
            callId = callId
        )

        return brev.createdAt.toLocalDate().isBefore(startDate)
    }
}
