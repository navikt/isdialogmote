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
                INSERT INTO avvent (uuid, motebehov_uuid, created_at, frist, created_by, personident, beskrivelse, is_lukket)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { preparedStatement ->
                preparedStatement.setString(1, avvent.uuid.toString())
                preparedStatement.setString(2, avvent.motebehovUuid.toString())
                preparedStatement.setObject(3, avvent.createdAt)
                preparedStatement.setObject(4, avvent.frist)
                preparedStatement.setString(5, avvent.createdBy)
                preparedStatement.setString(6, avvent.personident.value)
                preparedStatement.setString(7, avvent.beskrivelse)
                preparedStatement.setBoolean(8, avvent.isLukket)
                preparedStatement.executeUpdate()
            }
        }
    }

    override fun getByMotebehovUuid(motebehovUuid: String): Avvent? {
        database.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT uuid, motebehov_uuid, created_at, frist, created_by, personident, beskrivelse, is_lukket
                FROM avvent
                WHERE motebehov_uuid = ?
                """.trimIndent()
            ).use { preparedStatement ->
                preparedStatement.setString(1, motebehovUuid)
                val resultSet = preparedStatement.executeQuery()
                return if (resultSet.next()) {
                    resultSet.toAvvent()
                } else {
                    null
                }
            }
        }
    }
}

private fun ResultSet.toAvvent() = Avvent(
    uuid = java.util.UUID.fromString(this.getString("uuid")),
    motebehovUuid = java.util.UUID.fromString(this.getString("motebehov_uuid")),
    createdAt = this.getObject("created_at", java.time.OffsetDateTime::class.java),
    frist = this.getObject("frist", java.time.LocalDate::class.java),
    createdBy = this.getString("created_by"),
    personident = no.nav.syfo.domain.PersonIdent(this.getString("personident")),
    beskrivelse = this.getString("beskrivelse"),
    isLukket = this.getBoolean("is_lukket")
)
