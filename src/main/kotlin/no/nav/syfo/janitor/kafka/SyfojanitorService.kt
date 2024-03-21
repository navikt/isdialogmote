package no.nav.syfo.janitor.kafka

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.dialogmote.DialogmotestatusService
import no.nav.syfo.dialogmote.database.getDialogmote
import no.nav.syfo.dialogmote.domain.DialogmoteStatus
import no.nav.syfo.testdata.reset.TestdataResetService
import org.slf4j.LoggerFactory
import java.util.UUID

class SyfojanitorService(private val database: DatabaseInterface, private val dialogmotestatusService: DialogmotestatusService,) {
    fun setDialogmoteStatusLukket(uuid: UUID) {
        log.info("Lukker dialogmøte med UUID $uuid")
        val dialogmote = database.getDialogmote(uuid)
        database.connection.use { connection ->
            dialogmotestatusService.updateMoteStatus(
                connection = connection,
                dialogmote = dialogmote,
                newDialogmoteStatus = DialogmoteStatus.LUKKET,
                opprettetAv = "system",
            )

            connection.commit()
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(TestdataResetService::class.java)
    }
}
