package no.nav.syfo.application

import no.nav.syfo.infrastructure.client.oppfolgingstilfelle.OppfolgingstilfelleClient
import no.nav.syfo.infrastructure.database.UnitOfWork
import no.nav.syfo.infrastructure.database.repository.MoteStatusEndretRepository
import no.nav.syfo.api.dto.DialogmoteStatusEndringDTO
import no.nav.syfo.infrastructure.database.updateMoteStatus
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.dialogmote.Dialogmote
import no.nav.syfo.domain.dialogmote.NewDialogmote
import java.time.LocalDate

class DialogmotestatusService(
    private val oppfolgingstilfelleClient: OppfolgingstilfelleClient,
    private val moteStatusEndretRepository: MoteStatusEndretRepository,
) {

    suspend fun fetchTilfelleStart(
        personIdent: PersonIdent,
        token: String? = null,
        callId: String? = null,
    ): LocalDate? {
        val tilfelle = if (token != null) {
            oppfolgingstilfelleClient.oppfolgingstilfellePerson(
                personIdent = personIdent,
                token = token,
                callId = callId,
            )
        } else {
            oppfolgingstilfelleClient.oppfolgingstilfelleSystem(
                personIdent = personIdent,
            )
        }
        return tilfelle?.start
    }

    fun updateMoteStatus(
        uow: UnitOfWork,
        dialogmote: Dialogmote,
        newDialogmoteStatus: Dialogmote.Status,
        opprettetAv: String,
        tilfelleStart: LocalDate?,
    ) {
        uow.updateMoteStatus(
            moteId = dialogmote.id,
            moteStatus = newDialogmoteStatus,
        )
        moteStatusEndretRepository.createMoteStatusEndring(
            uow = uow,
            moteId = dialogmote.id,
            opprettetAv = opprettetAv,
            isBehandlerMotedeltaker = dialogmote.behandler != null,
            status = newDialogmoteStatus,
            tilfelleStart = tilfelleStart,
        )
    }

    fun createMoteStatusEndring(
        uow: UnitOfWork,
        newDialogmote: NewDialogmote,
        dialogmoteId: Int,
        dialogmoteStatus: Dialogmote.Status,
        opprettetAv: String,
        tilfelleStart: LocalDate?,
    ) {
        moteStatusEndretRepository.createMoteStatusEndring(
            uow = uow,
            moteId = dialogmoteId,
            opprettetAv = opprettetAv,
            isBehandlerMotedeltaker = newDialogmote.behandler != null,
            status = dialogmoteStatus,
            tilfelleStart = tilfelleStart,
        )
    }

    fun getMoteStatusEndringer(personident: PersonIdent): List<DialogmoteStatusEndringDTO> =
        moteStatusEndretRepository.getMoteStatusEndringer(personident)
}
