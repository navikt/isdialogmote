package no.nav.syfo.identhendelse

import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.dialogmote.database.getMotedeltakerArbeidstakerByIdent
import no.nav.syfo.dialogmote.database.updateMotedeltakerArbeidstakerPersonident
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.identhendelse.kafka.KafkaIdenthendelseDTO
import no.nav.syfo.metric.COUNT_KAFKA_CONSUMER_PDL_AKTOR_UPDATES
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class IdenthendelseService(
    private val database: DatabaseInterface,
    private val pdlClient: PdlClient,
) {

    private val log: Logger = LoggerFactory.getLogger(IdenthendelseService::class.java)

    fun handleIdenthendelse(identhendelse: KafkaIdenthendelseDTO) {
        if (identhendelse.folkeregisterIdenter.size > 1) {
            val activeIdent = identhendelse.getActivePersonident()
            if (activeIdent != null) {
                val inactiveIdenter = identhendelse.getInactivePersonidenter()
                val motedeltakereWithOldIdent = inactiveIdenter.flatMap { personident ->
                    database.getMotedeltakerArbeidstakerByIdent(personident)
                }

                if (motedeltakereWithOldIdent.isNotEmpty()) {
                    checkThatPdlIsUpdated(activeIdent)
                    var numberOfUpdatedIdenter = 0
                    motedeltakereWithOldIdent
                        .forEach { arbeidstaker ->
                            val inactiveIdent = arbeidstaker.personIdent
                            numberOfUpdatedIdenter += database.updateMotedeltakerArbeidstakerPersonident(activeIdent, inactiveIdent)
                        }
                    log.info("Identhendelse: Updated $numberOfUpdatedIdenter motedeltakere based on Identhendelse from PDL")
                    COUNT_KAFKA_CONSUMER_PDL_AKTOR_UPDATES.increment(numberOfUpdatedIdenter.toDouble())
                }
            } else {
                log.warn("Mangler gyldig ident fra PDL")
            }
        }
    }

    // Erfaringer fra andre team tilsier at vi burde dobbeltsjekke at ting har blitt oppdatert i PDL før vi gjør endringer
    private fun checkThatPdlIsUpdated(nyIdent: PersonIdent) {
        runBlocking {
            val pdlIdenter = pdlClient.hentIdenter(nyIdent.value) ?: throw RuntimeException("Fant ingen identer fra PDL")
            if (nyIdent.value != pdlIdenter.aktivIdent && pdlIdenter.identhendelseIsNotHistorisk(nyIdent.value)) {
                throw IllegalStateException("Ny ident er ikke aktiv ident i PDL")
            }
        }
    }
}
