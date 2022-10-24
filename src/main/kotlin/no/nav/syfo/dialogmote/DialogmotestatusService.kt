package no.nav.syfo.dialogmote

import no.nav.syfo.client.oppfolgingstilfelle.OppfolgingstilfelleClient
import no.nav.syfo.dialogmote.database.createMoteStatusEndring
import no.nav.syfo.dialogmote.database.updateMoteStatus
import no.nav.syfo.dialogmote.domain.*
import no.nav.syfo.domain.PersonIdent
import java.sql.Connection

class DialogmotestatusService(
    private val oppfolgingstilfelleClient: OppfolgingstilfelleClient,
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
        val tilfelle = oppfolgingstilfelleClient.oppfolgingstilfelle(
            callId = callId,
            personIdent = arbeidstakerPersonIdent,
            token = token,
        )

        connection.createMoteStatusEndring(
            commit = false,
            moteId = dialogmoteId,
            opprettetAv = opprettetAv,
            isBehandlerMotedeltaker = isBehandlerMotedeltaker,
            status = dialogmoteStatus,
            tilfelleStart = tilfelle?.start,
        )
    }
}
