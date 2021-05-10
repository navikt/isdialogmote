package no.nav.syfo.testhelper

import io.ktor.application.*
import no.nav.syfo.application.api.apiModule
import no.nav.syfo.application.mq.MQSenderInterface
import no.nav.syfo.varsel.arbeidstaker.brukernotifikasjon.BrukernotifikasjonProducer

fun Application.testApiModule(
    externalMockEnvironment: ExternalMockEnvironment,
    brukernotifikasjonProducer: BrukernotifikasjonProducer,
    mqSenderMock: MQSenderInterface,
) {
    this.apiModule(
        applicationState = externalMockEnvironment.applicationState,
        brukernotifikasjonProducer = brukernotifikasjonProducer,
        database = externalMockEnvironment.database,
        mqSender = mqSenderMock,
        environment = externalMockEnvironment.environment,
        wellKnownSelvbetjening = externalMockEnvironment.wellKnownSelvbetjening,
        wellKnownVeileder = externalMockEnvironment.wellKnownVeileder,
    )
}
