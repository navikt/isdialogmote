package no.nav.syfo.infrastructure.database.dialogmote

import no.nav.syfo.infrastructure.client.oppfolgingstilfelle.OppfolgingstilfelleClient
import no.nav.syfo.infrastructure.database.dialogmote.database.repository.MoteStatusEndretRepository
import no.nav.syfo.api.dto.DialogmoteStatusEndringDTO
import no.nav.syfo.infrastructure.database.dialogmote.database.updateMoteStatus
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.dialogmote.Dialogmote
import no.nav.syfo.domain.dialogmote.DialogmoteStatus
import no.nav.syfo.domain.dialogmote.NewDialogmote
import java.sql.Connection

class DialogmotestatusService(
    private val oppfolgingstilfelleClient: OppfolgingstilfelleClient,
    private val moteStatusEndretRepository: MoteStatusEndretRepository,
) {

    suspend fun updateMoteStatus(
        connection: Connection,
        dialogmote: Dialogmote,
        newDialogmoteStatus: DialogmoteStatus,
        opprettetAv: String,
        callId: String? = null,
        token: String? = null,
    ) {
        connection.updateMoteStatus(
            commit = false,
            moteId = dialogmote.id,
            moteStatus = newDialogmoteStatus,
        )
        createMoteStatusEndring(
            callId = callId,
            connection = connection,
            dialogmote = dialogmote,
            dialogmoteStatus = newDialogmoteStatus,
            opprettetAv = opprettetAv,
            token = token,
        )
    }

    suspend fun createMoteStatusEndring(
        connection: Connection,
        newDialogmote: NewDialogmote,
        dialogmoteId: Int,
        dialogmoteStatus: DialogmoteStatus,
        opprettetAv: String,
        callId: String? = null,
        token: String? = null,
    ) = createMoteStatusEndring(
        connection = connection,
        dialogmoteId = dialogmoteId,
        arbeidstakerPersonIdent = newDialogmote.arbeidstaker.personIdent,
        isBehandlerMotedeltaker = newDialogmote.behandler != null,
        dialogmoteStatus = dialogmoteStatus,
        opprettetAv = opprettetAv,
        callId = callId,
        token = token,
    )

    suspend fun createMoteStatusEndring(
        connection: Connection,
        dialogmote: Dialogmote,
        dialogmoteStatus: DialogmoteStatus,
        opprettetAv: String,
        callId: String? = null,
        token: String? = null,
    ) = createMoteStatusEndring(
        connection = connection,
        dialogmoteId = dialogmote.id,
        arbeidstakerPersonIdent = dialogmote.arbeidstaker.personIdent,
        isBehandlerMotedeltaker = dialogmote.behandler != null,
        dialogmoteStatus = dialogmoteStatus,
        opprettetAv = opprettetAv,
        callId = callId,
        token = token,
    )

    fun getMoteStatusEndringer(personident: PersonIdent): List<DialogmoteStatusEndringDTO> =
        moteStatusEndretRepository.getMoteStatusEndringer(personident)

    private suspend fun createMoteStatusEndring(
        connection: Connection,
        dialogmoteId: Int,
        arbeidstakerPersonIdent: PersonIdent,
        isBehandlerMotedeltaker: Boolean,
        dialogmoteStatus: DialogmoteStatus,
        opprettetAv: String,
        callId: String? = null,
        token: String? = null,
    ) {
        val tilfelle = if (token != null) {
            oppfolgingstilfelleClient.oppfolgingstilfellePerson(
                personIdent = arbeidstakerPersonIdent,
                token = token,
                callId = callId,
            )
        } else {
            oppfolgingstilfelleClient.oppfolgingstilfelleSystem(
                personIdent = arbeidstakerPersonIdent,
            )
        }

        moteStatusEndretRepository.createMoteStatusEndring(
            connection = connection,
            commit = false,
            moteId = dialogmoteId,
            opprettetAv = opprettetAv,
            isBehandlerMotedeltaker = isBehandlerMotedeltaker,
            status = dialogmoteStatus,
            tilfelleStart = tilfelle?.start,
        )
    }
}
