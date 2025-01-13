package no.nav.syfo.dialogmote.api.v2

import io.ktor.client.call.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import no.altinn.schemas.services.intermediary.receipt._2009._10.ReceiptExternal
import no.altinn.schemas.services.intermediary.receipt._2009._10.ReceiptStatusEnum
import no.altinn.services.serviceengine.correspondence._2009._10.ICorrespondenceAgencyExternalBasic
import no.nav.syfo.brev.esyfovarsel.EsyfovarselProducer
import no.nav.syfo.client.oppfolgingstilfelle.toLatestOppfolgingstilfelle
import no.nav.syfo.dialogmote.api.domain.DialogmoteDTO
import no.nav.syfo.dialogmote.database.repository.MoteStatusEndretRepository
import no.nav.syfo.dialogmote.domain.DocumentComponentType
import no.nav.syfo.dialogmote.domain.MotedeltakerVarselType
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_IKKE_VARSEL
import no.nav.syfo.testhelper.UserConstants.ENHET_NR
import no.nav.syfo.testhelper.UserConstants.VEILEDER_IDENT
import no.nav.syfo.testhelper.generator.DIALOGMOTE_TIDSPUNKT_FIXTURE
import no.nav.syfo.testhelper.generator.generateInkallingHendelse
import no.nav.syfo.testhelper.generator.generateNewDialogmoteDTO
import no.nav.syfo.testhelper.generator.generateNewDialogmoteDTOWithMissingValues
import no.nav.syfo.testhelper.mock.oppfolgingstilfellePersonDTO
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldNotBe
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate

class PostDialogmoteApiV2AllowVarselMedFysiskBrevSpek : Spek({
    describe(PostDialogmoteApiV2AllowVarselMedFysiskBrevSpek::class.java.simpleName) {
        val externalMockEnvironment = ExternalMockEnvironment.getInstance()
        val database = externalMockEnvironment.database
        val moteStatusEndretRepository = MoteStatusEndretRepository(database)

        val altinnMock = mockk<ICorrespondenceAgencyExternalBasic>()
        val esyfovarselHendelse = generateInkallingHendelse()

        val esyfovarselProducerMock = mockk<EsyfovarselProducer>(relaxed = true)
        justRun { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }

        describe("Create Dialogmote for PersonIdent payload") {
            val validToken = generateJWTNavIdent(
                externalMockEnvironment.environment.aadAppClient,
                externalMockEnvironment.wellKnownVeilederV2.issuer,
                VEILEDER_IDENT,
            )

            beforeEachTest {
                val altinnResponse = ReceiptExternal()
                altinnResponse.receiptStatusCode = ReceiptStatusEnum.OK

                clearMocks(altinnMock)
                every {
                    altinnMock.insertCorrespondenceBasicV2(any(), any(), any(), any(), any())
                } returns altinnResponse
            }

            afterEachTest {
                database.dropData()
            }

            beforeEachGroup {
                database.dropData()
            }

            describe("Happy path") {
                it("should return OK if request is successful even if ikke-varsle") {
                    val moteTidspunkt = DIALOGMOTE_TIDSPUNKT_FIXTURE
                    val newDialogmoteDTO = generateNewDialogmoteDTO(
                        personIdent = ARBEIDSTAKER_IKKE_VARSEL,
                        dato = moteTidspunkt,
                    )

                    testApplication {
                        val client = setupApiAndClient(
                            altinnMock = altinnMock,
                            esyfovarselProducer = esyfovarselProducerMock
                        )
                        client.postMote(validToken, newDialogmoteDTO)
                        verify(exactly = 1) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }

                        val varselUuid: String

                        val response = client.getDialogmoter(validToken, ARBEIDSTAKER_IKKE_VARSEL)

                        response.status shouldBeEqualTo HttpStatusCode.OK

                        val dialogmoteList = response.body<List<DialogmoteDTO>>()

                        dialogmoteList.size shouldBeEqualTo 1

                        val dialogmoteDTO = dialogmoteList.first()
                        dialogmoteDTO.tildeltEnhet shouldBeEqualTo ENHET_NR.value
                        dialogmoteDTO.tildeltVeilederIdent shouldBeEqualTo VEILEDER_IDENT

                        dialogmoteDTO.arbeidstaker.personIdent shouldBeEqualTo newDialogmoteDTO.arbeidstaker.personIdent
                        dialogmoteDTO.arbeidstaker.varselList.size shouldBeEqualTo 1

                        val arbeidstakerVarselDTO = dialogmoteDTO.arbeidstaker.varselList.first()
                        varselUuid = arbeidstakerVarselDTO.uuid
                        arbeidstakerVarselDTO.varselType shouldBeEqualTo MotedeltakerVarselType.INNKALT.name
                        arbeidstakerVarselDTO.digitalt shouldBeEqualTo false
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
                        arbeidstakerVarselDTO.brevBestiltTidspunkt shouldBeEqualTo null

                        dialogmoteDTO.arbeidsgiver.virksomhetsnummer shouldBeEqualTo newDialogmoteDTO.arbeidsgiver.virksomhetsnummer
                        dialogmoteDTO.arbeidsgiver.varselList.size shouldBeEqualTo 1
                        val arbeidsgiverVarselDTO = dialogmoteDTO.arbeidsgiver.varselList.first()
                        arbeidsgiverVarselDTO.varselType shouldBeEqualTo MotedeltakerVarselType.INNKALT.name
                        arbeidsgiverVarselDTO.lestDato.shouldBeNull()
                        arbeidsgiverVarselDTO.fritekst shouldBeEqualTo "Ipsum lorum arbeidsgiver"

                        dialogmoteDTO.sted shouldBeEqualTo newDialogmoteDTO.tidSted.sted
                        dialogmoteDTO.videoLink shouldBeEqualTo "https://meet.google.com/xyz"

                        val moteStatusEndretList = moteStatusEndretRepository.getMoteStatusEndretNotPublished()
                        moteStatusEndretList.size shouldBeEqualTo 1

                        moteStatusEndretList.first().status.name shouldBeEqualTo dialogmoteDTO.status
                        moteStatusEndretList.first().opprettetAv shouldBeEqualTo VEILEDER_IDENT
                        moteStatusEndretList.first().tilfelleStart shouldBeEqualTo oppfolgingstilfellePersonDTO().toLatestOppfolgingstilfelle()?.start

                        database.setMotedeltakerArbeidstakerVarselBrevBestilt(varselUuid)

                        client.getDialogmoter(validToken, ARBEIDSTAKER_IKKE_VARSEL).apply {
                            status shouldBeEqualTo HttpStatusCode.OK
                            val arbeidstakerVarselDTOBrevBestilt = body<List<DialogmoteDTO>>().first().arbeidstaker.varselList.first()
                            arbeidstakerVarselDTOBrevBestilt.brevBestiltTidspunkt shouldNotBe null
                            arbeidstakerVarselDTOBrevBestilt.brevBestiltTidspunkt!!.toLocalDate() shouldBeEqualTo LocalDate.now()
                        }
                    }
                }

                it("should return OK if request is successful: optional values missing") {
                    val newDialogmoteDTO = generateNewDialogmoteDTOWithMissingValues(ARBEIDSTAKER_IKKE_VARSEL)

                    testApplication {
                        val client = setupApiAndClient(
                            altinnMock = altinnMock,
                            esyfovarselProducer = esyfovarselProducerMock
                        )
                        val dialogmoteDTO =
                            client.postAndGetDialogmote(validToken, newDialogmoteDTO, ARBEIDSTAKER_IKKE_VARSEL)

                        dialogmoteDTO.tildeltEnhet shouldBeEqualTo ENHET_NR.value
                        dialogmoteDTO.tildeltVeilederIdent shouldBeEqualTo VEILEDER_IDENT

                        dialogmoteDTO.arbeidstaker.personIdent shouldBeEqualTo newDialogmoteDTO.arbeidstaker.personIdent
                        dialogmoteDTO.arbeidstaker.varselList.size shouldBeEqualTo 1

                        val arbeidstakerVarselDTO = dialogmoteDTO.arbeidstaker.varselList.first()
                        arbeidstakerVarselDTO.varselType shouldBeEqualTo MotedeltakerVarselType.INNKALT.name
                        arbeidstakerVarselDTO.digitalt shouldBeEqualTo false
                        arbeidstakerVarselDTO.lestDato.shouldBeNull()
                        arbeidstakerVarselDTO.fritekst shouldBeEqualTo ""

                        dialogmoteDTO.arbeidsgiver.virksomhetsnummer shouldBeEqualTo newDialogmoteDTO.arbeidsgiver.virksomhetsnummer

                        dialogmoteDTO.sted shouldBeEqualTo newDialogmoteDTO.tidSted.sted
                        dialogmoteDTO.videoLink shouldBeEqualTo ""
                    }
                }
            }
        }
    }
})
