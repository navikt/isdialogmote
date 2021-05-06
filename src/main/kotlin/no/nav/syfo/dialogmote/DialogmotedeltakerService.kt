package no.nav.syfo.dialogmote

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.dialogmote.database.*
import no.nav.syfo.dialogmote.database.domain.toDialogmotedeltakerArbeidsgiver
import no.nav.syfo.dialogmote.database.domain.toDialogmotedeltakerArbeidstaker
import no.nav.syfo.dialogmote.domain.*
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.varsel.arbeidstaker.ArbeidstakerVarselService
import java.util.*

class DialogmotedeltakerService(
    private val arbeidstakerVarselService: ArbeidstakerVarselService,
    private val database: DatabaseInterface,
) {
    fun getDialogmoteDeltakerArbeidstaker(
        moteId: Int,
    ): DialogmotedeltakerArbeidstaker {
        val pMotedeltakerArbeidstaker = database.getMoteDeltakerArbeidstaker(moteId)
            .first()
        val motedeltakerArbeidstakerVarselList = getDialogmoteDeltakerArbeidstakerVarselList(
            pMotedeltakerArbeidstaker.id
        )
        return pMotedeltakerArbeidstaker.toDialogmotedeltakerArbeidstaker(motedeltakerArbeidstakerVarselList)
    }

    fun getDialogmoteDeltakerArbeidstakerVarselList(
        motedeltakerArbeidstakerId: Int,
    ): List<DialogmotedeltakerArbeidstakerVarsel> {
        return database.getMotedeltakerArbeidstakerVarsel(motedeltakerArbeidstakerId).map {
            it.toDialogmotedeltakerArbeidstaker()
        }
    }

    fun getDialogmoteDeltakerArbeidstaker(
        personIdentNumber: PersonIdentNumber,
    ): DialogmotedeltakerArbeidstaker {
        val pMotedeltakerArbeidstaker = database.getMoteDeltakerArbeidstaker(personIdentNumber)
            .first()
        val motedeltakerArbeidstakerVarselList = getDialogmoteDeltakerArbeidstakerVarselList(
            pMotedeltakerArbeidstaker.id
        )
        return pMotedeltakerArbeidstaker.toDialogmotedeltakerArbeidstaker(motedeltakerArbeidstakerVarselList)
    }

    fun getDialogmoteDeltakerArbeidsgiver(
        moteId: Int,
    ): DialogmotedeltakerArbeidsgiver {
        return database.getMoteDeltakerArbeidsgiver(moteId)
            .first()
            .toDialogmotedeltakerArbeidsgiver()
    }

    fun lesDialogmotedeltakerArbeidstakerVarsel(
        personIdentNumber: PersonIdentNumber,
        dialogmotedeltakerArbeidstakerUuid: UUID,
        dialogmotedeltakerArbeidstakerVarselUuid: UUID,
    ) {
        database.connection.use { connection ->
            connection.updateMotedeltakerArbeidstakerVarselLestDato(
                motedeltakerArbeidstakerVarselUuid = dialogmotedeltakerArbeidstakerVarselUuid
            )

            arbeidstakerVarselService.lesVarsel(
                personIdent = personIdentNumber,
                motedeltakerArbeidstakerUuid = dialogmotedeltakerArbeidstakerUuid,
                varselUuid = dialogmotedeltakerArbeidstakerVarselUuid,
            )
            connection.commit()
        }
    }
}
