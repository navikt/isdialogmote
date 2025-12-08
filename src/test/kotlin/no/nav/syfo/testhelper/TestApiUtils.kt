package no.nav.syfo.testhelper

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.testing.*
import io.mockk.mockk
import no.altinn.services.serviceengine.correspondence._2009._10.ICorrespondenceAgencyExternalBasic
import no.nav.syfo.api.NAV_PERSONIDENT_HEADER
import no.nav.syfo.api.authentication.configure
import no.nav.syfo.api.dto.DialogmoteDTO
import no.nav.syfo.api.dto.NewDialogmoteDTO
import no.nav.syfo.api.endpoints.dialogmoteApiPersonIdentUrlPath
import no.nav.syfo.api.endpoints.dialogmoteApiV2Basepath
import no.nav.syfo.application.BehandlerVarselService
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.dialogmote.Dialogmote
import no.nav.syfo.infrastructure.kafka.esyfovarsel.EsyfovarselProducer
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import org.junit.jupiter.api.Assertions.assertEquals
import kotlin.test.assertTrue

fun ApplicationTestBuilder.setupApiAndClient(
    behandlerVarselService: BehandlerVarselService = mockk(),
    altinnMock: ICorrespondenceAgencyExternalBasic = mockk(),
    esyfovarselProducer: EsyfovarselProducer = mockk(relaxed = true),
): HttpClient {
    application {
        testApiModule(
            externalMockEnvironment = ExternalMockEnvironment.getInstance(),
            behandlerVarselService = behandlerVarselService,
            altinnMock = altinnMock,
            esyfovarselProducer = esyfovarselProducer,
        )
    }
    val client = createClient {
        install(ContentNegotiation) {
            jackson { configure() }
        }
    }

    return client
}

suspend fun HttpClient.postMote(token: String, newDialogmoteDTO: NewDialogmoteDTO): HttpResponse =
    this.post("$dialogmoteApiV2Basepath/$dialogmoteApiPersonIdentUrlPath") {
        bearerAuth(token)
        contentType(ContentType.Application.Json)
        setBody(newDialogmoteDTO)
    }

suspend fun HttpClient.getDialogmoter(token: String, personIdent: PersonIdent): HttpResponse =
    get("$dialogmoteApiV2Basepath$dialogmoteApiPersonIdentUrlPath") {
        bearerAuth(token)
        header(NAV_PERSONIDENT_HEADER, personIdent.value)
    }

suspend fun HttpClient.postAndGetDialogmote(
    token: String,
    newDialogmoteDTO: NewDialogmoteDTO,
    personIdent: PersonIdent = ARBEIDSTAKER_FNR,
): DialogmoteDTO {
    postMote(token, newDialogmoteDTO).apply {
        assertEquals(HttpStatusCode.OK, status)
    }
    val response = getDialogmoter(token, personIdent)

    assertEquals(HttpStatusCode.OK, response.status)

    val dialogmoteList = response.body<List<DialogmoteDTO>>()

    assertEquals(1, dialogmoteList.size)

    val dialogmoteDTO = dialogmoteList.first()
    assertEquals(Dialogmote.Status.INNKALT.name, dialogmoteDTO.status)
    assertTrue(dialogmoteDTO.referatList.isEmpty())

    return dialogmoteDTO
}
