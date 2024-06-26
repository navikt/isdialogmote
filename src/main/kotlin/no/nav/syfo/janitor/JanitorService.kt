package no.nav.syfo.janitor

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.dialogmote.DialogmoterelasjonService
import no.nav.syfo.dialogmote.DialogmotestatusService
import no.nav.syfo.dialogmote.database.getDialogmote
import no.nav.syfo.dialogmote.domain.DialogmoteStatus
import no.nav.syfo.dialogmote.domain.unfinished
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.janitor.kafka.*
import org.slf4j.LoggerFactory
import java.util.UUID

class JanitorService(
    private val database: DatabaseInterface,
    private val dialogmotestatusService: DialogmotestatusService,
    private val dialogmoterelasjonService: DialogmoterelasjonService,
    private val janitorEventStatusProducer: JanitorEventStatusProducer,
) {
    suspend fun handle(event: JanitorEventDTO) {
        log.info("Received janitor event ${event.action} with reference ${event.referenceUUID}")
        when (event.action) {
            JanitorAction.LUKK_DIALOGMOTE.name -> {
                val result = handleLukk(event)
                result.onFailure { log.error("Failed to handle janitor event", it) }
                sendEventStatus(event, result)
            }
            else -> log.trace("Irrelevant janitor event")
        }
    }

    private suspend fun handleLukk(event: JanitorEventDTO): Result<Unit> = runCatching {
        val personIdent = PersonIdent(event.personident)
        val dialogmoteUUID = UUID.fromString(event.referenceUUID)
        val pDialogmote = database.getDialogmote(moteUUID = dialogmoteUUID).firstOrNull() ?: throw RuntimeException(
            "Fant ikke dialogmøte"
        )
        val dialogmote =
            dialogmoterelasjonService.extendDialogmoteRelations(pDialogmote).takeIf { it.status.unfinished() }
                ?: throw RuntimeException("Dialogmøte ikke aktivt")
        if (dialogmote.arbeidstaker.personIdent != personIdent) {
            throw RuntimeException("Dialogmote gjelder ikke person")
        }

        log.info("Lukker dialogmøte med uuid $dialogmoteUUID")
        database.connection.use { connection ->
            dialogmotestatusService.updateMoteStatus(
                connection = connection,
                dialogmote = dialogmote,
                newDialogmoteStatus = DialogmoteStatus.LUKKET,
                opprettetAv = event.navident,
            )
            connection.commit()
        }
    }

    private fun sendEventStatus(event: JanitorEventDTO, result: Result<Unit>) {
        janitorEventStatusProducer.sendEventStatus(
            eventStatus = JanitorEventStatusDTO(
                eventUUID = event.eventUUID,
                status = if (result.isSuccess) JanitorEventStatus.OK else JanitorEventStatus.FAILED,
            )
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)
    }
}
