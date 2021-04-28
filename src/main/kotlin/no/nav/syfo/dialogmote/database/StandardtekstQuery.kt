package no.nav.syfo.dialogmote.database

import no.nav.syfo.application.database.DatabaseInterface
import java.sql.*
import java.time.Instant
import no.nav.syfo.application.database.toList

const val queryGetStandardtekst =
    """
        SELECT *
        FROM STANDARDTEKST S1
        WHERE S1.nokkel = ?
              AND S1.gyldig_fra = 
              (SELECT MAX(gyldig_fra) FROM STANDARDTEKST S2 WHERE
               S1.nokkel = S2.nokkel
               AND S2.gyldig_fra <= ?)  
    """

fun DatabaseInterface.getStandardtekst(nokkel: String, timestamp: Timestamp?): String {
    this.connection.use { connection ->
        val resultList = connection.prepareStatement(queryGetStandardtekst).use {
            it.setString(1, nokkel)
            it.setTimestamp(2, timestamp ?: Timestamp.from(Instant.now()))
            it.executeQuery().toList { getString("tekst") }
        }
        if (resultList.isEmpty()) {
            throw RuntimeException("No standardtekst found for key $nokkel and timestamp $timestamp")
        }
        return resultList[0]
    }
}
