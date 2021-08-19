package no.nav.syfo.dialogmote

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.brev.arbeidstaker.ArbeidstakerVarselService
import no.nav.syfo.dialogmote.database.*
import no.nav.syfo.dialogmote.database.domain.toDialogmotedeltakerArbeidsgiver
import no.nav.syfo.dialogmote.database.domain.toDialogmotedeltakerArbeidstaker
import no.nav.syfo.dialogmote.domain.DialogmotedeltakerArbeidsgiver
import no.nav.syfo.dialogmote.domain.DialogmotedeltakerArbeidsgiverVarsel
import no.nav.syfo.dialogmote.domain.DialogmotedeltakerArbeidstaker
import no.nav.syfo.dialogmote.domain.DialogmotedeltakerArbeidstakerVarsel
import no.nav.syfo.domain.PersonIdentNumber
import java.util.UUID

class DialogmotedeltakerService(
    private val arbeidstakerVarselService: ArbeidstakerVarselService,
    private val database: DatabaseInterface,
) {
    fun getDialogmoteDeltakerArbeidstaker(
        moteId: Int,
    ): DialogmotedeltakerArbeidstaker {
        val pMotedeltakerArbeidstaker = database.getMoteDeltakerArbeidstaker(moteId)
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

    fun getDialogmoteDeltakerArbeidstakerVarselList(
        uuid: UUID,
    ): List<DialogmotedeltakerArbeidstakerVarsel> {
        return database.getMotedeltakerArbeidstakerVarsel(uuid).map {
            it.toDialogmotedeltakerArbeidstaker()
        }
    }

    fun getDialogmoteDeltakerArbeidsgiverVarselList(
        motedeltakerArbeidsgiverId: Int,
    ): List<DialogmotedeltakerArbeidsgiverVarsel> {
        return database.getMotedeltakerArbeidsgiverVarsel(motedeltakerArbeidsgiverId).map {
            it.toDialogmotedeltakerArbeidsgiver()
        }
    }

    fun getDialogmoteDeltakerArbeidsgiverVarselList(
        uuid: UUID,
    ): List<DialogmotedeltakerArbeidsgiverVarsel> {
        return database.getMotedeltakerArbeidsgiverVarsel(uuid).map {
            it.toDialogmotedeltakerArbeidsgiver()
        }
    }

    fun getDialogmoteDeltakerArbeidstakerById(
        moteDeltakerArbeidstakerId: Int,
    ): DialogmotedeltakerArbeidstaker {
        val pMotedeltakerArbeidstaker = database.getMotedeltakerArbeidstakerById(moteDeltakerArbeidstakerId)
        val motedeltakerArbeidstakerVarselList = getDialogmoteDeltakerArbeidstakerVarselList(
            pMotedeltakerArbeidstaker.id
        )
        return pMotedeltakerArbeidstaker.toDialogmotedeltakerArbeidstaker(motedeltakerArbeidstakerVarselList)
    }

    fun getDialogmoteDeltakerArbeidsgiver(
        moteId: Int,
    ): DialogmotedeltakerArbeidsgiver {
        val pMotedeltakerArbeidsgiver = database.getMoteDeltakerArbeidsgiver(moteId)
        val motedeltakerArbeidsgiverVarselList = getDialogmoteDeltakerArbeidsgiverVarselList(
            pMotedeltakerArbeidsgiver.id
        )
        return pMotedeltakerArbeidsgiver.toDialogmotedeltakerArbeidsgiver(motedeltakerArbeidsgiverVarselList)
    }

    fun getDialogmoteDeltakerArbeidsgiverById(
        motedeltakerArbeidsgiverId: Int,
    ): DialogmotedeltakerArbeidsgiver {
        val pMotedeltakerArbeidsgiver = database.getMoteDeltakerArbeidsgiverById(motedeltakerArbeidsgiverId)
        val motedeltakerArbeidsgiverVarselList = getDialogmoteDeltakerArbeidsgiverVarselList(
            pMotedeltakerArbeidsgiver.id
        )
        return pMotedeltakerArbeidsgiver.toDialogmotedeltakerArbeidsgiver(motedeltakerArbeidsgiverVarselList)
    }

    fun updateArbeidstakerBrevSettSomLest(
        personIdentNumber: PersonIdentNumber,
        dialogmotedeltakerArbeidstakerUuid: UUID,
        brevUuid: UUID,
    ) {
        val brevIsReferat = database.getReferat(brevUuid).isNotEmpty()

        database.connection.use { connection ->
            if (brevIsReferat) {
                connection.updateReferatLestDatoArbeidstaker(
                    referatUUID = brevUuid
                )
            } else {
                connection.updateMotedeltakerArbeidstakerVarselLestDato(
                    motedeltakerArbeidstakerVarselUuid = brevUuid
                )
            }

            arbeidstakerVarselService.lesVarsel(
                personIdent = personIdentNumber,
                motedeltakerArbeidstakerUuid = dialogmotedeltakerArbeidstakerUuid,
                varselUuid = brevUuid,
            )
            connection.commit()
        }
    }

    fun updateArbeidsgiverBrevSettSomLest(brevUuid: UUID) {
        val brevIsReferat = database.getReferat(brevUuid).isNotEmpty()

        database.connection.use { connection ->
            if (brevIsReferat) {
                connection.updateReferatLestDatoArbeidsgiver(referatUUID = brevUuid)
            } else {
                connection.updateMotedeltakerArbeidsgiverVarselLestDato(brevUuid)
            }

            connection.commit()
        }
    }
}
