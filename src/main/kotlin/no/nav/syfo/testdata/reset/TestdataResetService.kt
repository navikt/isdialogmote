package no.nav.syfo.testdata.reset

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.dialogmote.database.getDialogmoteList
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.testdata.reset.database.deleteMote
import org.slf4j.LoggerFactory

class TestdataResetService(private val database: DatabaseInterface) {
    fun resetTestdata(fnr: PersonIdent) {
        log.info("Nullstiller dialogmøter for ${fnr.value}")
        val dialogmoteList = database.getDialogmoteList(fnr)
        database.connection.use { connection ->
            dialogmoteList.forEach {
                connection.deleteMote(
                    commit = false,
                    moteId = it.id,
                )
            }
            connection.commit()
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(TestdataResetService::class.java)
    }
}
