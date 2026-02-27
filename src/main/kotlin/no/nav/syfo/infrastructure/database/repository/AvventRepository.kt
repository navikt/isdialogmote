package no.nav.syfo.infrastructure.database.repository

import no.nav.syfo.application.IAvventRepository
import no.nav.syfo.domain.dialogmote.Avvent
import no.nav.syfo.infrastructure.database.DatabaseInterface
import java.sql.ResultSet

class AvventRepository(private val database: DatabaseInterface): IAvventRepository {
    override fun persist(avvent: Avvent) {
        database.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO avvent (uuid, created_at, frist, created_by, personident, beskrivelse, is_lukket)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { preparedStatement ->
                preparedStatement.setString(1, avvent.uuid.toString())
                preparedStatement.setObject(2, avvent.createdAt)
                preparedStatement.setObject(3, avvent.frist)
                preparedStatement.setString(4, avvent.createdBy)
                preparedStatement.setString(5, avvent.personident.value)
                preparedStatement.setString(6, avvent.beskrivelse)
                preparedStatement.setBoolean(7, avvent.isLukket)
                preparedStatement.executeUpdate()
            }
            connection.commit()
        }
    }
}

private fun ResultSet.toAvvent() = Avvent(
    uuid = java.util.UUID.fromString(this.getString("uuid")),
    createdAt = this.getObject("created_at", java.time.OffsetDateTime::class.java),
    frist = this.getObject("frist", java.time.LocalDate::class.java),
    createdBy = this.getString("created_by"),
    personident = no.nav.syfo.domain.PersonIdent(this.getString("personident")),
    beskrivelse = this.getString("beskrivelse"),
    isLukket = this.getBoolean("is_lukket")
)
