package no.nav.syfo.dialogmote

import no.nav.syfo.application.api.authentication.getNAVIdentFromToken
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.brev.arbeidstaker.ArbeidstakerVarselService
import no.nav.syfo.brev.narmesteleder.NarmesteLederVarselService
import no.nav.syfo.client.behandlendeenhet.BehandlendeEnhetClient
import no.nav.syfo.client.narmesteleder.NarmesteLederClient
import no.nav.syfo.client.narmesteleder.NarmesteLederDTO
import no.nav.syfo.client.pdfgen.PdfGenClient
import no.nav.syfo.client.person.kontaktinfo.KontaktinformasjonClient
import no.nav.syfo.client.person.oppfolgingstilfelle.OppfolgingstilfelleClient
import no.nav.syfo.dialogmote.api.domain.AvlysDialogmoteDTO
import no.nav.syfo.dialogmote.api.domain.EndreTidStedDialogmoteDTO
import no.nav.syfo.dialogmote.api.domain.NewDialogmoteDTO
import no.nav.syfo.dialogmote.api.domain.NewReferatDTO
import no.nav.syfo.dialogmote.api.domain.toNewDialogmote
import no.nav.syfo.dialogmote.api.domain.toNewReferat
import no.nav.syfo.dialogmote.database.CreatedDialogmoteIdentifiers
import no.nav.syfo.dialogmote.database.createMoteStatusEndring
import no.nav.syfo.dialogmote.database.createMotedeltakerVarselArbeidsgiver
import no.nav.syfo.dialogmote.database.createMotedeltakerVarselArbeidstaker
import no.nav.syfo.dialogmote.database.createNewDialogmoteWithReferences
import no.nav.syfo.dialogmote.database.createNewReferat
import no.nav.syfo.dialogmote.database.domain.PDialogmote
import no.nav.syfo.dialogmote.database.domain.toDialogmote
import no.nav.syfo.dialogmote.database.domain.toDialogmoteDeltakerAnnen
import no.nav.syfo.dialogmote.database.domain.toDialogmoteTidSted
import no.nav.syfo.dialogmote.database.domain.toReferat
import no.nav.syfo.dialogmote.database.getAndreDeltakereForReferatID
import no.nav.syfo.dialogmote.database.getDialogmote
import no.nav.syfo.dialogmote.database.getDialogmoteList
import no.nav.syfo.dialogmote.database.getMoteDeltakerArbeidsgiver
import no.nav.syfo.dialogmote.database.getMoteDeltakerArbeidstaker
import no.nav.syfo.dialogmote.database.getReferat
import no.nav.syfo.dialogmote.database.getReferatForMote
import no.nav.syfo.dialogmote.database.getTidSted
import no.nav.syfo.dialogmote.database.updateMoteStatus
import no.nav.syfo.dialogmote.database.updateMoteTidSted
import no.nav.syfo.dialogmote.database.updateMoteTildeltVeileder
import no.nav.syfo.dialogmote.domain.ArbeidstakerBrev
import no.nav.syfo.dialogmote.domain.Dialogmote
import no.nav.syfo.dialogmote.domain.DialogmoteStatus
import no.nav.syfo.dialogmote.domain.DialogmoteTidSted
import no.nav.syfo.dialogmote.domain.DialogmotedeltakerAnnen
import no.nav.syfo.dialogmote.domain.DocumentComponentDTO
import no.nav.syfo.dialogmote.domain.MotedeltakerVarselType
import no.nav.syfo.dialogmote.domain.NarmesteLederBrev
import no.nav.syfo.dialogmote.domain.Referat
import no.nav.syfo.dialogmote.domain.anyUnfinished
import no.nav.syfo.dialogmote.domain.latest
import no.nav.syfo.domain.EnhetNr
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.Virksomhetsnummer
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.time.LocalDateTime
import java.util.UUID

class DialogmoteService(
    private val database: DatabaseInterface,
    private val arbeidstakerVarselService: ArbeidstakerVarselService,
    private val narmesteLederVarselService: NarmesteLederVarselService,
    private val dialogmotedeltakerService: DialogmotedeltakerService,
    private val behandlendeEnhetClient: BehandlendeEnhetClient,
    private val narmesteLederClient: NarmesteLederClient,
    private val oppfolgingstilfelleClient: OppfolgingstilfelleClient,
    private val pdfGenClient: PdfGenClient,
    private val kontaktinformasjonClient: KontaktinformasjonClient,
    private val allowVarselMedFysiskBrev: Boolean,
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

    private fun extendDialogmoteRelations(
        pDialogmote: PDialogmote,
    ): Dialogmote {
        val motedeltakerArbeidstaker = dialogmotedeltakerService.getDialogmoteDeltakerArbeidstaker(pDialogmote.id)
        val motedeltakerArbeidsgiver = dialogmotedeltakerService.getDialogmoteDeltakerArbeidsgiver(pDialogmote.id)
        val motedeltakerBehandler = dialogmotedeltakerService.getDialogmoteDeltakerBehandler(pDialogmote.id)
        val dialogmoteTidStedList = getDialogmoteTidStedList(pDialogmote.id)
        val referat = getReferatForMote(pDialogmote.uuid)
        return pDialogmote.toDialogmote(
            dialogmotedeltakerArbeidstaker = motedeltakerArbeidstaker,
            dialogmotedeltakerArbeidsgiver = motedeltakerArbeidsgiver,
            dialogmotedeltakerBehandler = motedeltakerBehandler,
            dialogmoteTidStedList = dialogmoteTidStedList,
            referat = referat,
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
    ): Boolean {
        val personIdentNumber = PersonIdentNumber(newDialogmoteDTO.arbeidstaker.personIdent)
        val virksomhetsnummer = Virksomhetsnummer(newDialogmoteDTO.arbeidsgiver.virksomhetsnummer)

        val anyUnfinishedDialogmote =
            getDialogmoteList(personIdentNumber).filter {
                it.arbeidsgiver.virksomhetsnummer == virksomhetsnummer
            }.anyUnfinished()

        if (anyUnfinishedDialogmote) {
            throw IllegalStateException("Denied access to create Dialogmote: unfinished Dialogmote exists for PersonIdent")
        }

        val narmesteLeder = narmesteLederClient.activeLeder(
            personIdentNumber = personIdentNumber,
            virksomhetsnummer = virksomhetsnummer,
            callId = callId
        )
        return if (narmesteLeder == null) {
            log.warn("Denied access to Dialogmoter: No NarmesteLeder was found for person")
            false
        } else {
            val behandlendeEnhet = behandlendeEnhetClient.getEnhet(
                callId = callId,
                personIdentNumber = personIdentNumber,
                token = token,
            ) ?: throw RuntimeException("Failed to request BehandlendeEnhet of Person")

            val newDialogmote = newDialogmoteDTO.toNewDialogmote(
                requestByNAVIdent = getNAVIdentFromToken(token),
                narmesteLeder = narmesteLeder,
                navEnhet = EnhetNr(behandlendeEnhet.enhetId),
            )

            val pdfInnkallingArbeidstaker = pdfGenClient.pdfInnkallingArbeidstaker(
                callId = callId,
                documentComponentDTOList = newDialogmoteDTO.arbeidstaker.innkalling,
            ) ?: throw RuntimeException("Failed to request PDF - Innkalling Arbeidstaker")

            val pdfInnkallingArbeidsgiver = pdfGenClient.pdfInnkallingArbeidsgiver(
                callId = callId,
                documentComponentDTOList = newDialogmoteDTO.arbeidsgiver.innkalling,
            ) ?: throw RuntimeException("Failed to request PDF - Innkalling Arbeidsgiver")

            val createdDialogmoteIdentifiers: CreatedDialogmoteIdentifiers

            val digitalVarsling = allowVarselMedFysiskBrevDisabledOrCheckDigitalVarsling(
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
                    callId = callId,
                    connection = connection,
                    dialogmoteId = createdDialogmoteIdentifiers.dialogmoteIdPair.first,
                    dialogmoteStatus = newDialogmote.status,
                    opprettetAv = newDialogmote.opprettetAv,
                    personIdentNumber = newDialogmote.arbeidstaker.personIdent,
                    token = token,
                )
                createAndSendVarsel(
                    connection = connection,
                    arbeidstakerId = createdDialogmoteIdentifiers.motedeltakerArbeidstakerIdList.first,
                    arbeidstakerUuid = createdDialogmoteIdentifiers.motedeltakerArbeidstakerIdList.second,
                    arbeidsgiverId = createdDialogmoteIdentifiers.motedeltakerArbeidsgiverIdList.first,
                    arbeidstakerPersonIdent = newDialogmote.arbeidstaker.personIdent,
                    pdfArbeidstaker = pdfInnkallingArbeidstaker,
                    pdfArbeidsgiver = pdfInnkallingArbeidsgiver,
                    narmesteLeder = narmesteLeder,
                    varselType = MotedeltakerVarselType.INNKALT,
                    fritekstArbeidstaker = newDialogmote.arbeidstaker.fritekstInnkalling.orEmpty(),
                    fritekstArbeidsgiver = newDialogmote.arbeidsgiver.fritekstInnkalling.orEmpty(),
                    documentArbeidstaker = newDialogmoteDTO.arbeidstaker.innkalling,
                    documentArbeidsgiver = newDialogmoteDTO.arbeidsgiver.innkalling,
                    moteTidspunkt = newDialogmoteDTO.tidSted.tid,
                    digitalArbeidstakerVarsling = digitalVarsling,
                )

                connection.commit()
            }
            true
        }
    }

    suspend fun avlysMoteinnkalling(
        callId: String,
        dialogmote: Dialogmote,
        avlysDialogmote: AvlysDialogmoteDTO,
        token: String,
    ): Boolean {
        if (dialogmote.status == DialogmoteStatus.FERDIGSTILT) {
            throw RuntimeException("Failed to Avlys Dialogmote: already Ferdigstilt")
        }
        if (dialogmote.status == DialogmoteStatus.AVLYST) {
            throw RuntimeException("Failed to Avlys Dialogmote: already Avlyst")
        }
        val pdfAvlysningArbeidstaker = pdfGenClient.pdfAvlysningArbeidstaker(
            callId = callId,
            documentComponentDTOList = avlysDialogmote.arbeidstaker.avlysning,
        ) ?: throw RuntimeException("Failed to request PDF - Avlysning Arbeidstaker")

        val pdfAvlysningArbeidsgiver = pdfGenClient.pdfAvlysningArbeidsgiver(
            callId = callId,
            documentComponentDTOList = avlysDialogmote.arbeidsgiver.avlysning,
        ) ?: throw RuntimeException("Failed to request PDF - Avlysning Arbeidsgiver")

        val narmesteLeder = narmesteLederClient.activeLeder(
            personIdentNumber = dialogmote.arbeidstaker.personIdent,
            virksomhetsnummer = dialogmote.arbeidsgiver.virksomhetsnummer,
            callId = callId
        )
        return if (narmesteLeder == null) {
            log.warn("Denied access to Dialogmoter: No NarmesteLeder was found for person")
            false
        } else {
            val digitalVarsling = allowVarselMedFysiskBrevDisabledOrCheckDigitalVarsling(
                personIdentNumber = dialogmote.arbeidstaker.personIdent,
                token = token,
                callId = callId,
            )

            database.connection.use { connection ->
                updateMoteStatus(
                    callId = callId,
                    connection = connection,
                    dialogmoteId = dialogmote.id,
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
                    arbeidstakerPersonIdent = dialogmote.arbeidstaker.personIdent,
                    pdfArbeidstaker = pdfAvlysningArbeidstaker,
                    pdfArbeidsgiver = pdfAvlysningArbeidsgiver,
                    narmesteLeder = narmesteLeder,
                    varselType = MotedeltakerVarselType.AVLYST,
                    fritekstArbeidstaker = avlysDialogmote.arbeidstaker.begrunnelse,
                    fritekstArbeidsgiver = avlysDialogmote.arbeidsgiver.begrunnelse,
                    documentArbeidstaker = avlysDialogmote.arbeidstaker.avlysning,
                    documentArbeidsgiver = avlysDialogmote.arbeidsgiver.avlysning,
                    moteTidspunkt = dialogmote.tidStedList.latest()!!.tid,
                    digitalArbeidstakerVarsling = digitalVarsling,
                )
                connection.commit()
            }
            true
        }
    }

    suspend fun nyttMoteinnkallingTidSted(
        callId: String,
        dialogmote: Dialogmote,
        endreDialogmoteTidSted: EndreTidStedDialogmoteDTO,
        token: String,
    ): Boolean {
        if (dialogmote.status == DialogmoteStatus.FERDIGSTILT) {
            throw RuntimeException("Failed to change tid/sted, already Ferdigstilt")
        }
        if (dialogmote.status == DialogmoteStatus.AVLYST) {
            throw RuntimeException("Failed to change tid/sted, already Avlyst")
        }
        val pdfEndringArbeidstaker = pdfGenClient.pdfEndringTidStedArbeidstaker(
            callId = callId,
            documentComponentDTOList = endreDialogmoteTidSted.arbeidstaker.endringsdokument
        ) ?: throw RuntimeException("Failed to request PDF - EndringTidSted Arbeidstaker")

        val pdfEndringArbeidsgiver = pdfGenClient.pdfEndringTidStedArbeidsgiver(
            callId = callId,
            documentComponentDTOList = endreDialogmoteTidSted.arbeidsgiver.endringsdokument
        ) ?: throw RuntimeException("Failed to request PDF - EndringTidSted Arbeidsgiver")

        val narmesteLeder = narmesteLederClient.activeLeder(
            personIdentNumber = dialogmote.arbeidstaker.personIdent,
            virksomhetsnummer = dialogmote.arbeidsgiver.virksomhetsnummer,
            callId = callId
        )

        return if (narmesteLeder == null) {
            log.warn("Denied access to Dialogmoter: No NarmesteLeder was found for person")
            false
        } else {
            val digitalVarsling = allowVarselMedFysiskBrevDisabledOrCheckDigitalVarsling(
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
                    arbeidstakerPersonIdent = dialogmote.arbeidstaker.personIdent,
                    pdfArbeidstaker = pdfEndringArbeidstaker,
                    pdfArbeidsgiver = pdfEndringArbeidsgiver,
                    narmesteLeder = narmesteLeder,
                    varselType = MotedeltakerVarselType.NYTT_TID_STED,
                    fritekstArbeidstaker = endreDialogmoteTidSted.arbeidstaker.begrunnelse,
                    fritekstArbeidsgiver = endreDialogmoteTidSted.arbeidsgiver.begrunnelse,
                    documentArbeidstaker = endreDialogmoteTidSted.arbeidstaker.endringsdokument,
                    documentArbeidsgiver = endreDialogmoteTidSted.arbeidsgiver.endringsdokument,
                    moteTidspunkt = endreDialogmoteTidSted.tid,
                    digitalArbeidstakerVarsling = digitalVarsling,
                )

                connection.commit()
            }
            true
        }
    }

    private fun createAndSendVarsel(
        connection: Connection,
        arbeidstakerUuid: UUID,
        arbeidstakerId: Int,
        arbeidsgiverId: Int,
        arbeidstakerPersonIdent: PersonIdentNumber,
        pdfArbeidstaker: ByteArray,
        pdfArbeidsgiver: ByteArray,
        narmesteLeder: NarmesteLederDTO,
        varselType: MotedeltakerVarselType,
        fritekstArbeidstaker: String = "",
        fritekstArbeidsgiver: String = "",
        documentArbeidstaker: List<DocumentComponentDTO> = emptyList(),
        documentArbeidsgiver: List<DocumentComponentDTO> = emptyList(),
        moteTidspunkt: LocalDateTime,
        digitalArbeidstakerVarsling: Boolean,
    ) {
        val (_, varselArbeidstakerId) = connection.createMotedeltakerVarselArbeidstaker(
            commit = false,
            motedeltakerArbeidstakerId = arbeidstakerId,
            status = "OK",
            varselType = varselType,
            digitalt = digitalArbeidstakerVarsling,
            pdf = pdfArbeidstaker,
            fritekst = fritekstArbeidstaker,
            document = documentArbeidstaker,
        )
        connection.createMotedeltakerVarselArbeidsgiver(
            commit = false,
            motedeltakerArbeidsgiverId = arbeidsgiverId,
            status = "OK",
            varselType = varselType,
            pdf = pdfArbeidsgiver,
            fritekst = fritekstArbeidsgiver,
            document = documentArbeidsgiver,
        )

        val now = LocalDateTime.now()
        val isDialogmoteTidPassed = moteTidspunkt.isBefore(now)

        if (!isDialogmoteTidPassed && digitalArbeidstakerVarsling) {
            arbeidstakerVarselService.sendVarsel(
                createdAt = now,
                personIdent = arbeidstakerPersonIdent,
                type = varselType,
                motedeltakerArbeidstakerUuid = arbeidstakerUuid,
                varselUuid = varselArbeidstakerId,
            )
        }

        if (!isDialogmoteTidPassed) {
            narmesteLederVarselService.sendVarsel(
                createdAt = now,
                moteTidspunkt = moteTidspunkt,
                narmesteLeder = narmesteLeder,
                varseltype = varselType
            )
        }
    }

    fun overtaMoter(veilederIdent: String, dialogmoter: List<Dialogmote>): Boolean {
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
        return true
    }

    suspend fun ferdigstillMote(
        callId: String,
        dialogmote: Dialogmote,
        opprettetAv: String,
        referat: NewReferatDTO,
        token: String,
    ): Boolean {
        if (dialogmote.status == DialogmoteStatus.FERDIGSTILT) {
            throw RuntimeException("Failed to Ferdigstille Dialogmote, already Ferdigstilt")
        }
        if (dialogmote.status == DialogmoteStatus.AVLYST) {
            throw RuntimeException("Failed to Ferdigstille Dialogmote, already Avlyst")
        }

        val narmesteLeder = narmesteLederClient.activeLeder(
            personIdentNumber = dialogmote.arbeidstaker.personIdent,
            virksomhetsnummer = dialogmote.arbeidsgiver.virksomhetsnummer,
            callId = callId
        )
        if (narmesteLeder == null) {
            log.warn("Denied access to Dialogmoter: No NarmesteLeder was found for person")
            throw RuntimeException("Denied access to Dialogmoter: No NarmesteLeder was found for person")
        }

        val pdfReferat = pdfGenClient.pdfReferat(
            callId = callId,
            documentComponentDTOList = referat.document,
        ) ?: throw RuntimeException("Failed to request PDF - Referat")

        val now = LocalDateTime.now()

        val digitalVarsling = allowVarselMedFysiskBrevDisabledOrCheckDigitalVarsling(
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
                newDialogmoteStatus = DialogmoteStatus.FERDIGSTILT,
                opprettetAv = opprettetAv,
                personIdentNumber = dialogmote.arbeidstaker.personIdent,
                token = token,
            )
            val (_, referatUuid) = connection.createNewReferat(
                commit = false,
                newReferat = referat.toNewReferat(dialogmote.id),
                pdf = pdfReferat,
                digitalt = digitalVarsling,
            )
            if (digitalVarsling) {
                arbeidstakerVarselService.sendVarsel(
                    createdAt = now,
                    personIdent = dialogmote.arbeidstaker.personIdent,
                    type = MotedeltakerVarselType.REFERAT,
                    motedeltakerArbeidstakerUuid = dialogmote.arbeidstaker.uuid,
                    varselUuid = referatUuid,
                )
            }

            narmesteLederVarselService.sendVarsel(
                createdAt = now,
                moteTidspunkt = dialogmote.tidStedList.latest()!!.tid,
                narmesteLeder = narmesteLeder,
                varseltype = MotedeltakerVarselType.REFERAT,
            )
            connection.commit()
        }
        return true
    }

    private suspend fun updateMoteStatus(
        callId: String,
        connection: Connection,
        dialogmoteId: Int,
        newDialogmoteStatus: DialogmoteStatus,
        opprettetAv: String,
        personIdentNumber: PersonIdentNumber,
        token: String,
    ) {
        connection.updateMoteStatus(
            commit = false,
            moteId = dialogmoteId,
            moteStatus = newDialogmoteStatus,
        )
        createMoteStatusEndring(
            callId = callId,
            connection = connection,
            dialogmoteId = dialogmoteId,
            dialogmoteStatus = newDialogmoteStatus,
            opprettetAv = opprettetAv,
            personIdentNumber = personIdentNumber,
            token = token,
        )
    }

    private suspend fun createMoteStatusEndring(
        callId: String,
        connection: Connection,
        dialogmoteId: Int,
        dialogmoteStatus: DialogmoteStatus,
        opprettetAv: String,
        personIdentNumber: PersonIdentNumber,
        token: String,
    ) {
        val tilfelleStart = oppfolgingstilfelleClient.oppfolgingstilfelle(
            callId = callId,
            personIdentNumber = personIdentNumber,
            token = token,
        )?.fom ?: throw RuntimeException("Cannot create MoteStatusEndring: No TilfelleStart was found")
        connection.createMoteStatusEndring(
            commit = false,
            moteId = dialogmoteId,
            opprettetAv = opprettetAv,
            status = dialogmoteStatus,
            tilfelleStart = tilfelleStart,
        )
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
    ): Referat? {
        return database.getReferatForMote(moteUUID).firstOrNull()?.let { pReferat ->
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

        val referat = getReferat(
            referatUUID = brevUuid
        )

        if (motedeltakerArbeidstakerVarsel == null && referat == null) {
            throw IllegalArgumentException("No Brev found for arbeidstaker with uuid=$brevUuid")
        }
        return motedeltakerArbeidstakerVarsel ?: referat!!
    }

    fun getNarmesteLederBrevFromUuid(brevUuid: UUID): NarmesteLederBrev {
        val moteDeltagerArbeidsgiverVarsel =
            dialogmotedeltakerService.getDialogmoteDeltakerArbeidsgiverVarselList(uuid = brevUuid).firstOrNull()

        val referat = getReferat(referatUUID = brevUuid)

        if (moteDeltagerArbeidsgiverVarsel == null && referat == null) {
            throw IllegalArgumentException("No Brev found for arbeidsgiver with uuid=$brevUuid")
        }

        return moteDeltagerArbeidsgiverVarsel ?: referat!!
    }

    private suspend fun allowVarselMedFysiskBrevDisabledOrCheckDigitalVarsling(
        personIdentNumber: PersonIdentNumber,
        token: String,
        callId: String,
    ): Boolean {
        return !allowVarselMedFysiskBrev || kontaktinformasjonClient.isDigitalVarselEnabled(
            personIdentNumber = personIdentNumber,
            token = token,
            callId = callId,
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(DialogmoteService::class.java)
    }
}
