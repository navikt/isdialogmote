package no.nav.syfo.infrastructure.database.repository

import no.nav.syfo.application.IDialogmoteFlytRepository
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.dialogmote.Dialogmote
import no.nav.syfo.domain.dialogmoteflyt.DialogmoteFlyt
import no.nav.syfo.domain.dialogmoteflyt.DialogmoteFlytEndring
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.model.PAvvent
import no.nav.syfo.infrastructure.database.model.PDialogmoteFlyt
import no.nav.syfo.infrastructure.database.model.toAvvent
import no.nav.syfo.infrastructure.database.model.toDialogmoteFlyt
import no.nav.syfo.infrastructure.database.toList
import java.sql.Connection
import java.sql.ResultSet
import java.util.*

class DialogmoteFlytRepository(private val database: DatabaseInterface) : IDialogmoteFlytRepository {

    override fun getDialogmoteFlyt(dialogmoteFlytUuid: UUID): DialogmoteFlyt? =
        database.connection.use { connection ->
            val pDialogmoteFlyt = connection.prepareStatement(GET_DIALOGMOTE_FLYT_BY_UUID).use {
                it.setString(1, dialogmoteFlytUuid.toString())
                it.executeQuery().toList { toPDialogmoteFlyt() }
            }.firstOrNull() ?: return null

            connection.extendDialogmoteFlytRelations(pDialogmoteFlyt)
        }

    override fun getDialogmoteFlytForPerson(personIdent: PersonIdent): List<DialogmoteFlyt> =
        database.connection.use { connection ->
            val flytList = connection.prepareStatement(GET_DIALOGMOTE_FLYT_FOR_PERSON).use {
                it.setString(1, personIdent.value)
                it.executeQuery().toList { toPDialogmoteFlyt() }
            }
            flytList.map { connection.extendDialogmoteFlytRelations(it) }
        }

    private fun Connection.extendDialogmoteFlytRelations(pDialogmoteFlyt: PDialogmoteFlyt): DialogmoteFlyt {
        val avventer = this.getAvventer(pDialogmoteFlyt.id)
        val moter = this.getMoterForFlyt(pDialogmoteFlyt.id)

        val endringer = mutableListOf<DialogmoteFlytEndring>()
        endringer.addAll(avventer.map { DialogmoteFlytEndring.AvventEndring(it.toAvvent()) })
        endringer.addAll(moter.map {
            DialogmoteFlytEndring.DialogmoteEndring(
                moteUuid = it.uuid,
                moteStatus = Dialogmote.Status.valueOf(it.status),
                createdAt = it.createdAt,
            )
        })

        return pDialogmoteFlyt.toDialogmoteFlyt(endringer)
    }

    private fun Connection.getAvventer(dialogmoteFlytId: Int): List<PAvvent> =
        this.prepareStatement(GET_AVVENT_FOR_FLYT).use {
            it.setInt(1, dialogmoteFlytId)
            it.executeQuery().toList { toPAvvent() }
        }

    private data class PMoteRef(
        val uuid: UUID,
        val status: String,
        val createdAt: java.time.LocalDateTime,
    )

    private fun Connection.getMoterForFlyt(dialogmoteFlytId: Int): List<PMoteRef> =
        this.prepareStatement(GET_MOTER_FOR_FLYT).use {
            it.setInt(1, dialogmoteFlytId)
            it.executeQuery().toList {
                PMoteRef(
                    uuid = UUID.fromString(getString("uuid")),
                    status = getString("status"),
                    createdAt = getTimestamp("created_at").toLocalDateTime(),
                )
            }
        }

    private fun ResultSet.toPDialogmoteFlyt(): PDialogmoteFlyt =
        PDialogmoteFlyt(
            id = getInt("id"),
            uuid = UUID.fromString(getString("uuid")),
            createdAt = getTimestamp("created_at").toLocalDateTime(),
            updatedAt = getTimestamp("updated_at").toLocalDateTime(),
        )

    private fun ResultSet.toPAvvent(): PAvvent =
        PAvvent(
            id = getInt("id"),
            uuid = UUID.fromString(getString("uuid")),
            createdAt = getTimestamp("created_at").toLocalDateTime(),
            updatedAt = getTimestamp("updated_at").toLocalDateTime(),
            dialogmoteFlytId = getInt("dialogmote_flyt_id"),
            frist = getDate("frist").toLocalDate(),
            beskrivelse = getString("beskrivelse"),
            personident = getString("personident"),
            createdBy = getString("created_by"),
        )

    companion object {
        private const val GET_DIALOGMOTE_FLYT_BY_UUID =
            """
                SELECT *
                FROM DIALOGMOTE_FLYT
                WHERE uuid = ?
            """

        private const val GET_DIALOGMOTE_FLYT_FOR_PERSON =
            """
                SELECT DISTINCT df.*
                FROM DIALOGMOTE_FLYT df
                INNER JOIN AVVENT a ON a.dialogmote_flyt_id = df.id
                WHERE a.personident = ?
                ORDER BY df.created_at DESC
            """

        private const val GET_AVVENT_FOR_FLYT =
            """
                SELECT *
                FROM AVVENT
                WHERE dialogmote_flyt_id = ?
                ORDER BY created_at ASC
            """

        private const val GET_MOTER_FOR_FLYT =
            """
                SELECT uuid, status, created_at
                FROM MOTE
                WHERE dialogmote_flyt_id = ?
                ORDER BY created_at ASC
            """
    }
}
