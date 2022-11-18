package no.nav.syfo.identhendelse

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.dialogmote.database.getMotedeltakerArbeidstakerByIdent
import no.nav.syfo.dialogmote.database.updateMotedeltakerArbeidstakerPersonident
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.identhendelse.kafka.KafkaIdenthendelseDTO
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class IdenthendelseService(
    private val database: DatabaseInterface,
    private val pdlClient: PdlClient,
) {

    private val log: Logger = LoggerFactory.getLogger(IdenthendelseService::class.java)

    suspend fun handleIdenthendelse(identhendelse: KafkaIdenthendelseDTO) {
        if (identhendelse.folkeregisterIdenter.size > 1) {
            val activeIdent = identhendelse.activePersonident
            val inactiveIdenter = identhendelse.inactivePersonidenter

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
            }
        }
    }

    // Erfaringer fra andre team tilsier at vi burde dobbeltsjekke at ting har blitt oppdatert i PDL før vi gjør endringer
    private suspend fun checkThatPdlIsUpdated(nyIdent: PersonIdent) {
        val pdlIdenter = pdlClient.hentIdenter(nyIdent.value)
        if (nyIdent.value != pdlIdenter?.aktivIdent || pdlIdenter.identer.any { it.ident == nyIdent.value && it.historisk }) {
            throw IllegalStateException("Ny ident er ikke aktiv ident i PDL")
        }
    }
}
