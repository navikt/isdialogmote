package no.nav.syfo.testhelper

import no.nav.common.KafkaEnvironment
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.testhelper.mock.*

class ExternalMockEnvironment {
    val applicationState: ApplicationState = testAppState()
    val database = TestDatabase()
    val embeddedEnvironment: KafkaEnvironment = testKafka()

    val azureADMock = AzureADMock()
    val dokarkivMock = DokarkivMock()
    val isdialogmotepdfgenMock = IsdialogmotepdfgenMock()
    val modiasyforestMock = ModiasyforestMock()
    val syfobehandlendeenhetMock = SyfobehandlendeenhetMock()
    val syfopersonMock = SyfopersonMock()
    val tilgangskontrollMock = VeilederTilgangskontrollMock()

    val externalApplicationMockMap = hashMapOf(
        dokarkivMock.name to dokarkivMock.server,
        isdialogmotepdfgenMock.name to isdialogmotepdfgenMock.server,
        modiasyforestMock.name to modiasyforestMock.server,
        syfobehandlendeenhetMock.name to syfobehandlendeenhetMock.server,
        syfopersonMock.name to syfopersonMock.server,
        tilgangskontrollMock.name to tilgangskontrollMock.server,
    )

    val environment = testEnvironment(
        kafkaBootstrapServers = embeddedEnvironment.brokersURL,
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
}
