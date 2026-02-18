package no.nav.syfo.application

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.dialogmote.Dialogmote
import no.nav.syfo.domain.dialogmote.DialogmoteSvarType
import no.nav.syfo.domain.dialogmote.DialogmotedeltakerArbeidsgiver
import no.nav.syfo.domain.dialogmote.DialogmotedeltakerArbeidsgiverVarsel
import no.nav.syfo.domain.dialogmote.DialogmotedeltakerArbeidstaker
import no.nav.syfo.domain.dialogmote.DialogmotedeltakerArbeidstakerVarsel
import no.nav.syfo.domain.dialogmote.DialogmotedeltakerBehandler
import no.nav.syfo.domain.dialogmote.erBrukeroppgaveVarsel
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.getMoteDeltakerArbeidsgiverById
import no.nav.syfo.infrastructure.database.getMotedeltakerArbeidsgiverVarsel
import no.nav.syfo.infrastructure.database.getMotedeltakerArbeidstakerById
import no.nav.syfo.infrastructure.database.getMotedeltakerArbeidstakerVarsel
import no.nav.syfo.infrastructure.database.model.toDialogmotedeltakerArbeidsgiverVarsel
import no.nav.syfo.infrastructure.database.model.toDialogmotedeltakerArbeidstakerVarsel
import no.nav.syfo.infrastructure.database.model.toMotedeltakerArbeidsgiverUsingDomainVarsler
import no.nav.syfo.infrastructure.database.model.toMotedeltakerArbeidstakerUsingDomainVarsler
import no.nav.syfo.infrastructure.database.updateMotedeltakerArbeidsgiverVarselLestDato
import no.nav.syfo.infrastructure.database.updateMotedeltakerArbeidsgiverVarselRespons
import no.nav.syfo.infrastructure.database.updateMotedeltakerArbeidstakerVarselLestDato
import no.nav.syfo.infrastructure.database.updateMotedeltakerArbeidstakerVarselRespons
import no.nav.syfo.infrastructure.database.updateReferatLestDatoArbeidsgiver
import no.nav.syfo.infrastructure.database.updateReferatLestDatoArbeidstaker
import java.util.*

class DialogmotedeltakerService(
    private val arbeidstakerVarselService: ArbeidstakerVarselService,
    private val database: DatabaseInterface,
    private val moteRepository: IMoteRepository,
) {
    fun getDialogmoteDeltakerArbeidstaker(
        moteId: Int,
    ): DialogmotedeltakerArbeidstaker {
        return moteRepository.getMotedeltakerArbeidstaker(moteId)
    }

    fun getDialogmoteDeltakerArbeidstakerById(
        moteDeltakerArbeidstakerId: Int,
    ): DialogmotedeltakerArbeidstaker {
        val pMotedeltakerArbeidstaker = database.getMotedeltakerArbeidstakerById(moteDeltakerArbeidstakerId)
        val motedeltakerArbeidstakerVarselList = getDialogmoteDeltakerArbeidstakerVarselList(
            pMotedeltakerArbeidstaker.id
        )
        return pMotedeltakerArbeidstaker.toMotedeltakerArbeidstakerUsingDomainVarsler(motedeltakerArbeidstakerVarselList)
    }

    private fun getDialogmoteDeltakerArbeidstakerVarselList(
        motedeltakerArbeidstakerId: Int,
    ): List<DialogmotedeltakerArbeidstakerVarsel> {
        return database.getMotedeltakerArbeidstakerVarsel(motedeltakerArbeidstakerId).map {
            it.toDialogmotedeltakerArbeidstakerVarsel()
        }
    }

    fun getDialogmoteDeltakerArbeidstakerVarselList(
        uuid: UUID,
    ): List<DialogmotedeltakerArbeidstakerVarsel> {
        return database.getMotedeltakerArbeidstakerVarsel(uuid).map {
            it.toDialogmotedeltakerArbeidstakerVarsel()
        }
    }

    fun getDialogmoteDeltakerArbeidsgiver(
        moteId: Int,
    ): DialogmotedeltakerArbeidsgiver {
        return moteRepository.getMotedeltakerArbeidsgiver(moteId)
    }

    fun getDialogmoteDeltakerArbeidsgiverById(
        motedeltakerArbeidsgiverId: Int,
    ): DialogmotedeltakerArbeidsgiver {
        val pMotedeltakerArbeidsgiver = database.getMoteDeltakerArbeidsgiverById(motedeltakerArbeidsgiverId)
        val motedeltakerArbeidsgiverVarselList = getDialogmoteDeltakerArbeidsgiverVarselList(
            pMotedeltakerArbeidsgiver.id
        )
        return pMotedeltakerArbeidsgiver.toMotedeltakerArbeidsgiverUsingDomainVarsler(motedeltakerArbeidsgiverVarselList)
    }

    private fun getDialogmoteDeltakerArbeidsgiverVarselList(
        motedeltakerArbeidsgiverId: Int,
    ): List<DialogmotedeltakerArbeidsgiverVarsel> {
        return database.getMotedeltakerArbeidsgiverVarsel(motedeltakerArbeidsgiverId).map {
            it.toDialogmotedeltakerArbeidsgiverVarsel()
        }
    }

    fun getDialogmoteDeltakerArbeidsgiverVarselList(
        uuid: UUID,
    ): List<DialogmotedeltakerArbeidsgiverVarsel> {
        return database.getMotedeltakerArbeidsgiverVarsel(uuid).map {
            it.toDialogmotedeltakerArbeidsgiverVarsel()
        }
    }

    fun getDialogmoteDeltakerBehandler(
        moteId: Int,
    ): DialogmotedeltakerBehandler? {
        return moteRepository.getMotedeltakerBehandler(moteId)
    }

    fun updateArbeidstakerBrevSettSomLest(
        personIdent: PersonIdent,
        brevUuid: UUID,
    ) {
        val isBrevReferat = moteRepository.getReferat(brevUuid) != null
        database.connection.use { connection ->
            if (isBrevReferat) {
                connection.updateReferatLestDatoArbeidstaker(referatUUID = brevUuid)
            } else {
                connection.updateMotedeltakerArbeidstakerVarselLestDato(
                    motedeltakerArbeidstakerVarselUuid = brevUuid
                )
            }

            arbeidstakerVarselService.lesVarsel(
                personIdent = personIdent,
                varselUuid = brevUuid,
            )
            connection.commit()
        }
    }

    fun slettBrukeroppgaverPaMote(
        dialogmote: Dialogmote,
    ) {
        val personIdent = dialogmote.arbeidstaker.personIdent
        dialogmote.arbeidstaker.varselList
            .filter { it.varselType.erBrukeroppgaveVarsel() }
            .forEach { brukeroppgaveVarsel ->
                arbeidstakerVarselService.lesVarsel(
                    personIdent = personIdent,
                    varselUuid = brukeroppgaveVarsel.uuid
                )
            }
    }

    fun updateArbeidsgiverBrevSettSomLest(brevUuid: UUID) {
        val isBrevReferat = moteRepository.getReferat(brevUuid) != null
        database.connection.use { connection ->
            if (isBrevReferat) {
                connection.updateReferatLestDatoArbeidsgiver(referatUUID = brevUuid)
            } else {
                connection.updateMotedeltakerArbeidsgiverVarselLestDato(brevUuid)
            }

            connection.commit()
        }
    }

    fun updateArbeidstakerBrevWithRespons(
        brevUuid: UUID,
        svarType: DialogmoteSvarType,
        svarTekst: String?,
    ): Boolean {
        val isBrevReferat = moteRepository.getReferat(brevUuid) != null
        if (isBrevReferat) {
            throw IllegalArgumentException("Cannot store response for referat")
        }
        return database.connection.use { connection ->
            val updateCount = connection.updateMotedeltakerArbeidstakerVarselRespons(
                motedeltakerArbeidstakerVarselUuid = brevUuid,
                svarType = svarType,
                svarTekst = sanitizeTextInput(svarTekst, 300),
            )
            connection.commit()
            updateCount > 0
        }
    }

    private fun sanitizeTextInput(
        tekst: String?,
        truncate: Int,
    ): String? {
        return tekst?.let {
            Regex("[^A-Za-z0-9 æøåÆØÅ%!()?.,;:/-]").replace(it.take(truncate), "_")
        }
    }

    fun updateArbeidsgiverBrevWithRespons(
        brevUuid: UUID,
        svarType: DialogmoteSvarType,
        svarTekst: String?,
    ): Boolean {
        val isBrevReferat = moteRepository.getReferat(brevUuid) != null
        if (isBrevReferat) {
            throw IllegalArgumentException("Cannot store response for referat")
        }

        return database.connection.use { connection ->
            val updateCount = connection.updateMotedeltakerArbeidsgiverVarselRespons(
                motedeltakerArbeidsgiverVarselUuid = brevUuid,
                svarType = svarType,
                svarTekst = sanitizeTextInput(svarTekst, 300),
            )
            connection.commit()
            updateCount > 0
        }
    }
}
