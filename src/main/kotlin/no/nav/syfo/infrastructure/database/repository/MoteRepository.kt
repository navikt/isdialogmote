package no.nav.syfo.infrastructure.database.repository

import com.fasterxml.jackson.core.type.TypeReference
import no.nav.syfo.api.authentication.configuredJacksonMapper
import no.nav.syfo.application.IMoteRepository
import no.nav.syfo.domain.EnhetNr
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Virksomhetsnummer
import no.nav.syfo.domain.dialogmote.Dialogmote
import no.nav.syfo.domain.dialogmote.DialogmoteTidSted
import no.nav.syfo.domain.dialogmote.DialogmotedeltakerArbeidsgiver
import no.nav.syfo.domain.dialogmote.DialogmotedeltakerArbeidstaker
import no.nav.syfo.domain.dialogmote.DialogmotedeltakerBehandler
import no.nav.syfo.domain.dialogmote.DocumentComponentDTO
import no.nav.syfo.domain.dialogmote.Referat
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.model.PDialogmote
import no.nav.syfo.infrastructure.database.model.PMotedeltakerAnnen
import no.nav.syfo.infrastructure.database.model.PMotedeltakerArbeidsgiver
import no.nav.syfo.infrastructure.database.model.PMotedeltakerArbeidsgiverVarsel
import no.nav.syfo.infrastructure.database.model.PMotedeltakerArbeidstaker
import no.nav.syfo.infrastructure.database.model.PMotedeltakerArbeidstakerVarsel
import no.nav.syfo.infrastructure.database.model.PMotedeltakerBehandler
import no.nav.syfo.infrastructure.database.model.PMotedeltakerBehandlerVarsel
import no.nav.syfo.infrastructure.database.model.PMotedeltakerBehandlerVarselSvar
import no.nav.syfo.infrastructure.database.model.PReferat
import no.nav.syfo.infrastructure.database.model.PTidSted
import no.nav.syfo.infrastructure.database.model.toDialogmote
import no.nav.syfo.infrastructure.database.model.toDialogmoteDeltakerAnnen
import no.nav.syfo.infrastructure.database.model.toDialogmoteTidSted
import no.nav.syfo.infrastructure.database.model.toDialogmotedeltakerArbeidsgiver
import no.nav.syfo.infrastructure.database.model.toDialogmotedeltakerArbeidstaker
import no.nav.syfo.infrastructure.database.model.toDialogmotedeltakerBehandler
import no.nav.syfo.infrastructure.database.model.toDialogmotedeltakerBehandlerVarsel
import no.nav.syfo.infrastructure.database.model.toReferat
import no.nav.syfo.infrastructure.database.toList
import no.nav.syfo.infrastructure.database.toPMotedeltakerAnnen
import no.nav.syfo.infrastructure.database.toPMotedeltakerArbeidsgiver
import no.nav.syfo.infrastructure.database.toPMotedeltakerArbeidsgiverVarsel
import no.nav.syfo.infrastructure.database.toPMotedeltakerArbeidstaker
import no.nav.syfo.infrastructure.database.toPMotedeltakerArbeidstakerVarsel
import no.nav.syfo.infrastructure.database.toPMotedeltakerBehandler
import no.nav.syfo.infrastructure.database.toPMotedeltakerBehandlerVarsel
import no.nav.syfo.infrastructure.database.toPTidSted
import no.nav.syfo.util.toOffsetDateTimeUTC
import java.sql.Connection
import java.sql.ResultSet
import java.util.*

class MoteRepository(private val database: DatabaseInterface) : IMoteRepository {

    private val mapper = configuredJacksonMapper()

    override fun getMote(moteUUID: UUID): Dialogmote =
        database.connection.use { connection ->
            val dialogmote = connection.getMoteForUUID(moteUUID)
            val arbeidstaker = connection.getMotedeltakerArbeidstaker(dialogmote.id)
            val arbeidsgiver = connection.getMotedeltakerArbeidsgiver(dialogmote.id)
            val tidSted = connection.getTidSted(dialogmote.id)

            dialogmote.toDialogmote(
                dialogmotedeltakerArbeidstaker = arbeidstaker,
                dialogmotedeltakerArbeidsgiver = arbeidsgiver,
                dialogmotedeltakerBehandler = connection.getBehandler(dialogmote.id),
                dialogmoteTidStedList = tidSted.map { it.toDialogmoteTidSted() },
                referatList = connection.getReferater(dialogmote.id, arbeidstaker.id, arbeidsgiver.id)
            )
        }

    override fun getMotedeltakerBehandler(moteId: Int): DialogmotedeltakerBehandler? =
        database.connection.use { connection ->
            connection.getBehandler(moteId)
        }

    override fun getMoterFor(personIdent: PersonIdent): List<PDialogmote> =
        database.connection.use { connection ->
            connection.prepareStatement(GET_DIALOGMOTER_FOR_PERSONIDENT_QUERY).use {
                it.setString(1, personIdent.value)
                it.executeQuery().toList { toPDialogmote() }
            }
        }

    override fun getDialogmoteList(enhetNr: EnhetNr): List<PDialogmote> =
        database.connection.use { connection ->
            connection.prepareStatement(GET_DIALOGMOTER_FOR_ENHET).use {
                it.setString(1, enhetNr.value)
                it.executeQuery().toList { toPDialogmote() }
            }
        }

    override fun getUnfinishedMoterForEnhet(enhetNr: EnhetNr): List<PDialogmote> =
        database.connection.use { connection ->
            connection.prepareStatement(GET_UNFINISHED_MOTER_FOR_ENHET).use {
                it.setString(1, enhetNr.value)
                it.executeQuery().toList { toPDialogmote() }
            }
        }

    override fun getUnfinishedMoterForVeileder(veilederIdent: String): List<PDialogmote> =
        database.connection.use { connection ->
            connection.prepareStatement(GET_UNFINISHED_MOTER_FOR_VEILEDER).use {
                it.setString(1, veilederIdent)
                it.executeQuery().toList { toPDialogmote() }
            }
        }

    override fun getMotedeltakerArbeidstaker(moteId: Int): DialogmotedeltakerArbeidstaker =
        database.connection.use { connection ->
            connection.getMotedeltakerArbeidstaker(moteId)
        }

    override fun getMotedeltakerArbeidsgiver(moteId: Int): DialogmotedeltakerArbeidsgiver =
        database.connection.use { connection ->
            connection.getMotedeltakerArbeidsgiver(moteId)
        }

    override fun getTidSted(moteId: Int): List<DialogmoteTidSted> =
        database.connection.use { connection ->
            connection.getTidSted(moteId)
                .map { it.toDialogmoteTidSted() }
        }

    override fun getReferatForMote(moteUUID: UUID): List<Referat> =
        database.connection.use { connection ->
            val referater = connection.prepareStatement(GET_REFERAT_FOR_MOTE_UUID).use {
                it.setString(1, moteUUID.toString())
                it.executeQuery().toList { toPReferat() }
            }
            referater.map { referat ->
                val arbeidstaker = connection.getPMotedeltakerArbeidstaker(referat.moteId)
                val arbeidsgiver = connection.getPMotedeltakerArbeidsgiver(referat.moteId)
                val andreDeltakere = connection.getAndreDeltakereForReferatID(referat.id)
                    .map { it.toDialogmoteDeltakerAnnen() }

                referat.toReferat(
                    andreDeltakere = andreDeltakere,
                    motedeltakerArbeidstakerId = arbeidstaker.id,
                    motedeltakerArbeidsgiverId = arbeidsgiver.id,
                )
            }
        }

    override fun getReferat(referatUUID: UUID): Referat? =
        database.connection.use { connection ->
            val referat = connection.prepareStatement(GET_REFERAT_QUERY).use {
                it.setString(1, referatUUID.toString())
                it.executeQuery().toList { toPReferat() }.firstOrNull()
            } ?: return null

            val arbeidstaker = connection.getPMotedeltakerArbeidstaker(referat.moteId)
            val arbeidsgiver = connection.getPMotedeltakerArbeidsgiver(referat.moteId)
            val andreDeltakere = connection.getAndreDeltakereForReferatID(referat.id)
                .map { it.toDialogmoteDeltakerAnnen() }

            referat.toReferat(
                andreDeltakere = andreDeltakere,
                motedeltakerArbeidstakerId = arbeidstaker.id,
                motedeltakerArbeidsgiverId = arbeidsgiver.id,
            )
        }

    override fun getFerdigstilteReferatWithoutJournalpostArbeidstakerList(): List<Pair<PersonIdent, Referat>> =
        database.connection.use { connection ->
            val referater = connection.prepareStatement(GET_FERDIGSTILTE_REFERAT_WITHOUT_JOURNALPOST_ARBEIDSTAKER).use {
                it.executeQuery().toList { toPReferat() }
            }
            referater.map { referat ->
                val arbeidstaker = connection.getPMotedeltakerArbeidstaker(referat.moteId)
                val arbeidsgiver = connection.getPMotedeltakerArbeidsgiver(referat.moteId)
                val andreDeltakere = connection.getAndreDeltakereForReferatID(referat.id)
                    .map { it.toDialogmoteDeltakerAnnen() }

                arbeidstaker.personIdent to
                    referat.toReferat(
                        andreDeltakere = andreDeltakere,
                        motedeltakerArbeidstakerId = arbeidstaker.id,
                        motedeltakerArbeidsgiverId = arbeidsgiver.id,
                    )
            }
        }

    override fun getFerdigstilteReferatWithoutJournalpostArbeidsgiverList(): List<Triple<Virksomhetsnummer, PersonIdent, Referat>> =
        database.connection.use { connection ->
            val referater = connection.prepareStatement(GET_FERDIGSTILTE_REFERAT_WITHOUT_JOURNALPOST_ARBEIDSGIVER).use {
                it.executeQuery().toList {
                    Pair(Virksomhetsnummer(getString("virksomhetsnummer")), toPReferat())
                }
            }
            referater.map { (virksomhetsnummer, referat) ->
                val arbeidstaker = connection.getPMotedeltakerArbeidstaker(referat.moteId)
                val arbeidsgiver = connection.getPMotedeltakerArbeidsgiver(referat.moteId)
                val andreDeltakere = connection.getAndreDeltakereForReferatID(referat.id)
                    .map { it.toDialogmoteDeltakerAnnen() }

                Triple(
                    virksomhetsnummer,
                    arbeidstaker.personIdent,
                    referat.toReferat(
                        andreDeltakere = andreDeltakere,
                        motedeltakerArbeidstakerId = arbeidstaker.id,
                        motedeltakerArbeidsgiverId = arbeidsgiver.id,
                    )
                )
            }
        }

    private fun Connection.getMotedeltakerArbeidsgiver(moteId: Int): DialogmotedeltakerArbeidsgiver {
        val arbeidsgiver = this.getPMotedeltakerArbeidsgiver(moteId)
        val varsler = this.getMotedeltakerArbeidsgiverVarsler(arbeidsgiver.id)
        return arbeidsgiver.toDialogmotedeltakerArbeidsgiver(varsler)
    }

    private fun Connection.getMotedeltakerArbeidstaker(moteId: Int): DialogmotedeltakerArbeidstaker {
        val arbeidstaker = this.getPMotedeltakerArbeidstaker(moteId)
        val varsler = this.getMotedeltakerArbeidstakerVarsler(arbeidstaker.id)
        return arbeidstaker.toDialogmotedeltakerArbeidstaker(varsler)
    }

    private fun Connection.getBehandler(moteId: Int): DialogmotedeltakerBehandler? {
        val behandler = this.getPMotedeltakerBehandler(moteId) ?: return null
        val pVarsler = this.getMotedeltakerBehandlerVarsler(behandler.id)
        val varsler = pVarsler.map { pVarsel ->
            val varselSvar = this.getMoteDeltakerBehandlerVarselSvar(pVarsel.id)
            pVarsel.toDialogmotedeltakerBehandlerVarsel(varselSvar)
        }
        return behandler.toDialogmotedeltakerBehandler(varsler)
    }

    private fun Connection.getReferater(dialogmoteId: Int, arbeidstakerId: Int, arbeidsgiverId: Int): List<Referat> {
        val pReferater = this.getReferat(dialogmoteId)
        val referater = pReferater.map { referat ->
            val andreDeltakere = this.getAndreDeltakereForReferatID(referat.id)
                .map { it.toDialogmoteDeltakerAnnen() }
            referat.toReferat(
                andreDeltakere = andreDeltakere,
                motedeltakerArbeidstakerId = arbeidstakerId,
                motedeltakerArbeidsgiverId = arbeidsgiverId,
            )
        }
        return referater
    }

    private fun Connection.getMoteForUUID(moteUUID: UUID): PDialogmote =
        this.prepareStatement(GET_DIALOGMOTE_FOR_UUID_QUERY).use {
            it.setString(1, moteUUID.toString())
            it.executeQuery().toList { toPDialogmote() }
        }.first()

    private fun Connection.getPMotedeltakerArbeidstaker(moteId: Int): PMotedeltakerArbeidstaker =
        this.prepareStatement(GET_MOTEDELTAKER_ARBEIDSTAKER).use {
            it.setInt(1, moteId)
            it.executeQuery().toList { toPMotedeltakerArbeidstaker() }
        }.single()

    private fun Connection.getMotedeltakerArbeidstakerVarsler(arbeidstakerId: Int): List<PMotedeltakerArbeidstakerVarsel> =
        this.prepareStatement(GET_VARSLER_MOTEDELTAKER_ARBEIDSTAKER).use {
            it.setInt(1, arbeidstakerId)
            it.executeQuery().toList { toPMotedeltakerArbeidstakerVarsel() }
        }

    private fun Connection.getPMotedeltakerArbeidsgiver(moteId: Int): PMotedeltakerArbeidsgiver =
        this.prepareStatement(GET_MOTEDELTAGER_ARBEIDSGIVER).use {
            it.setInt(1, moteId)
            it.executeQuery().toList { toPMotedeltakerArbeidsgiver() }
        }.single()

    private fun Connection.getMotedeltakerArbeidsgiverVarsler(arbeidsgiverId: Int): List<PMotedeltakerArbeidsgiverVarsel> =
        this.prepareStatement(GET_VARSLER_MOTEDELTAKER_ARBEIDSGIVER).use {
            it.setInt(1, arbeidsgiverId)
            it.executeQuery().toList { toPMotedeltakerArbeidsgiverVarsel() }
        }

    private fun Connection.getPMotedeltakerBehandler(moteId: Int): PMotedeltakerBehandler? =
        this.prepareStatement(GET_MOTEDELTAKER_BEHANDLER).use {
            it.setInt(1, moteId)
            it.executeQuery().toList { toPMotedeltakerBehandler() }
        }.firstOrNull()

    private fun Connection.getMotedeltakerBehandlerVarsler(behandlerId: Int): List<PMotedeltakerBehandlerVarsel> =
        this.prepareStatement(GET_VARSLER_MOTEDELTAKER_BEHANDLER).use {
            it.setInt(1, behandlerId)
            it.executeQuery().toList { toPMotedeltakerBehandlerVarsel() }
        }

    internal fun Connection.getMoteDeltakerBehandlerVarselSvar(varselId: Int): List<PMotedeltakerBehandlerVarselSvar> =
        this.prepareStatement(GET_VARSEL_SVAR_MOTEDELTAKER_BEHANDLER).use {
            it.setInt(1, varselId)
            it.executeQuery().toList { toPMotedeltakerBehandlerVarselSvar() }
        }

    private fun Connection.getTidSted(moteId: Int): List<PTidSted> =
        this.prepareStatement(GET_TID_STED_FOR_MOTE).use {
            it.setInt(1, moteId)
            it.executeQuery().toList { toPTidSted() }
        }

    private fun Connection.getReferat(moteId: Int): List<PReferat> =
        this.prepareStatement(GET_REFERAT_FOR_MOTE_ID).use {
            it.setInt(1, moteId)
            it.executeQuery().toList { toPReferat() }
        }

    private fun Connection.getAndreDeltakereForReferatID(referatId: Int): List<PMotedeltakerAnnen> =
        this.prepareStatement(GET_ANDRE_DELTAKERE_FOR_REFERAT_ID).use {
            it.setInt(1, referatId)
            it.executeQuery().toList { toPMotedeltakerAnnen() }
        }

    private fun ResultSet.toPReferat(): PReferat =
        PReferat(
            id = getInt("id"),
            uuid = UUID.fromString(getString("uuid")),
            createdAt = getTimestamp("created_at").toLocalDateTime(),
            updatedAt = getTimestamp("updated_at").toLocalDateTime(),
            moteId = getInt("mote_id"),
            digitalt = getBoolean("digitalt"),
            begrunnelseEndring = getString("begrunnelse_endring"),
            situasjon = getString("situasjon"),
            konklusjon = getString("konklusjon"),
            arbeidstakerOppgave = getString("arbeidstaker_oppgave"),
            arbeidsgiverOppgave = getString("arbeidsgiver_oppgave"),
            veilederOppgave = getString("veileder_oppgave"),
            behandlerOppgave = getString("behandler_oppgave"),
            narmesteLederNavn = getString("narmeste_leder_navn"),
            document = mapper.readValue(getString("document"), object : TypeReference<List<DocumentComponentDTO>>() {}),
            pdfId = getInt("pdf_id"),
            journalpostIdArbeidstaker = getString("journalpost_id"),
            lestDatoArbeidstaker = getTimestamp("lest_dato_arbeidstaker")?.toLocalDateTime(),
            lestDatoArbeidsgiver = getTimestamp("lest_dato_arbeidsgiver")?.toLocalDateTime(),
            brevBestillingsId = getString("brev_bestilling_id"),
            brevBestiltTidspunkt = getTimestamp("brev_bestilt_tidspunkt")?.toLocalDateTime(),
            ferdigstilt = getBoolean("ferdigstilt"),
        )

    companion object {
        private const val GET_DIALOGMOTE_FOR_UUID_QUERY =
            """
                SELECT *
                FROM MOTE
                WHERE uuid = ?
            """

        private const val GET_DIALOGMOTER_FOR_PERSONIDENT_QUERY =
            """
                SELECT *
                FROM MOTE
                INNER JOIN MOTEDELTAKER_ARBEIDSTAKER on MOTEDELTAKER_ARBEIDSTAKER.mote_id = MOTE.id
                WHERE personident = ?
                ORDER BY MOTE.created_at DESC
            """

        private const val GET_DIALOGMOTER_FOR_ENHET =
            """
                SELECT *
                FROM MOTE
                WHERE tildelt_enhet = ?
                ORDER BY MOTE.created_at DESC
            """

        private const val GET_UNFINISHED_MOTER_FOR_ENHET =
            """
                SELECT *
                FROM MOTE
                WHERE tildelt_enhet = ? AND status IN ('INNKALT', 'NYTT_TID_STED')
                ORDER BY MOTE.created_at DESC
            """

        private const val GET_UNFINISHED_MOTER_FOR_VEILEDER =
            """
                SELECT *
                FROM MOTE
                WHERE tildelt_veileder_ident = ? AND status IN ('INNKALT', 'NYTT_TID_STED')
                ORDER BY MOTE.created_at DESC
            """

        private const val GET_MOTEDELTAKER_ARBEIDSTAKER =
            """
                SELECT *
                FROM MOTEDELTAKER_ARBEIDSTAKER
                WHERE mote_id = ?
            """

        private const val GET_VARSLER_MOTEDELTAKER_ARBEIDSTAKER =
            """
                SELECT *
                FROM MOTEDELTAKER_ARBEIDSTAKER_VARSEL
                WHERE motedeltaker_arbeidstaker_id = ?
                ORDER BY created_at DESC
            """

        private const val GET_MOTEDELTAGER_ARBEIDSGIVER =
            """
                SELECT *
                FROM MOTEDELTAKER_ARBEIDSGIVER
                WHERE mote_id = ?
            """

        private const val GET_VARSLER_MOTEDELTAKER_ARBEIDSGIVER =
            """
                SELECT *
                FROM MOTEDELTAKER_ARBEIDSGIVER_VARSEL
                WHERE motedeltaker_arbeidsgiver_id = ?
                ORDER BY created_at DESC
            """

        private const val GET_MOTEDELTAKER_BEHANDLER =
            """
                SELECT *
                FROM MOTEDELTAKER_BEHANDLER
                WHERE mote_id = ?
            """

        private const val GET_VARSLER_MOTEDELTAKER_BEHANDLER =
            """
                SELECT *
                FROM MOTEDELTAKER_BEHANDLER_VARSEL
                WHERE motedeltaker_behandler_id = ?
                ORDER BY created_at DESC
            """

        private const val GET_VARSEL_SVAR_MOTEDELTAKER_BEHANDLER =
            """
                SELECT *
                FROM MOTEDELTAKER_BEHANDLER_VARSEL_SVAR
                WHERE motedeltaker_behandler_varsel_id = ?
                ORDER BY created_at DESC
            """

        private const val GET_TID_STED_FOR_MOTE =
            """
                SELECT *
                FROM TID_STED
                WHERE mote_id = ?
            """

        private const val GET_REFERAT_QUERY =
            """
                SELECT *
                FROM MOTE_REFERAT
                WHERE uuid = ?
            """

        private const val GET_REFERAT_FOR_MOTE_UUID =
            """
                SELECT MOTE_REFERAT.*
                FROM MOTE INNER JOIN MOTE_REFERAT on (MOTE.id = MOTE_REFERAT.mote_id)
                WHERE MOTE.uuid = ?
                ORDER BY MOTE_REFERAT.created_at DESC
            """

        private const val GET_REFERAT_FOR_MOTE_ID =
            """
                SELECT MOTE_REFERAT.*
                FROM MOTE INNER JOIN MOTE_REFERAT on (MOTE.id = MOTE_REFERAT.mote_id)
                WHERE MOTE.id = ?
                ORDER BY MOTE_REFERAT.created_at DESC
            """

        private const val GET_ANDRE_DELTAKERE_FOR_REFERAT_ID =
            """
                SELECT *
                FROM MOTEDELTAKER_ANNEN
                WHERE mote_referat_id = ?
            """

        private const val GET_FERDIGSTILTE_REFERAT_WITHOUT_JOURNALPOST_ARBEIDSTAKER =
            """
                SELECT MOTE_REFERAT.*
                FROM MOTE INNER JOIN MOTE_REFERAT ON (MOTE.ID = MOTE_REFERAT.MOTE_ID)
                    INNER JOIN MOTEDELTAKER_ARBEIDSTAKER ON (MOTE.ID = MOTEDELTAKER_ARBEIDSTAKER.MOTE_ID) 
                WHERE MOTE_REFERAT.journalpost_id IS NULL AND MOTE_REFERAT.ferdigstilt = true
                ORDER BY MOTE_REFERAT.created_at ASC
                LIMIT 20
            """

        private const val GET_FERDIGSTILTE_REFERAT_WITHOUT_JOURNALPOST_ARBEIDSGIVER =
            """
                SELECT MOTEDELTAKER_ARBEIDSGIVER.VIRKSOMHETSNUMMER, MOTE_REFERAT.*
                FROM MOTE INNER JOIN MOTE_REFERAT ON (MOTE.ID = MOTE_REFERAT.MOTE_ID)
                          INNER JOIN MOTEDELTAKER_ARBEIDSGIVER ON (MOTE.ID = MOTEDELTAKER_ARBEIDSGIVER.MOTE_ID) 
                WHERE MOTE_REFERAT.journalpost_ag_id IS NULL AND MOTE_REFERAT.ferdigstilt = true
                ORDER BY MOTE_REFERAT.created_at ASC
                LIMIT 20
            """
    }
}

fun ResultSet.toPDialogmote(): PDialogmote =
    PDialogmote(
        id = getInt("id"),
        uuid = UUID.fromString(getString("uuid")),
        createdAt = getTimestamp("created_at").toLocalDateTime(),
        updatedAt = getTimestamp("updated_at").toLocalDateTime(),
        status = getString("status"),
        opprettetAv = getString("opprettet_av"),
        tildeltVeilederIdent = getString("tildelt_veileder_ident"),
        tildeltEnhet = getString("tildelt_enhet")
    )

private fun ResultSet.toPMotedeltakerBehandlerVarselSvar(): PMotedeltakerBehandlerVarselSvar =
    PMotedeltakerBehandlerVarselSvar(
        id = getInt("id"),
        uuid = UUID.fromString(getString("uuid")),
        createdAt = getTimestamp("created_at").toLocalDateTime(),
        motedeltakerBehandlerVarselId = getInt("motedeltaker_behandler_varsel_id"),
        svarType = getString("svar_type"),
        svarTekst = getString("svar_tekst"),
        msgId = getString("msg_id"),
        svarPublishedToKafkaAt = getTimestamp("svar_published_to_kafka_at")?.toLocalDateTime()?.toOffsetDateTimeUTC(),
    )
