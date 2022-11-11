package no.nav.syfo.dialogmote.api.v2

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import no.altinn.schemas.services.intermediary.receipt._2009._10.ReceiptExternal
import no.altinn.schemas.services.intermediary.receipt._2009._10.ReceiptStatusEnum
import no.altinn.services.serviceengine.correspondence._2009._10.ICorrespondenceAgencyExternalBasic
import no.nav.brukernotifikasjon.schemas.input.NokkelInput
import no.nav.brukernotifikasjon.schemas.input.OppgaveInput
import no.nav.syfo.brev.arbeidstaker.brukernotifikasjon.BrukernotifikasjonProducer
import no.nav.syfo.brev.behandler.BehandlerVarselService
import no.nav.syfo.brev.behandler.kafka.BehandlerDialogmeldingProducer
import no.nav.syfo.brev.behandler.kafka.KafkaBehandlerDialogmeldingDTO
import no.nav.syfo.brev.esyfovarsel.EsyfovarselHendelseType
import no.nav.syfo.brev.esyfovarsel.EsyfovarselNarmesteLederHendelse
import no.nav.syfo.brev.esyfovarsel.EsyfovarselProducer
import no.nav.syfo.client.oppfolgingstilfelle.toLatestOppfolgingstilfelle
import no.nav.syfo.dialogmote.api.domain.DialogmoteDTO
import no.nav.syfo.dialogmote.database.getMoteStatusEndretNotPublished
import no.nav.syfo.dialogmote.domain.DialogmeldingKode
import no.nav.syfo.dialogmote.domain.DialogmeldingType
import no.nav.syfo.dialogmote.domain.DialogmoteStatus
import no.nav.syfo.dialogmote.domain.DocumentComponentType
import no.nav.syfo.dialogmote.domain.MotedeltakerVarselType
import no.nav.syfo.dialogmote.domain.serialize
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_ADRESSEBESKYTTET
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_INACTIVE_OPPFOLGINGSTILFELLE
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_NO_BEHANDLENDE_ENHET
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_NO_OPPFOLGINGSTILFELLE
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_VEILEDER_NO_ACCESS
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER
import no.nav.syfo.testhelper.UserConstants.ENHET_NR
import no.nav.syfo.testhelper.UserConstants.VEILEDER_IDENT
import no.nav.syfo.testhelper.UserConstants.VIRKSOMHETSNUMMER_HAS_NARMESTELEDER
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generateJWTNavIdent
import no.nav.syfo.testhelper.generator.generateNewDialogmoteDTO
import no.nav.syfo.testhelper.generator.generateNewDialogmoteDTOWithBehandler
import no.nav.syfo.testhelper.generator.generateNewDialogmoteDTOWithMissingValues
import no.nav.syfo.testhelper.mock.oppfolgingstilfellePersonDTO
import no.nav.syfo.testhelper.testApiModule
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import no.nav.syfo.util.bearerHeader
import no.nav.syfo.util.configuredJacksonMapper
import org.amshove.kluent.shouldBeBefore
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldContain
import org.amshove.kluent.shouldNotBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class PostDialogmoteApiV2Spek : Spek({
    val objectMapper: ObjectMapper = configuredJacksonMapper()

    describe(PostDialogmoteApiV2Spek::class.java.simpleName) {

        with(TestApplicationEngine()) {
            start()

            val externalMockEnvironment = ExternalMockEnvironment.getInstance()
            val database = externalMockEnvironment.database

            val brukernotifikasjonProducer = mockk<BrukernotifikasjonProducer>()
            val behandlerDialogmeldingProducer = mockk<BehandlerDialogmeldingProducer>()

            val esyfovarselHendelse = EsyfovarselNarmesteLederHendelse(
                type = EsyfovarselHendelseType.NL_DIALOGMOTE_INNKALT,
                data = null,
                narmesteLederFnr = "98765432101",
                narmesteLederNavn = "narmesteLederNavn",
                arbeidstakerFnr = "12345678912",
                orgnummer = "934567897"
            )
            val esyfovarselProducerMock = mockk<EsyfovarselProducer>(relaxed = true)
            justRun { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }

            val altinnMock = mockk<ICorrespondenceAgencyExternalBasic>()

            val behandlerVarselService = BehandlerVarselService(
                database = database,
                behandlerDialogmeldingProducer = behandlerDialogmeldingProducer,
            )

            application.testApiModule(
                externalMockEnvironment = externalMockEnvironment,
                behandlerVarselService = behandlerVarselService,
                brukernotifikasjonProducer = brukernotifikasjonProducer,
                altinnMock = altinnMock,
                esyfovarselProducer = esyfovarselProducerMock
            )

            val urlMote = "$dialogmoteApiV2Basepath/$dialogmoteApiPersonIdentUrlPath"

            beforeGroup {
                database.dropData()
            }

            describe("Create Dialogmote for PersonIdent payload") {
                val validToken = generateJWTNavIdent(
                    externalMockEnvironment.environment.aadAppClient,
                    externalMockEnvironment.wellKnownVeilederV2.issuer,
                    VEILEDER_IDENT,
                )

                beforeEachTest {
                    val altinnResponse = ReceiptExternal()
                    altinnResponse.receiptStatusCode = ReceiptStatusEnum.OK

                    clearMocks(brukernotifikasjonProducer)
                    justRun { brukernotifikasjonProducer.sendOppgave(any(), any()) }
                    clearMocks(behandlerDialogmeldingProducer)
                    justRun { behandlerDialogmeldingProducer.sendDialogmelding(any()) }
                    clearMocks(altinnMock)
                    clearMocks(esyfovarselProducerMock)
                    justRun { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
                    every {
                        altinnMock.insertCorrespondenceBasicV2(any(), any(), any(), any(), any())
                    } returns altinnResponse
                }

                afterEachTest {
                    database.dropData()
                }

                describe("Happy path") {

                    val urlMoter = "$dialogmoteApiV2Basepath$dialogmoteApiPersonIdentUrlPath"

                    it("should return OK if request is successful") {
                        val moteTidspunkt = LocalDateTime.now().plusDays(30)
                        val newDialogmoteDTO = generateNewDialogmoteDTO(
                            personIdent = ARBEIDSTAKER_FNR,
                            dato = moteTidspunkt,
                        )

                        with(
                            handleRequest(HttpMethod.Post, urlMote) {
                                addHeader(Authorization, bearerHeader(validToken))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                setBody(objectMapper.writeValueAsString(newDialogmoteDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            verify(exactly = 1) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
                            verify(exactly = 1) {
                                altinnMock.insertCorrespondenceBasicV2(
                                    any(),
                                    any(),
                                    any(),
                                    any(),
                                    any()
                                )
                            }
                        }

                        with(
                            handleRequest(HttpMethod.Get, urlMoter) {
                                addHeader(Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)

                            dialogmoteList.size shouldBeEqualTo 1

                            val dialogmoteDTO = dialogmoteList.first()
                            dialogmoteDTO.tildeltEnhet shouldBeEqualTo ENHET_NR.value
                            dialogmoteDTO.tildeltVeilederIdent shouldBeEqualTo VEILEDER_IDENT

                            dialogmoteDTO.arbeidstaker.personIdent shouldBeEqualTo newDialogmoteDTO.arbeidstaker.personIdent
                            dialogmoteDTO.arbeidstaker.varselList.size shouldBeEqualTo 1

                            dialogmoteDTO.behandler shouldBeEqualTo null

                            val arbeidstakerVarselDTO = dialogmoteDTO.arbeidstaker.varselList.first()
                            arbeidstakerVarselDTO.varselType shouldBeEqualTo MotedeltakerVarselType.INNKALT.name
                            arbeidstakerVarselDTO.digitalt shouldBeEqualTo true
                            arbeidstakerVarselDTO.lestDato.shouldBeNull()
                            arbeidstakerVarselDTO.fritekst shouldBeEqualTo "Ipsum lorum arbeidstaker"

                            arbeidstakerVarselDTO.document.size shouldBeEqualTo 5
                            arbeidstakerVarselDTO.document[0].type shouldBeEqualTo DocumentComponentType.PARAGRAPH
                            arbeidstakerVarselDTO.document[0].title shouldBeEqualTo "Tittel innkalling"
                            arbeidstakerVarselDTO.document[0].texts shouldBeEqualTo emptyList()
                            arbeidstakerVarselDTO.document[1].type shouldBeEqualTo DocumentComponentType.PARAGRAPH
                            arbeidstakerVarselDTO.document[1].title shouldBeEqualTo "Møtetid:"
                            arbeidstakerVarselDTO.document[1].texts shouldBeEqualTo listOf("5. mai 2021")
                            arbeidstakerVarselDTO.document[2].type shouldBeEqualTo DocumentComponentType.PARAGRAPH
                            arbeidstakerVarselDTO.document[2].texts shouldBeEqualTo listOf("Brødtekst")
                            arbeidstakerVarselDTO.document[3].type shouldBeEqualTo DocumentComponentType.LINK
                            arbeidstakerVarselDTO.document[3].texts shouldBeEqualTo listOf("https://nav.no/")
                            arbeidstakerVarselDTO.document[4].type shouldBeEqualTo DocumentComponentType.PARAGRAPH
                            arbeidstakerVarselDTO.document[4].texts shouldBeEqualTo listOf(
                                "Vennlig hilsen",
                                "NAV Staden",
                                "Kari Saksbehandler"
                            )

                            dialogmoteDTO.arbeidsgiver.virksomhetsnummer shouldBeEqualTo newDialogmoteDTO.arbeidsgiver.virksomhetsnummer
                            dialogmoteDTO.arbeidsgiver.varselList.size shouldBeEqualTo 1
                            val arbeidsgiverVarselDTO = dialogmoteDTO.arbeidsgiver.varselList.first()
                            arbeidsgiverVarselDTO.varselType shouldBeEqualTo MotedeltakerVarselType.INNKALT.name
                            arbeidsgiverVarselDTO.lestDato.shouldBeNull()
                            arbeidsgiverVarselDTO.fritekst shouldBeEqualTo "Ipsum lorum arbeidsgiver"

                            dialogmoteDTO.sted shouldBeEqualTo newDialogmoteDTO.tidSted.sted
                            dialogmoteDTO.videoLink shouldBeEqualTo "https://meet.google.com/xyz"

                            verify(exactly = 1) { brukernotifikasjonProducer.sendOppgave(any(), any()) }

                            val moteStatusEndretList = database.getMoteStatusEndretNotPublished()
                            moteStatusEndretList.size shouldBeEqualTo 1

                            moteStatusEndretList.first().status.name shouldBeEqualTo dialogmoteDTO.status
                            moteStatusEndretList.first().opprettetAv shouldBeEqualTo VEILEDER_IDENT
                            moteStatusEndretList.first().tilfelleStart shouldBeEqualTo oppfolgingstilfellePersonDTO().toLatestOppfolgingstilfelle()?.start
                        }
                    }

                    it("should return OK if request is successful: optional values missing") {
                        val newDialogmoteDTO = generateNewDialogmoteDTOWithMissingValues(ARBEIDSTAKER_FNR)

                        with(
                            handleRequest(HttpMethod.Post, urlMote) {
                                addHeader(Authorization, bearerHeader(validToken))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                setBody(objectMapper.writeValueAsString(newDialogmoteDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            verify(exactly = 1) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
                            clearMocks(esyfovarselProducerMock)
                        }

                        with(
                            handleRequest(HttpMethod.Get, urlMoter) {
                                addHeader(Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)

                            dialogmoteList.size shouldBeEqualTo 1

                            val dialogmoteDTO = dialogmoteList.first()
                            dialogmoteDTO.tildeltEnhet shouldBeEqualTo ENHET_NR.value
                            dialogmoteDTO.tildeltVeilederIdent shouldBeEqualTo VEILEDER_IDENT

                            dialogmoteDTO.arbeidstaker.personIdent shouldBeEqualTo newDialogmoteDTO.arbeidstaker.personIdent
                            dialogmoteDTO.arbeidstaker.varselList.size shouldBeEqualTo 1

                            dialogmoteDTO.behandler shouldBeEqualTo null

                            val arbeidstakerVarselDTO = dialogmoteDTO.arbeidstaker.varselList.first()
                            arbeidstakerVarselDTO.varselType shouldBeEqualTo MotedeltakerVarselType.INNKALT.name
                            arbeidstakerVarselDTO.digitalt shouldBeEqualTo true
                            arbeidstakerVarselDTO.lestDato.shouldBeNull()
                            arbeidstakerVarselDTO.fritekst shouldBeEqualTo ""

                            dialogmoteDTO.arbeidsgiver.virksomhetsnummer shouldBeEqualTo newDialogmoteDTO.arbeidsgiver.virksomhetsnummer

                            dialogmoteDTO.sted shouldBeEqualTo newDialogmoteDTO.tidSted.sted
                            dialogmoteDTO.videoLink shouldBeEqualTo ""

                            verify(exactly = 1) { brukernotifikasjonProducer.sendOppgave(any(), any()) }
                        }
                    }
                    it("should return OK if request is successful: with behandler") {
                        val newDialogmoteDTO = generateNewDialogmoteDTOWithBehandler(ARBEIDSTAKER_FNR)
                        val createdAt = LocalDateTime.now()
                        with(
                            handleRequest(HttpMethod.Post, urlMote) {
                                addHeader(Authorization, bearerHeader(validToken))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                setBody(objectMapper.writeValueAsString(newDialogmoteDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            verify(exactly = 1) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
                            clearMocks(esyfovarselProducerMock)
                        }

                        with(
                            handleRequest(HttpMethod.Get, urlMoter) {
                                addHeader(Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val dialogmoteList = objectMapper.readValue<List<DialogmoteDTO>>(response.content!!)

                            dialogmoteList.size shouldBeEqualTo 1

                            val dialogmoteDTO = dialogmoteList.first()
                            dialogmoteDTO.tildeltEnhet shouldBeEqualTo ENHET_NR.value
                            dialogmoteDTO.tildeltVeilederIdent shouldBeEqualTo VEILEDER_IDENT

                            dialogmoteDTO.arbeidstaker.personIdent shouldBeEqualTo newDialogmoteDTO.arbeidstaker.personIdent
                            dialogmoteDTO.arbeidsgiver.virksomhetsnummer shouldBeEqualTo newDialogmoteDTO.arbeidsgiver.virksomhetsnummer
                            dialogmoteDTO.behandler shouldNotBeEqualTo null
                            dialogmoteDTO.behandler!!.behandlerRef shouldBeEqualTo newDialogmoteDTO.behandler!!.behandlerRef
                            dialogmoteDTO.behandler!!.behandlerNavn shouldBeEqualTo newDialogmoteDTO.behandler!!.behandlerNavn
                            dialogmoteDTO.behandler!!.behandlerKontor shouldBeEqualTo newDialogmoteDTO.behandler!!.behandlerKontor
                            dialogmoteDTO.behandler!!.personIdent shouldBeEqualTo newDialogmoteDTO.behandler!!.personIdent

                            val behandlerVarselDTO = dialogmoteDTO.behandler!!.varselList.first()
                            behandlerVarselDTO.varselType shouldBeEqualTo MotedeltakerVarselType.INNKALT.name
                            behandlerVarselDTO.fritekst shouldBeEqualTo "Ipsum lorum behandler"

                            dialogmoteDTO.sted shouldBeEqualTo newDialogmoteDTO.tidSted.sted
                            dialogmoteDTO.videoLink shouldBeEqualTo ""

                            val nokkelInputSlot = slot<NokkelInput>()
                            val oppgaveInputSlot = slot<OppgaveInput>()
                            verify(exactly = 1) {
                                brukernotifikasjonProducer.sendOppgave(
                                    capture(nokkelInputSlot),
                                    capture(oppgaveInputSlot)
                                )
                            }
                            val nokkelInput = nokkelInputSlot.captured
                            nokkelInput.getFodselsnummer() shouldBeEqualTo ARBEIDSTAKER_FNR.value
                            val oppgaveInput = oppgaveInputSlot.captured
                            val oppgaveTidspunkt =
                                Instant.ofEpochMilli(oppgaveInput.getTidspunkt()).atOffset(ZoneOffset.UTC)
                                    .toLocalDateTime()
                            oppgaveTidspunkt shouldBeBefore createdAt // Since oppgaveTidspunkt is UTC
                            oppgaveInput.getTekst() shouldBeEqualTo "Du er innkalt til dialogmøte - vi trenger svaret ditt"

                            val kafkaBehandlerDialogmeldingDTOSlot = slot<KafkaBehandlerDialogmeldingDTO>()
                            verify(exactly = 1) {
                                behandlerDialogmeldingProducer.sendDialogmelding(
                                    capture(
                                        kafkaBehandlerDialogmeldingDTOSlot
                                    )
                                )
                            }
                            val kafkaBehandlerDialogmeldingDTO = kafkaBehandlerDialogmeldingDTOSlot.captured
                            kafkaBehandlerDialogmeldingDTO.behandlerRef shouldBeEqualTo newDialogmoteDTO.behandler!!.behandlerRef
                            kafkaBehandlerDialogmeldingDTO.dialogmeldingTekst shouldBeEqualTo newDialogmoteDTO.behandler!!.innkalling.serialize()
                            kafkaBehandlerDialogmeldingDTO.dialogmeldingType shouldBeEqualTo DialogmeldingType.DIALOG_FORESPORSEL.name
                            kafkaBehandlerDialogmeldingDTO.dialogmeldingKode shouldBeEqualTo DialogmeldingKode.INNKALLING.value
                            kafkaBehandlerDialogmeldingDTO.dialogmeldingRefParent shouldBeEqualTo null
                            kafkaBehandlerDialogmeldingDTO.dialogmeldingVedlegg shouldNotBeEqualTo null
                        }
                    }
                    it("should return status OK if denied person has Adressbeskyttese") {
                        val newDialogmoteDTO = generateNewDialogmoteDTO(ARBEIDSTAKER_ADRESSEBESKYTTET)
                        val urlMotePersonIdent = "$dialogmoteApiV2Basepath/$dialogmoteApiPersonIdentUrlPath"
                        with(
                            handleRequest(HttpMethod.Post, urlMotePersonIdent) {
                                addHeader(Authorization, bearerHeader(validToken))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                setBody(objectMapper.writeValueAsString(newDialogmoteDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            //TODO: ANY() --> MOCKK, TYPE-- TYPE
                            verify(exactly = 1) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
                            clearMocks(esyfovarselProducerMock)
                        }
                    }

                    it("should return OK if request is successful: does not have a leader for Virksomhet") {
                        val newDialogmoteDTO =
                            generateNewDialogmoteDTO(personIdent = ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER)
                        with(
                            handleRequest(HttpMethod.Post, urlMote) {
                                addHeader(Authorization, bearerHeader(validToken))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                setBody(objectMapper.writeValueAsString(newDialogmoteDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            verify(exactly = 0) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
                            clearMocks(esyfovarselProducerMock)
                            verify(exactly = 1) {
                                altinnMock.insertCorrespondenceBasicV2(
                                    any(),
                                    any(),
                                    any(),
                                    any(),
                                    any()
                                )
                            }
                        }
                    }
                    it("should return OK if requesting to create Dialogmote for PersonIdent with inactive Oppfolgingstilfelle") {
                        val newDialogmoteDTO = generateNewDialogmoteDTO(ARBEIDSTAKER_INACTIVE_OPPFOLGINGSTILFELLE)
                        with(
                            handleRequest(HttpMethod.Post, urlMote) {
                                addHeader(Authorization, bearerHeader(validToken))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                setBody(objectMapper.writeValueAsString(newDialogmoteDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            verify(exactly = 0) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
                            clearMocks(esyfovarselProducerMock)
                        }
                    }

                    it("return OK when creating dialogmote for innbygger without Oppfolgingstilfelle") {
                        val newDialogmoteDTO = generateNewDialogmoteDTO(ARBEIDSTAKER_NO_OPPFOLGINGSTILFELLE)
                        with(
                            handleRequest(HttpMethod.Post, urlMote) {
                                addHeader(Authorization, bearerHeader(validToken))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                setBody(objectMapper.writeValueAsString(newDialogmoteDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            verify(exactly = 0) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
                            clearMocks(esyfovarselProducerMock)
                        }

                        val moteStatusEndretList = database.getMoteStatusEndretNotPublished()
                        moteStatusEndretList.size shouldBeEqualTo 1

                        moteStatusEndretList.first().status.name shouldBeEqualTo DialogmoteStatus.INNKALT.name
                        moteStatusEndretList.first().opprettetAv shouldBeEqualTo VEILEDER_IDENT
                        moteStatusEndretList.first().tilfelleStart shouldBeEqualTo LocalDate.EPOCH
                    }
                }

                describe("Unhappy paths") {
                    val url = "$dialogmoteApiV2Basepath/$dialogmoteApiPersonIdentUrlPath"
                    it("should return status Unauthorized if no token is supplied") {
                        with(
                            handleRequest(HttpMethod.Post, url) {}
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                            verify(exactly = 0) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
                            clearMocks(esyfovarselProducerMock)
                        }
                    }

                    it("should return status Forbidden if denied access to person") {
                        val newDialogmoteDTO = generateNewDialogmoteDTO(ARBEIDSTAKER_VEILEDER_NO_ACCESS)
                        val urlMotePersonIdent = "$dialogmoteApiV2Basepath/$dialogmoteApiPersonIdentUrlPath"
                        with(
                            handleRequest(HttpMethod.Post, urlMotePersonIdent) {
                                addHeader(Authorization, bearerHeader(validToken))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                setBody(objectMapper.writeValueAsString(newDialogmoteDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Forbidden
                            verify(exactly = 0) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
                            clearMocks(esyfovarselProducerMock)
                        }
                    }

                    it("should return Conflict if requesting to create Dialogmote for PersonIdent with an existing unfinished Dialogmote") {
                        val newDialogmoteDTO = generateNewDialogmoteDTOWithMissingValues(ARBEIDSTAKER_FNR)

                        with(
                            handleRequest(HttpMethod.Post, url) {
                                addHeader(Authorization, bearerHeader(validToken))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                setBody(objectMapper.writeValueAsString(newDialogmoteDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            verify(exactly = 1) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
                            clearMocks(esyfovarselProducerMock)
                        }

                        with(
                            handleRequest(HttpMethod.Post, urlMote) {
                                addHeader(Authorization, bearerHeader(validToken))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                setBody(objectMapper.writeValueAsString(newDialogmoteDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Conflict
                            verify(exactly = 0) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
                            clearMocks(esyfovarselProducerMock)
                        }
                    }

                    it("should return InternalServerError if requesting to create Dialogmote for PersonIdent no behandlende enhet") {
                        val newDialogmoteDTO = generateNewDialogmoteDTO(ARBEIDSTAKER_NO_BEHANDLENDE_ENHET)
                        with(
                            handleRequest(HttpMethod.Post, url) {
                                addHeader(Authorization, bearerHeader(validToken))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                setBody(objectMapper.writeValueAsString(newDialogmoteDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.InternalServerError
                            verify(exactly = 0) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
                            clearMocks(esyfovarselProducerMock)
                        }
                    }
                }
            }
        }
    }
})
