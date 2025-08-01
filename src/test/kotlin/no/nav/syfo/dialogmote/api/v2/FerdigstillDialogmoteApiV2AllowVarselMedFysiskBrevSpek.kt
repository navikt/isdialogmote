package no.nav.syfo.dialogmote.api.v2

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import no.altinn.schemas.services.intermediary.receipt._2009._10.ReceiptExternal
import no.altinn.schemas.services.intermediary.receipt._2009._10.ReceiptStatusEnum
import no.altinn.services.serviceengine.correspondence._2009._10.ICorrespondenceAgencyExternalBasic
import no.nav.syfo.api.endpoints.dialogmoteApiMoteFerdigstillPath
import no.nav.syfo.api.endpoints.dialogmoteApiV2Basepath
import no.nav.syfo.infrastructure.kafka.esyfovarsel.EsyfovarselProducer
import no.nav.syfo.infrastructure.kafka.esyfovarsel.HendelseType
import no.nav.syfo.infrastructure.database.dialogmote.PdfService
import no.nav.syfo.api.dto.DialogmoteDTO
import no.nav.syfo.infrastructure.database.dialogmote.database.getReferat
import no.nav.syfo.infrastructure.database.dialogmote.database.repository.MoteStatusEndretRepository
import no.nav.syfo.domain.dialogmote.DialogmoteStatus
import no.nav.syfo.domain.dialogmote.DocumentComponentType
import no.nav.syfo.infrastructure.client.oppfolgingstilfelle.toLatestOppfolgingstilfelle
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_IKKE_VARSEL
import no.nav.syfo.testhelper.UserConstants.VEILEDER_IDENT
import no.nav.syfo.testhelper.generator.generateInkallingHendelse
import no.nav.syfo.testhelper.generator.generateNewDialogmoteDTO
import no.nav.syfo.testhelper.generator.generateNewReferatDTO
import no.nav.syfo.testhelper.mock.oppfolgingstilfellePersonDTO
import no.nav.syfo.testhelper.mock.pdfReferat
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBe
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.util.*

class FerdigstillDialogmoteApiV2AllowVarselMedFysiskBrevSpek : Spek({
    describe(FerdigstillDialogmoteApiV2AllowVarselMedFysiskBrevSpek::class.java.simpleName) {
        val externalMockEnvironment = ExternalMockEnvironment.getInstance()

        val database = externalMockEnvironment.database
        val moteStatusEndretRepository = MoteStatusEndretRepository(database)

        val esyfovarselHendelse = generateInkallingHendelse()
        val esyfovarselProducerMock = mockk<EsyfovarselProducer>(relaxed = true)
        justRun { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }

        val pdfService = PdfService(
            database = database,
        )

        val altinnMock = mockk<ICorrespondenceAgencyExternalBasic>()

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

        describe("Ferdigstill Dialogmote") {
            val validToken = generateJWTNavIdent(
                externalMockEnvironment.environment.aadAppClient,
                externalMockEnvironment.wellKnownVeilederV2.issuer,
                VEILEDER_IDENT,
            )
            describe("Happy path") {
                val newDialogmoteDTO = generateNewDialogmoteDTO(ARBEIDSTAKER_IKKE_VARSEL)
                val newReferatDTO = generateNewReferatDTO()

                it("should return OK if request is successful") {
                    testApplication {
                        val createdDialogmoteUUID: String
                        val client = setupApiAndClient(
                            altinnMock = altinnMock,
                            esyfovarselProducer = esyfovarselProducerMock,
                        )
                        client.postMote(validToken, newDialogmoteDTO)
                        verify(exactly = 1) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }

                        client.getDialogmoter(validToken, ARBEIDSTAKER_IKKE_VARSEL).apply {
                            status shouldBeEqualTo HttpStatusCode.OK
                            val dialogmoteList = body<List<DialogmoteDTO>>()
                            dialogmoteList.size shouldBeEqualTo 1

                            val dialogmoteDTO = dialogmoteList.first()
                            dialogmoteDTO.status shouldBeEqualTo DialogmoteStatus.INNKALT.name
                            dialogmoteDTO.referatList shouldBeEqualTo emptyList()

                            createdDialogmoteUUID = dialogmoteDTO.uuid
                        }

                        val urlMoteUUIDFerdigstill =
                            "$dialogmoteApiV2Basepath/$createdDialogmoteUUID$dialogmoteApiMoteFerdigstillPath"
                        client.post(urlMoteUUIDFerdigstill) {
                            bearerAuth(validToken)
                            contentType(ContentType.Application.Json)
                            setBody(newReferatDTO)
                        }.apply {
                            status shouldBeEqualTo HttpStatusCode.OK
                            esyfovarselHendelse.type = HendelseType.NL_DIALOGMOTE_REFERAT
                            verify(exactly = 1) { esyfovarselProducerMock.sendVarselToEsyfovarsel(esyfovarselHendelse) }
                        }

                        val referatUuid: String
                        client.getDialogmoter(validToken, ARBEIDSTAKER_IKKE_VARSEL).apply {
                            status shouldBeEqualTo HttpStatusCode.OK
                            val dialogmoteList = body<List<DialogmoteDTO>>()
                            dialogmoteList.size shouldBeEqualTo 1

                            val dialogmoteDTO = dialogmoteList.first()
                            dialogmoteDTO.status shouldBeEqualTo DialogmoteStatus.FERDIGSTILT.name
                            dialogmoteDTO.arbeidstaker.personIdent shouldBeEqualTo newDialogmoteDTO.arbeidstaker.personIdent
                            dialogmoteDTO.arbeidsgiver.virksomhetsnummer shouldBeEqualTo newDialogmoteDTO.arbeidsgiver.virksomhetsnummer

                            dialogmoteDTO.sted shouldBeEqualTo newDialogmoteDTO.tidSted.sted

                            val referat = dialogmoteDTO.referatList.first()
                            referatUuid = referat.uuid
                            referat.digitalt shouldBeEqualTo false
                            referat.situasjon shouldBeEqualTo "Dette er en beskrivelse av situasjonen"
                            referat.narmesteLederNavn shouldBeEqualTo "Grønn Bamse"
                            referat.document[0].type shouldBeEqualTo DocumentComponentType.HEADER_H1
                            referat.document[0].texts shouldBeEqualTo listOf("Tittel referat")

                            referat.document[1].type shouldBeEqualTo DocumentComponentType.PARAGRAPH
                            referat.document[1].texts shouldBeEqualTo listOf("Brødtekst")

                            referat.document[2].type shouldBeEqualTo DocumentComponentType.PARAGRAPH
                            referat.document[2].key shouldBeEqualTo "Standardtekst"
                            referat.document[2].texts shouldBeEqualTo listOf("Dette er en standardtekst")
                            referat.brevBestiltTidspunkt shouldBeEqualTo null

                            referat.andreDeltakere.first().funksjon shouldBeEqualTo "Verneombud"
                            referat.andreDeltakere.first().navn shouldBeEqualTo "Tøff Pyjamas"

                            val pdf =
                                pdfService.getPdf(database.getReferat(UUID.fromString(referat.uuid)).first().pdfId!!)
                            pdf shouldBeEqualTo pdfReferat

                            val moteStatusEndretList = moteStatusEndretRepository.getMoteStatusEndretNotPublished()
                            moteStatusEndretList.size shouldBeEqualTo 2

                            moteStatusEndretList.forEach { moteStatusEndret ->
                                moteStatusEndret.opprettetAv shouldBeEqualTo VEILEDER_IDENT
                                moteStatusEndret.tilfelleStart shouldBeEqualTo oppfolgingstilfellePersonDTO().toLatestOppfolgingstilfelle()?.start
                            }
                        }
                        database.setReferatBrevBestilt(referatUuid)

                        val response = client.getDialogmoter(validToken, ARBEIDSTAKER_IKKE_VARSEL)

                        response.status shouldBeEqualTo HttpStatusCode.OK
                        val dialogmoteList = response.body<List<DialogmoteDTO>>()

                        dialogmoteList.size shouldBeEqualTo 1

                        val dialogmoteDTO = dialogmoteList.first()
                        val referat = dialogmoteDTO.referatList.first()
                        referat.brevBestiltTidspunkt shouldNotBe null
                        referat.brevBestiltTidspunkt!!.toLocalDate() shouldBeEqualTo LocalDate.now()
                    }
                }
            }
        }
    }
})
