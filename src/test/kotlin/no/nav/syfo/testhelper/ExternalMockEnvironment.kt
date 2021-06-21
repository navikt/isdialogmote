package no.nav.syfo.testhelper

import io.ktor.server.netty.*
import no.nav.common.KafkaEnvironment
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.testhelper.mock.*

class ExternalMockEnvironment {
    val applicationState: ApplicationState = testAppState()
    val database = TestDatabase()
    val embeddedEnvironment: KafkaEnvironment = testKafka()

    val azureADMock = AzureADMock()
    val azureAdV2Mock = AzureAdV2Mock()
    val dokarkivMock = DokarkivMock()
    val isdialogmotepdfgenMock = IsdialogmotepdfgenMock()
    val modiasyforestMock = ModiasyforestMock()
    val syfobehandlendeenhetMock = SyfobehandlendeenhetMock()
    val syfopersonMock = SyfopersonMock()
    val tilgangskontrollMock = VeilederTilgangskontrollMock()

    val externalApplicationMockMap = hashMapOf(
        azureAdV2Mock.name to azureAdV2Mock.server,
        dokarkivMock.name to dokarkivMock.server,
        isdialogmotepdfgenMock.name to isdialogmotepdfgenMock.server,
        modiasyforestMock.name to modiasyforestMock.server,
        syfobehandlendeenhetMock.name to syfobehandlendeenhetMock.server,
        syfopersonMock.name to syfopersonMock.server,
        tilgangskontrollMock.name to tilgangskontrollMock.server,
    )

    val environment = testEnvironment(
        kafkaBootstrapServers = embeddedEnvironment.brokersURL,
        azureTokenEndpoint = azureAdV2Mock.url,
        dokarkivUrl = dokarkivMock.url,
        isdialogmotepdfgenUrl = isdialogmotepdfgenMock.url,
        modiasyforestUrl = modiasyforestMock.url,
        syfobehandlendeenhetUrl = syfobehandlendeenhetMock.url,
        syfopersonUrl = syfopersonMock.url,
        syfotilgangskontrollUrl = tilgangskontrollMock.url
    )
    val redisServer = testRedis(environment)

    val wellKnownSelvbetjening = wellKnownSelvbetjeningMock()
    val wellKnownVeileder = wellKnownSelvbetjeningMock()
    val wellKnownVeilederV2 = wellKnownVeilederV2Mock()
}

fun ExternalMockEnvironment.startExternalMocks() {
    this.externalApplicationMockMap.start()
    this.embeddedEnvironment.start()
    this.redisServer.start()
}

fun ExternalMockEnvironment.stopExternalMocks() {
    this.externalApplicationMockMap.stop()
    this.database.stop()
    this.embeddedEnvironment.tearDown()
    this.redisServer.stop()
}

fun HashMap<String, NettyApplicationEngine>.start() {
    this.forEach {
        it.value.start()
    }
}

fun HashMap<String, NettyApplicationEngine>.stop(
    gracePeriodMillis: Long = 1L,
    timeoutMillis: Long = 10L,
) {
    this.forEach {
        it.value.stop(gracePeriodMillis, timeoutMillis)
    }
}
