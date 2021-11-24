package no.nav.syfo.brev.behandler.kafka

import no.nav.syfo.application.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.brev.behandler.kafka")

fun kafkaModule(
    applicationState: ApplicationState,
    environment: Environment,
) {
    if (environment.toggleKafkaProcessingDialogmeldinger) {
        launchBackgroundTask(applicationState = applicationState) {
            blockingApplicationLogicDialogmeldinger(
                applicationState = applicationState,
                applicationEnvironmentKafka = environment.kafka,
            )
        }
    } else {
        log.info("Kafka processing dialogmeldinger is not enabled")
    }
}
