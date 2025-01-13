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
import no.nav.syfo.brev.behandler.BehandlerVarselService
import no.nav.syfo.brev.esyfovarsel.EsyfovarselProducer
import no.nav.syfo.dialogmote.api.domain.DialogmoteDTO
import no.nav.syfo.dialogmote.api.domain.NewDialogmoteDTO
import no.nav.syfo.dialogmote.api.v2.dialogmoteApiPersonIdentUrlPath
import no.nav.syfo.dialogmote.api.v2.dialogmoteApiV2Basepath
import no.nav.syfo.dialogmote.domain.DialogmoteStatus
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import no.nav.syfo.util.configure
import org.amshove.kluent.shouldBeEqualTo

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
        status shouldBeEqualTo HttpStatusCode.OK
    }
    val response = getDialogmoter(token, personIdent)

    response.status shouldBeEqualTo HttpStatusCode.OK

    val dialogmoteList = response.body<List<DialogmoteDTO>>()

    dialogmoteList.size shouldBeEqualTo 1

    val dialogmoteDTO = dialogmoteList.first()
    dialogmoteDTO.status shouldBeEqualTo DialogmoteStatus.INNKALT.name
    dialogmoteDTO.referatList shouldBeEqualTo emptyList()

    return dialogmoteDTO
}
