package no.nav.syfo.testhelper

import io.ktor.server.netty.*
import no.nav.common.KafkaEnvironment
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.testhelper.mock.*

class ExternalMockEnvironment private constructor() {
    val applicationState: ApplicationState = testAppState()
    val database = TestDatabase()
    val embeddedEnvironment: KafkaEnvironment = testKafka()
    val azureAdV2Mock = AzureAdV2Mock()
    val tokendingsMock = TokendingsMock()
    val dokarkivMock = DokarkivMock()
    val pdlMock = PdlMock()
    val isdialogmotepdfgenMock = IsdialogmotepdfgenMock()
    val isoppfolgingstilfelleMock = IsoppfolgingstilfelleMock()
    val eregMock = EregMock()
    val krrMock = KrrMock()
    val syfobehandlendeenhetMock = SyfobehandlendeenhetMock()
    val tilgangskontrollMock = VeilederTilgangskontrollMock()
    var narmesteLederMock = NarmesteLederMock()

    val externalApplicationMockMap = hashMapOf(
        azureAdV2Mock.name to azureAdV2Mock.server,
        tokendingsMock.name to tokendingsMock.server,
        dokarkivMock.name to dokarkivMock.server,
        isdialogmotepdfgenMock.name to isdialogmotepdfgenMock.server,
        isoppfolgingstilfelleMock.name to isoppfolgingstilfelleMock.server,
        eregMock.name to eregMock.server,
        krrMock.name to krrMock.server,
        syfobehandlendeenhetMock.name to syfobehandlendeenhetMock.server,
        tilgangskontrollMock.name to tilgangskontrollMock.server,
        narmesteLederMock.name to narmesteLederMock.server,
        pdlMock.name to pdlMock.server,
    )

    var environment = testEnvironment(
        kafkaBootstrapServers = embeddedEnvironment.brokersURL,
        azureTokenEndpoint = azureAdV2Mock.url,
        tokenxEndpoint = tokendingsMock.url,
        dokarkivUrl = dokarkivMock.url,
        isdialogmotepdfgenUrl = isdialogmotepdfgenMock.url,
        isoppfolgingstilfelleUrl = isoppfolgingstilfelleMock.url,
        eregUrl = eregMock.url,
        krrUrl = krrMock.url,
        syfobehandlendeenhetUrl = syfobehandlendeenhetMock.url,
        tilgangskontrollUrl = tilgangskontrollMock.url,
        narmestelederUrl = narmesteLederMock.url,
        pdlUrl = pdlMock.url,
    )
    val redisServer = testRedis(environment)

    val wellKnownSelvbetjening = wellKnownSelvbetjeningMock()
    val wellKnownVeilederV2 = wellKnownVeilederV2Mock()

    companion object {
        private val singletonInstance: ExternalMockEnvironment by lazy {
            ExternalMockEnvironment().also {
                it.startExternalMocks()
            }
        }

        fun getInstance(): ExternalMockEnvironment {
            return singletonInstance
        }
    }
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
