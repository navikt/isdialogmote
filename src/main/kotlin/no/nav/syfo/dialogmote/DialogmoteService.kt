package no.nav.syfo.dialogmote

import no.nav.syfo.application.api.authentication.getNAVIdentFromToken
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.exception.ConflictException
import no.nav.syfo.client.behandlendeenhet.BehandlendeEnhetClient
import no.nav.syfo.client.narmesteleder.NarmesteLederClient
import no.nav.syfo.client.narmesteleder.NarmesteLederRelasjonDTO
import no.nav.syfo.client.oppfolgingstilfelle.*
import no.nav.syfo.client.pdfgen.PdfGenClient
import no.nav.syfo.client.person.kontaktinfo.KontaktinformasjonClient
import no.nav.syfo.dialogmote.api.domain.*
import no.nav.syfo.dialogmote.database.*
import no.nav.syfo.dialogmote.database.domain.*
import no.nav.syfo.dialogmote.domain.*
import no.nav.syfo.domain.*
import java.sql.Connection
import java.time.LocalDateTime
import java.util.*

class DialogmoteService(
    private val database: DatabaseInterface,
    private val dialogmotedeltakerService: DialogmotedeltakerService,
    private val behandlendeEnhetClient: BehandlendeEnhetClient,
    private val narmesteLederClient: NarmesteLederClient,
    private val oppfolgingstilfelleClient: OppfolgingstilfelleClient,
    private val pdfGenClient: PdfGenClient,
    private val kontaktinformasjonClient: KontaktinformasjonClient,
    private val varselService: VarselService,
) {
    fun getDialogmote(
        moteUUID: UUID
    ): Dialogmote {
        return database.getDialogmote(moteUUID).first().let { pDialogmote ->
            extendDialogmoteRelations(pDialogmote)
        }
    }

    fun getDialogmoteList(
        personIdentNumber: PersonIdentNumber,
    ): List<Dialogmote> {
        return database.getDialogmoteList(personIdentNumber).map { pDialogmote ->
            extendDialogmoteRelations(pDialogmote)
        }
    }

    fun getDialogmoteList(
        enhetNr: EnhetNr,
    ): List<Dialogmote> {
        return database.getDialogmoteList(enhetNr).map { pDialogmote ->
            extendDialogmoteRelations(pDialogmote)
        }
    }

    fun getDialogmoteUnfinishedList(enhetNr: EnhetNr): List<Dialogmote> {
        return database.getDialogmoteUnfinishedList(enhetNr).map { pDialogmote ->
            extendDialogmoteRelations(pDialogmote)
        }
    }

    fun getDialogmoteUnfinishedListForVeilederIdent(veilederIdent: String): List<Dialogmote> {
        return database.getDialogmoteUnfinishedListForVeilederIdent(veilederIdent).map { pDialogmote ->
            extendDialogmoteRelations(pDialogmote)
        }
    }

    private fun extendDialogmoteRelations(
        pDialogmote: PDialogmote,
    ): Dialogmote {
        val motedeltakerArbeidstaker = dialogmotedeltakerService.getDialogmoteDeltakerArbeidstaker(pDialogmote.id)
        val motedeltakerArbeidsgiver = dialogmotedeltakerService.getDialogmoteDeltakerArbeidsgiver(pDialogmote.id)
        val motedeltakerBehandler = dialogmotedeltakerService.getDialogmoteDeltakerBehandler(pDialogmote.id)
        val dialogmoteTidStedList = getDialogmoteTidStedList(pDialogmote.id)
        val referatList = getReferatForMote(pDialogmote.uuid)
        return pDialogmote.toDialogmote(
            dialogmotedeltakerArbeidstaker = motedeltakerArbeidstaker,
            dialogmotedeltakerArbeidsgiver = motedeltakerArbeidsgiver,
            dialogmotedeltakerBehandler = motedeltakerBehandler,
            dialogmoteTidStedList = dialogmoteTidStedList,
            referatList = referatList,
        )
    }

    private fun getDialogmoteTidStedList(
        moteId: Int
    ): List<DialogmoteTidSted> {
        return database.getTidSted(moteId).map {
            it.toDialogmoteTidSted()
        }
    }

    suspend fun createMoteinnkalling(
        newDialogmoteDTO: NewDialogmoteDTO,
        callId: String,
        token: String,
    ) {
        val personIdentNumber = PersonIdentNumber(newDialogmoteDTO.arbeidstaker.personIdent)
        val virksomhetsnummer = Virksomhetsnummer(newDialogmoteDTO.arbeidsgiver.virksomhetsnummer)

        val anyUnfinishedDialogmote =
            getDialogmoteList(personIdentNumber).filter {
                it.arbeidsgiver.virksomhetsnummer == virksomhetsnummer
            }.anyUnfinished()

        if (anyUnfinishedDialogmote) {
            throw ConflictException("Denied access to create Dialogmote: unfinished Dialogmote exists for PersonIdent")
        }

        val narmesteLeder = narmesteLederClient.activeLeder(
            personIdentNumber = personIdentNumber,
            virksomhetsnummer = virksomhetsnummer,
            callId = callId,
            token = token,
        )

        val behandlendeEnhet = behandlendeEnhetClient.getEnhet(
            callId = callId,
            personIdentNumber = personIdentNumber,
            token = token,
        ) ?: throw RuntimeException("Failed to request BehandlendeEnhet of Person")

        val newDialogmote = newDialogmoteDTO.toNewDialogmote(
            requestByNAVIdent = getNAVIdentFromToken(token),
            navEnhet = EnhetNr(behandlendeEnhet.enhetId),
        )

        val tilfelle = oppfolgingstilfelleClient.oppfolgingstilfelle(
            callId = callId,
            personIdentNumber = personIdentNumber,
            token = token,
        ) ?: throw RuntimeException("Cannot create Dialogmote: No Oppfolgingstilfelle was found")

        if (tilfelle.isInactive()) {
            throw RuntimeException("Cannot create Dialogmote: Dialogmoteinnkalling for person with inactive Oppfolgingstilfelle is not allowed")
        }

        val pdfInnkallingArbeidstaker = pdfGenClient.pdfInnkalling(
            callId = callId,
            documentComponentDTOList = newDialogmoteDTO.arbeidstaker.innkalling,
        ) ?: throw RuntimeException("Failed to request PDF - Innkalling Arbeidstaker")

        val pdfInnkallingArbeidsgiver = pdfGenClient.pdfInnkalling(
            callId = callId,
            documentComponentDTOList = newDialogmoteDTO.arbeidsgiver.innkalling,
        ) ?: throw RuntimeException("Failed to request PDF - Innkalling Arbeidsgiver")

        val pdfInnkallingBehandler = newDialogmoteDTO.behandler?.let {
            pdfGenClient.pdfInnkalling(
                callId = callId,
                documentComponentDTOList = it.innkalling,
            ) ?: throw RuntimeException("Failed to request PDF - Innkalling Behandler")
        }

        val createdDialogmoteIdentifiers: CreatedDialogmoteIdentifiers

        val digitalVarsling = isDigitalVarselEnabled(
            personIdentNumber = personIdentNumber,
            token = token,
            callId = callId,
        )

        database.connection.use { connection ->
            createdDialogmoteIdentifiers = connection.createNewDialogmoteWithReferences(
                commit = false,
                newDialogmote = newDialogmote,
            )
            createMoteStatusEndring(
                connection = connection,
                dialogmoteId = createdDialogmoteIdentifiers.dialogmoteIdPair.first,
                dialogmoteStatus = newDialogmote.status,
                isBehandlerMotedeltaker = newDialogmote.behandler != null,
                opprettetAv = newDialogmote.opprettetAv,
                tilfelle = tilfelle,
            )
            createAndSendVarsel(
                connection = connection,
                arbeidstakerId = createdDialogmoteIdentifiers.motedeltakerArbeidstakerIdPair.first,
                arbeidstakerUuid = createdDialogmoteIdentifiers.motedeltakerArbeidstakerIdPair.second,
                arbeidsgiverId = createdDialogmoteIdentifiers.motedeltakerArbeidsgiverIdPair.first,
                behandlerId = createdDialogmoteIdentifiers.motedeltakerBehandlerIdPair?.first,
                behandlerRef = newDialogmote.behandler?.behandlerRef,
                behandlerParentVarselId = null,
                behandlerInnkallingUuid = null,
                arbeidstakerPersonIdent = newDialogmote.arbeidstaker.personIdent,
                pdfArbeidstaker = pdfInnkallingArbeidstaker,
                pdfArbeidsgiver = pdfInnkallingArbeidsgiver,
                pdfBehandler = pdfInnkallingBehandler,
                narmesteLeder = narmesteLeder,
                varselType = MotedeltakerVarselType.INNKALT,
                fritekstArbeidstaker = newDialogmote.arbeidstaker.fritekstInnkalling.orEmpty(),
                fritekstArbeidsgiver = newDialogmote.arbeidsgiver.fritekstInnkalling.orEmpty(),
                fritekstBehandler = newDialogmote.behandler?.fritekstInnkalling.orEmpty(),
                documentArbeidstaker = newDialogmoteDTO.arbeidstaker.innkalling,
                documentArbeidsgiver = newDialogmoteDTO.arbeidsgiver.innkalling,
                documentBehandler = newDialogmoteDTO.behandler?.innkalling ?: emptyList(),
                moteTidspunkt = newDialogmoteDTO.tidSted.tid,
                digitalArbeidstakerVarsling = digitalVarsling,
                virksomhetsnummer = virksomhetsnummer
            )

            connection.commit()
        }
    }

    suspend fun avlysMoteinnkalling(
        callId: String,
        dialogmote: Dialogmote,
        avlysDialogmote: AvlysDialogmoteDTO,
        token: String,
    ) {

        val virksomhetsnummer = dialogmote.arbeidsgiver.virksomhetsnummer

        if (dialogmote.status == DialogmoteStatus.FERDIGSTILT) {
            throw ConflictException("Failed to Avlys Dialogmote: already Ferdigstilt")
        }
        if (dialogmote.status == DialogmoteStatus.AVLYST) {
            throw ConflictException("Failed to Avlys Dialogmote: already Avlyst")
        }
        if (dialogmote.behandler != null && avlysDialogmote.behandler == null) {
            throw IllegalArgumentException("Failed to Avlys Dialogmote: missing behandler")
        }

        val pdfAvlysningArbeidstaker = pdfGenClient.pdfAvlysning(
            callId = callId,
            documentComponentDTOList = avlysDialogmote.arbeidstaker.avlysning,
        ) ?: throw RuntimeException("Failed to request PDF - Avlysning Arbeidstaker")

        val pdfAvlysningArbeidsgiver = pdfGenClient.pdfAvlysning(
            callId = callId,
            documentComponentDTOList = avlysDialogmote.arbeidsgiver.avlysning,
        ) ?: throw RuntimeException("Failed to request PDF - Avlysning Arbeidsgiver")

        val pdfAvlysningBehandler = avlysDialogmote.behandler?.let {
            pdfGenClient.pdfAvlysning(
                callId = callId,
                documentComponentDTOList = it.avlysning,
            ) ?: throw RuntimeException("Failed to request PDF - Avlysning Behandler")
        }

        val narmesteLeder = narmesteLederClient.activeLeder(
            personIdentNumber = dialogmote.arbeidstaker.personIdent,
            virksomhetsnummer = virksomhetsnummer,
            callId = callId,
            token = token,
        )
        val digitalVarsling = isDigitalVarselEnabled(
            personIdentNumber = dialogmote.arbeidstaker.personIdent,
            token = token,
            callId = callId,
        )

        database.connection.use { connection ->
            updateMoteStatus(
                callId = callId,
                connection = connection,
                dialogmoteId = dialogmote.id,
                isBehandlerMotedeltaker = avlysDialogmote.behandler != null,
                newDialogmoteStatus = DialogmoteStatus.AVLYST,
                opprettetAv = getNAVIdentFromToken(token),
                personIdentNumber = dialogmote.arbeidstaker.personIdent,
                token = token,
            )
            createAndSendVarsel(
                connection = connection,
                arbeidstakerId = dialogmote.arbeidstaker.id,
                arbeidstakerUuid = dialogmote.arbeidstaker.uuid,
                arbeidsgiverId = dialogmote.arbeidsgiver.id,
                behandlerId = dialogmote.behandler?.id,
                behandlerRef = dialogmote.behandler?.behandlerRef,
                behandlerParentVarselId = dialogmote.behandler?.findParentVarselId(),
                behandlerInnkallingUuid = dialogmote.behandler?.findInnkallingVarselUuid(),
                arbeidstakerPersonIdent = dialogmote.arbeidstaker.personIdent,
                pdfArbeidstaker = pdfAvlysningArbeidstaker,
                pdfArbeidsgiver = pdfAvlysningArbeidsgiver,
                pdfBehandler = pdfAvlysningBehandler,
                narmesteLeder = narmesteLeder,
                varselType = MotedeltakerVarselType.AVLYST,
                fritekstArbeidstaker = avlysDialogmote.arbeidstaker.begrunnelse,
                fritekstArbeidsgiver = avlysDialogmote.arbeidsgiver.begrunnelse,
                documentArbeidstaker = avlysDialogmote.arbeidstaker.avlysning,
                documentArbeidsgiver = avlysDialogmote.arbeidsgiver.avlysning,
                documentBehandler = avlysDialogmote.behandler?.avlysning ?: emptyList(),
                moteTidspunkt = dialogmote.tidStedList.latest()!!.tid,
                digitalArbeidstakerVarsling = digitalVarsling,
                virksomhetsnummer = virksomhetsnummer,
            )
            connection.commit()
        }
    }

    suspend fun nyttMoteinnkallingTidSted(
        callId: String,
        dialogmote: Dialogmote,
        endreDialogmoteTidSted: EndreTidStedDialogmoteDTO,
        token: String,
    ) {

        val virksomhetsnummer = dialogmote.arbeidsgiver.virksomhetsnummer

        if (dialogmote.status == DialogmoteStatus.FERDIGSTILT) {
            throw ConflictException("Failed to change tid/sted, already Ferdigstilt")
        }
        if (dialogmote.status == DialogmoteStatus.AVLYST) {
            throw ConflictException("Failed to change tid/sted, already Avlyst")
        }
        if (dialogmote.behandler != null && endreDialogmoteTidSted.behandler == null) {
            throw IllegalArgumentException("Failed to change tid/sted: missing behandler")
        }

        val pdfEndringArbeidstaker = pdfGenClient.pdfEndringTidSted(
            callId = callId,
            documentComponentDTOList = endreDialogmoteTidSted.arbeidstaker.endringsdokument
        ) ?: throw RuntimeException("Failed to request PDF - EndringTidSted Arbeidstaker")

        val pdfEndringArbeidsgiver = pdfGenClient.pdfEndringTidSted(
            callId = callId,
            documentComponentDTOList = endreDialogmoteTidSted.arbeidsgiver.endringsdokument
        ) ?: throw RuntimeException("Failed to request PDF - EndringTidSted Arbeidsgiver")

        val pdfEndringBehandler = endreDialogmoteTidSted.behandler?.let {
            pdfGenClient.pdfEndringTidSted(
                callId = callId,
                documentComponentDTOList = it.endringsdokument
            ) ?: throw RuntimeException("Failed to request PDF - EndringTidSted Behandler")
        }

        val narmesteLeder = narmesteLederClient.activeLeder(
            personIdentNumber = dialogmote.arbeidstaker.personIdent,
            virksomhetsnummer = virksomhetsnummer,
            callId = callId,
            token = token,
        )

        val digitalVarsling = isDigitalVarselEnabled(
            personIdentNumber = dialogmote.arbeidstaker.personIdent,
            token = token,
            callId = callId,
        )

        database.connection.use { connection ->
            connection.updateMoteTidSted(
                commit = false,
                moteId = dialogmote.id,
                newDialogmoteTidSted = endreDialogmoteTidSted,
            )
            updateMoteStatus(
                callId = callId,
                connection = connection,
                dialogmoteId = dialogmote.id,
                isBehandlerMotedeltaker = dialogmote.behandler != null,
                newDialogmoteStatus = DialogmoteStatus.NYTT_TID_STED,
                opprettetAv = getNAVIdentFromToken(token),
                personIdentNumber = dialogmote.arbeidstaker.personIdent,
                token = token,
            )
            createAndSendVarsel(
                connection = connection,
                arbeidstakerId = dialogmote.arbeidstaker.id,
                arbeidstakerUuid = dialogmote.arbeidstaker.uuid,
                arbeidsgiverId = dialogmote.arbeidsgiver.id,
                behandlerId = dialogmote.behandler?.id,
                behandlerRef = dialogmote.behandler?.behandlerRef,
                behandlerParentVarselId = dialogmote.behandler?.findParentVarselId(),
                behandlerInnkallingUuid = dialogmote.behandler?.findInnkallingVarselUuid(),
                arbeidstakerPersonIdent = dialogmote.arbeidstaker.personIdent,
                pdfArbeidstaker = pdfEndringArbeidstaker,
                pdfArbeidsgiver = pdfEndringArbeidsgiver,
                pdfBehandler = pdfEndringBehandler,
                narmesteLeder = narmesteLeder,
                varselType = MotedeltakerVarselType.NYTT_TID_STED,
                fritekstArbeidstaker = endreDialogmoteTidSted.arbeidstaker.begrunnelse,
                fritekstArbeidsgiver = endreDialogmoteTidSted.arbeidsgiver.begrunnelse,
                documentArbeidstaker = endreDialogmoteTidSted.arbeidstaker.endringsdokument,
                documentArbeidsgiver = endreDialogmoteTidSted.arbeidsgiver.endringsdokument,
                documentBehandler = endreDialogmoteTidSted.behandler?.endringsdokument ?: emptyList(),
                moteTidspunkt = endreDialogmoteTidSted.tid,
                digitalArbeidstakerVarsling = digitalVarsling,
                virksomhetsnummer = virksomhetsnummer,
            )

            connection.commit()
        }
    }

    private fun createAndSendVarsel(
        connection: Connection,
        arbeidstakerUuid: UUID,
        arbeidstakerId: Int,
        arbeidsgiverId: Int,
        behandlerId: Int?,
        behandlerRef: String?,
        behandlerParentVarselId: String?,
        behandlerInnkallingUuid: UUID?,
        arbeidstakerPersonIdent: PersonIdentNumber,
        pdfArbeidstaker: ByteArray,
        pdfArbeidsgiver: ByteArray,
        pdfBehandler: ByteArray?,
        narmesteLeder: NarmesteLederRelasjonDTO?,
        varselType: MotedeltakerVarselType,
        fritekstArbeidstaker: String = "",
        fritekstArbeidsgiver: String = "",
        fritekstBehandler: String = "",
        documentArbeidstaker: List<DocumentComponentDTO> = emptyList(),
        documentArbeidsgiver: List<DocumentComponentDTO> = emptyList(),
        documentBehandler: List<DocumentComponentDTO> = emptyList(),
        moteTidspunkt: LocalDateTime,
        digitalArbeidstakerVarsling: Boolean,
        virksomhetsnummer: Virksomhetsnummer,
    ) {
        val (pdfArbeidstakerId, _) = connection.createPdf(
            commit = false,
            pdf = pdfArbeidstaker,
        )
        val (pdfArbeidsgiverId, _) = connection.createPdf(
            commit = false,
            pdf = pdfArbeidsgiver,
        )
        val (_, varselArbeidstakerId) = connection.createMotedeltakerVarselArbeidstaker(
            commit = false,
            motedeltakerArbeidstakerId = arbeidstakerId,
            status = "OK",
            varselType = varselType,
            digitalt = digitalArbeidstakerVarsling,
            pdfId = pdfArbeidstakerId,
            fritekst = fritekstArbeidstaker,
            document = documentArbeidstaker,
        )
        val (_, virksomhetsbrevId) = connection.createMotedeltakerVarselArbeidsgiver(
            commit = false,
            motedeltakerArbeidsgiverId = arbeidsgiverId,
            status = "OK",
            varselType = varselType,
            pdfId = pdfArbeidsgiverId,
            fritekst = fritekstArbeidsgiver,
            sendAltinn = narmesteLeder == null,
            document = documentArbeidsgiver,
        )
        val behandlerVarselIdPair = behandlerId?.let {
            val (pdfBehandlerId, _) = connection.createPdf(
                commit = false,
                pdf = pdfBehandler!!,
            )
            connection.createMotedeltakerVarselBehandler(
                commit = false,
                motedeltakerBehandlerId = it,
                status = "OK",
                varselType = varselType,
                pdfId = pdfBehandlerId,
                fritekst = fritekstBehandler,
                document = documentBehandler,
            )
        }

        val now = LocalDateTime.now()
        val isDialogmoteTidPassed = moteTidspunkt.isBefore(now)

        if (!isDialogmoteTidPassed) {
            varselService.sendVarsel(
                tidspunktForVarsel = now,
                varselType = varselType,
                moteTidspunkt = moteTidspunkt,
                isDigitalVarselEnabledForArbeidstaker = digitalArbeidstakerVarsling,
                arbeidstakerPersonIdent = arbeidstakerPersonIdent,
                arbeidstakerId = arbeidstakerUuid,
                arbeidstakerbrevId = varselArbeidstakerId,
                narmesteLeder = narmesteLeder,
                virksomhetsbrevId = virksomhetsbrevId,
                virksomhetsPdf = pdfArbeidsgiver,
                virksomhetsnummer = virksomhetsnummer,
                behandlerId = behandlerId,
                behandlerRef = behandlerRef,
                behandlerDocument = documentBehandler,
                behandlerPdf = pdfBehandler,
                behandlerbrevId = behandlerVarselIdPair?.second,
                behandlerbrevParentId = behandlerParentVarselId,
                behandlerInnkallingUuid = behandlerInnkallingUuid
            )
        }
    }

    fun overtaMoter(veilederIdent: String, dialogmoter: List<Dialogmote>) {
        database.connection.use { connection ->
            dialogmoter.forEach { dialogmote ->
                connection.updateMoteTildeltVeileder(
                    commit = false,
                    moteId = dialogmote.id,
                    veilederId = veilederIdent
                )
            }
            connection.commit()
        }
    }

    fun mellomlagreReferat(
        dialogmote: Dialogmote,
        opprettetAv: String,
        referat: NewReferatDTO,
    ) {
        if (dialogmote.status == DialogmoteStatus.AVLYST) {
            throw ConflictException("Failed to mellomlagre referat Dialogmote, already Avlyst")
        }

        database.connection.use { connection ->

            if (dialogmote.tildeltVeilederIdent != opprettetAv) {
                connection.updateMoteTildeltVeileder(
                    commit = false,
                    moteId = dialogmote.id,
                    veilederId = opprettetAv,
                )
            }
            val newReferat = referat.toNewReferat(
                moteId = dialogmote.id,
                ferdigstilt = false,
            )
            val existingReferat = dialogmote.referatList.firstOrNull()
            if (existingReferat == null || existingReferat.ferdigstilt) {
                connection.createNewReferat(
                    commit = false,
                    newReferat = newReferat,
                    pdfId = null,
                    digitalt = true,
                    sendAltinn = false,
                )
            } else {
                connection.updateReferat(
                    commit = false,
                    referat = existingReferat,
                    newReferat = newReferat,
                    pdfId = null,
                    digitalt = true,
                    sendAltinn = false,
                )
            }

            if (dialogmote.behandler != null) {
                connection.updateMotedeltakerBehandler(
                    deltakerId = dialogmote.behandler.id,
                    deltatt = referat.behandlerDeltatt ?: true,
                    mottarReferat = referat.behandlerMottarReferat ?: true,
                )
            }
            connection.commit()
        }
    }

    suspend fun ferdigstillMote(
        callId: String,
        dialogmote: Dialogmote,
        opprettetAv: String,
        referat: NewReferatDTO,
        token: String,
    ) {
        if (dialogmote.status == DialogmoteStatus.FERDIGSTILT) {
            throw ConflictException("Failed to Ferdigstille Dialogmote, already Ferdigstilt")
        }
        if (dialogmote.status == DialogmoteStatus.AVLYST) {
            throw ConflictException("Failed to Ferdigstille Dialogmote, already Avlyst")
        }

        val virksomhetsnummer = dialogmote.arbeidsgiver.virksomhetsnummer

        val narmesteLeder = narmesteLederClient.activeLeder(
            personIdentNumber = dialogmote.arbeidstaker.personIdent,
            virksomhetsnummer = virksomhetsnummer,
            callId = callId,
            token = token,
        )

        val pdfReferat = pdfGenClient.pdfReferat(
            callId = callId,
            documentComponentDTOList = referat.document,
        ) ?: throw RuntimeException("Failed to request PDF - Referat")

        val now = LocalDateTime.now()

        val digitalVarsling = isDigitalVarselEnabled(
            personIdentNumber = dialogmote.arbeidstaker.personIdent,
            token = token,
            callId = callId,
        )

        database.connection.use { connection ->

            if (dialogmote.tildeltVeilederIdent != opprettetAv) {
                connection.updateMoteTildeltVeileder(
                    commit = false,
                    moteId = dialogmote.id,
                    veilederId = opprettetAv,
                )
            }
            updateMoteStatus(
                callId = callId,
                connection = connection,
                dialogmoteId = dialogmote.id,
                isBehandlerMotedeltaker = dialogmote.behandler != null,
                newDialogmoteStatus = DialogmoteStatus.FERDIGSTILT,
                opprettetAv = opprettetAv,
                personIdentNumber = dialogmote.arbeidstaker.personIdent,
                token = token,
            )
            val (pdfId, _) = connection.createPdf(
                commit = false,
                pdf = pdfReferat,
            )
            val newReferat = referat.toNewReferat(
                moteId = dialogmote.id,
                ferdigstilt = true,
            )
            val (_, referatUuid) = if (dialogmote.referatList.isEmpty()) {
                connection.createNewReferat(
                    commit = false,
                    newReferat = newReferat,
                    pdfId = pdfId,
                    digitalt = digitalVarsling,
                    sendAltinn = narmesteLeder == null,
                )
            } else {
                val existingReferat = dialogmote.referatList.first()
                if (existingReferat.ferdigstilt) {
                    throw ConflictException("Failed to Ferdigstille referat for Dialogmote, referat already Ferdigstilt")
                }
                connection.updateReferat(
                    commit = false,
                    referat = existingReferat,
                    newReferat = newReferat,
                    pdfId = pdfId,
                    digitalt = digitalVarsling,
                    sendAltinn = narmesteLeder == null,
                )
            }
            val behandler = dialogmote.behandler
            if (behandler != null) {
                connection.updateMotedeltakerBehandler(
                    deltakerId = behandler.id,
                    deltatt = referat.behandlerDeltatt ?: true,
                    mottarReferat = referat.behandlerMottarReferat ?: true,
                )
            }

            varselService.sendVarsel(
                tidspunktForVarsel = now,
                varselType = MotedeltakerVarselType.REFERAT,
                moteTidspunkt = dialogmote.tidStedList.latest()!!.tid,
                isDigitalVarselEnabledForArbeidstaker = digitalVarsling,
                arbeidstakerPersonIdent = dialogmote.arbeidstaker.personIdent,
                arbeidstakerId = dialogmote.arbeidstaker.uuid,
                arbeidstakerbrevId = referatUuid,
                narmesteLeder = narmesteLeder,
                virksomhetsbrevId = referatUuid,
                virksomhetsPdf = pdfReferat,
                virksomhetsnummer = virksomhetsnummer,
                skalVarsleBehandler = referat.behandlerMottarReferat ?: true,
                behandlerId = behandler?.id,
                behandlerRef = behandler?.behandlerRef,
                behandlerDocument = referat.document,
                behandlerPdf = pdfReferat,
                behandlerbrevId = referatUuid,
                behandlerbrevParentId = behandler?.findParentVarselId(),
                behandlerInnkallingUuid = behandler?.findInnkallingVarselUuid(),
            )

            connection.commit()
        }
    }

    suspend fun endreFerdigstiltReferat(
        callId: String,
        dialogmote: Dialogmote,
        opprettetAv: String,
        referat: NewReferatDTO,
        token: String,
    ) {
        if (dialogmote.status != DialogmoteStatus.FERDIGSTILT) {
            throw ConflictException("Failed to Endre Ferdigstilt Dialogmote, not Ferdigstilt")
        }

        val virksomhetsnummer = dialogmote.arbeidsgiver.virksomhetsnummer

        val narmesteLeder = narmesteLederClient.activeLeder(
            personIdentNumber = dialogmote.arbeidstaker.personIdent,
            virksomhetsnummer = virksomhetsnummer,
            callId = callId,
            token = token,
        )

        val pdfReferat = pdfGenClient.pdfReferat(
            callId = callId,
            documentComponentDTOList = referat.document,
        ) ?: throw RuntimeException("Failed to request PDF - Referat")

        val digitalVarsling = isDigitalVarselEnabled(
            personIdentNumber = dialogmote.arbeidstaker.personIdent,
            token = token,
            callId = callId,
        )

        val newReferat = referat.toNewReferat(
            moteId = dialogmote.id,
            ferdigstilt = true,
        )
        val existingReferat = dialogmote.referatList.firstOrNull()
            ?: throw RuntimeException("Ferdigstilt mote ${dialogmote.id} does not have referat")

        database.connection.use { connection ->

            if (dialogmote.tildeltVeilederIdent != opprettetAv) {
                connection.updateMoteTildeltVeileder(
                    commit = false,
                    moteId = dialogmote.id,
                    veilederId = opprettetAv,
                )
            }
            val (pdfId, _) = connection.createPdf(
                commit = false,
                pdf = pdfReferat,
            )

            val (_, referatUuid) = if (existingReferat.ferdigstilt) {
                connection.createNewReferat(
                    commit = false,
                    newReferat = newReferat,
                    pdfId = pdfId,
                    digitalt = digitalVarsling,
                    sendAltinn = narmesteLeder == null,
                )
            } else {
                connection.updateReferat(
                    commit = false,
                    referat = existingReferat,
                    newReferat = newReferat,
                    pdfId = pdfId,
                    digitalt = digitalVarsling,
                    sendAltinn = narmesteLeder == null,
                )
            }
            val behandler = dialogmote.behandler
            if (behandler != null) {
                connection.updateMotedeltakerBehandler(
                    deltakerId = behandler.id,
                    deltatt = referat.behandlerDeltatt ?: true,
                    mottarReferat = referat.behandlerMottarReferat ?: true,
                )
            }

            varselService.sendVarsel(
                tidspunktForVarsel = LocalDateTime.now(),
                varselType = MotedeltakerVarselType.REFERAT,
                moteTidspunkt = dialogmote.tidStedList.latest()!!.tid,
                isDigitalVarselEnabledForArbeidstaker = digitalVarsling,
                arbeidstakerPersonIdent = dialogmote.arbeidstaker.personIdent,
                arbeidstakerId = dialogmote.arbeidstaker.uuid,
                arbeidstakerbrevId = referatUuid,
                narmesteLeder = narmesteLeder,
                virksomhetsbrevId = referatUuid,
                virksomhetsPdf = pdfReferat,
                virksomhetsnummer = virksomhetsnummer,
                skalVarsleBehandler = referat.behandlerMottarReferat ?: true,
                behandlerId = behandler?.id,
                behandlerRef = behandler?.behandlerRef,
                behandlerDocument = referat.document,
                behandlerPdf = pdfReferat,
                behandlerbrevId = referatUuid,
                behandlerbrevParentId = behandler?.findParentVarselId(),
                behandlerInnkallingUuid = behandler?.findInnkallingVarselUuid(),
            )

            connection.commit()
        }
    }

    private suspend fun updateMoteStatus(
        callId: String,
        connection: Connection,
        dialogmoteId: Int,
        isBehandlerMotedeltaker: Boolean,
        newDialogmoteStatus: DialogmoteStatus,
        opprettetAv: String,
        personIdentNumber: PersonIdentNumber,
        token: String,
    ) {
        val tilfelle = oppfolgingstilfelleClient.oppfolgingstilfelle(
            callId = callId,
            personIdentNumber = personIdentNumber,
            token = token,
        ) ?: throw RuntimeException("Cannot update MoteStatusEndring: No Oppfolgingstilfelle was found")

        connection.updateMoteStatus(
            commit = false,
            moteId = dialogmoteId,
            moteStatus = newDialogmoteStatus,
        )
        createMoteStatusEndring(
            connection = connection,
            dialogmoteId = dialogmoteId,
            dialogmoteStatus = newDialogmoteStatus,
            isBehandlerMotedeltaker = isBehandlerMotedeltaker,
            opprettetAv = opprettetAv,
            tilfelle = tilfelle,
        )
    }

    private fun createMoteStatusEndring(
        connection: Connection,
        dialogmoteId: Int,
        dialogmoteStatus: DialogmoteStatus,
        isBehandlerMotedeltaker: Boolean,
        opprettetAv: String,
        tilfelle: Oppfolgingstilfelle,
    ) {
        connection.createMoteStatusEndring(
            commit = false,
            moteId = dialogmoteId,
            opprettetAv = opprettetAv,
            isBehandlerMotedeltaker = isBehandlerMotedeltaker,
            status = dialogmoteStatus,
            tilfelleStart = tilfelle.start,
        )
    }

    private fun getFerdigReferat(
        referatUUID: UUID
    ): Referat? {
        val referat = getReferat(referatUUID)
        return if (referat?.ferdigstilt == true) referat else null
    }

    private fun getReferat(
        referatUUID: UUID
    ): Referat? {
        return database.getReferat(referatUUID).firstOrNull()?.let { pReferat ->
            val andreDeltakere = getAndreDeltakere(pReferat.id)
            val motedeltakerArbeidstakerId = database.getMoteDeltakerArbeidstaker(pReferat.moteId).id
            val motedeltakerArbeidsgiverId = database.getMoteDeltakerArbeidsgiver(pReferat.moteId).id
            pReferat.toReferat(
                andreDeltakere = andreDeltakere,
                motedeltakerArbeidstakerId = motedeltakerArbeidstakerId,
                motedeltakerArbeidsgiverId = motedeltakerArbeidsgiverId
            )
        }
    }

    private fun getReferatForMote(
        moteUUID: UUID
    ): List<Referat> {
        return database.getReferatForMote(moteUUID).map { pReferat ->
            val andreDeltakere = getAndreDeltakere(pReferat.id)
            val motedeltakerArbeidstakerId = database.getMoteDeltakerArbeidstaker(pReferat.moteId).id
            val motedeltakerArbeidsgiverId = database.getMoteDeltakerArbeidsgiver(pReferat.moteId).id

            pReferat.toReferat(
                andreDeltakere = andreDeltakere,
                motedeltakerArbeidstakerId = motedeltakerArbeidstakerId,
                motedeltakerArbeidsgiverId = motedeltakerArbeidsgiverId
            )
        }
    }

    private fun getAndreDeltakere(
        referatId: Int
    ): List<DialogmotedeltakerAnnen> {
        return database.getAndreDeltakereForReferatID(referatId).map { pMotedeltakerAnnen ->
            pMotedeltakerAnnen.toDialogmoteDeltakerAnnen()
        }
    }

    fun getArbeidstakerBrevFromUuid(
        brevUuid: UUID
    ): ArbeidstakerBrev {
        val motedeltakerArbeidstakerVarsel = dialogmotedeltakerService.getDialogmoteDeltakerArbeidstakerVarselList(
            uuid = brevUuid
        ).firstOrNull()

        val ferdigreferat = getFerdigReferat(
            referatUUID = brevUuid
        )
        val noBrevFound = (motedeltakerArbeidstakerVarsel == null && ferdigreferat == null)
        if (noBrevFound) {
            throw IllegalArgumentException("No Brev found for arbeidstaker with uuid=$brevUuid")
        }
        return motedeltakerArbeidstakerVarsel ?: ferdigreferat!!
    }

    fun getNarmesteLederBrevFromUuid(brevUuid: UUID): NarmesteLederBrev {
        val moteDeltagerArbeidsgiverVarsel =
            dialogmotedeltakerService.getDialogmoteDeltakerArbeidsgiverVarselList(uuid = brevUuid).firstOrNull()

        val ferdigReferat = getFerdigReferat(
            referatUUID = brevUuid
        )
        val noBrevFound = (moteDeltagerArbeidsgiverVarsel == null && ferdigReferat == null)
        if (noBrevFound) {
            throw IllegalArgumentException("No Brev found for arbeidsgiver with uuid=$brevUuid")
        }

        return moteDeltagerArbeidsgiverVarsel ?: ferdigReferat!!
    }

    private suspend fun isDigitalVarselEnabled(
        personIdentNumber: PersonIdentNumber,
        token: String,
        callId: String,
    ): Boolean {
        return kontaktinformasjonClient.isDigitalVarselEnabled(
            personIdentNumber = personIdentNumber,
            token = token,
            callId = callId,
        )
    }
}
