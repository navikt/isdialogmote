package no.nav.syfo.testhelper

import io.ktor.server.netty.*
import no.nav.common.KafkaEnvironment
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.testhelper.mock.*

class ExternalMockEnvironment private constructor(
    allowVarselMedFysiskBrev: Boolean = false,
) {
    val applicationState: ApplicationState = testAppState()
    val database = TestDatabase()
    val embeddedEnvironment: KafkaEnvironment = testKafka()

    val azureAdV2Mock = AzureAdV2Mock()
    val dokarkivMock = DokarkivMock()
    val pdlMock = PdlMock()
    val isdialogmotepdfgenMock = IsdialogmotepdfgenMock()
    val isproxyMock = IsproxyMock()
    val krrMock = KrrMock()
    val syfobehandlendeenhetMock = SyfobehandlendeenhetMock()
    val tilgangskontrollMock = VeilederTilgangskontrollMock()
    var narmesteLederMock = NarmesteLederMock()

    val externalApplicationMockMap = hashMapOf(
        azureAdV2Mock.name to azureAdV2Mock.server,
        dokarkivMock.name to dokarkivMock.server,
        isdialogmotepdfgenMock.name to isdialogmotepdfgenMock.server,
        isproxyMock.name to isproxyMock.server,
        krrMock.name to krrMock.server,
        syfobehandlendeenhetMock.name to syfobehandlendeenhetMock.server,
        tilgangskontrollMock.name to tilgangskontrollMock.server,
        narmesteLederMock.name to narmesteLederMock.server,
        pdlMock.name to pdlMock.server,
    )

    var environment = testEnvironment(
        kafkaBootstrapServers = embeddedEnvironment.brokersURL,
        azureTokenEndpoint = azureAdV2Mock.url,
        dokarkivUrl = dokarkivMock.url,
        isdialogmotepdfgenUrl = isdialogmotepdfgenMock.url,
        isproxyUrl = isproxyMock.url,
        krrUrl = krrMock.url,
        syfobehandlendeenhetUrl = syfobehandlendeenhetMock.url,
        syfotilgangskontrollUrl = tilgangskontrollMock.url,
        narmestelederUrl = narmesteLederMock.url,
        pdlUrl = pdlMock.url,
        allowVarselMedFysiskBrev = allowVarselMedFysiskBrev,
    )
    val redisServer = testRedis(environment)

    val wellKnownSelvbetjening = wellKnownSelvbetjeningMock()
    val wellKnownVeilederV2 = wellKnownVeilederV2Mock()

    companion object {
        private val instance: ExternalMockEnvironment by lazy {
            ExternalMockEnvironment().also {
                it.startExternalMocks()
            }
        }

        fun getInstance(allowVarselMedFysiskBrev: Boolean = false): ExternalMockEnvironment {
            if (instance.environment.allowVarselMedFysiskBrev != allowVarselMedFysiskBrev) {
                instance.environment = instance.environment.copy(
                    allowVarselMedFysiskBrev = allowVarselMedFysiskBrev,
                )
            }

            return instance
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
