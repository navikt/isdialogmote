package no.nav.syfo.identhendelse

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.dialogmote.database.getMotedeltakerArbeidstakerByIdent
import no.nav.syfo.dialogmote.database.updateMotedeltakerArbeidstakerPersonident
import no.nav.syfo.identhendelse.kafka.IdentType
import no.nav.syfo.identhendelse.kafka.KafkaIdenthendelseDTO
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class IdenthendelseService(
    private val database: DatabaseInterface,
    private val pdlClient: PdlClient,
) {

    private val log: Logger = LoggerFactory.getLogger(IdenthendelseService::class.java)

    suspend fun handleIdenthendelse(identhendelse: KafkaIdenthendelseDTO) {
        val identer = identhendelse.identifikatorer.filter { it.type == IdentType.FOLKEREGISTERIDENT }
        if (identer.size > 1) {
            val nyIdent = identer.find { it.gjeldende }?.idnummer ?: throw IllegalStateException("Mangler gyldig ident fra PDL")
            val gamleIdenter = identer.filter { !it.gjeldende }.map { it.idnummer }

            val motedeltakereMedGammelIdent = gamleIdenter.flatMap { personident ->
                database.getMotedeltakerArbeidstakerByIdent(personident)
            }

            if (motedeltakereMedGammelIdent.isNotEmpty()) {
                checkThatPdlIsUpdated(nyIdent)
                var numberOfUpdatedIdenter = 0
                motedeltakereMedGammelIdent
                    .forEach { arbeidstaker ->
                        val gammelIdent = arbeidstaker.personIdent.value
                        numberOfUpdatedIdenter += database.updateMotedeltakerArbeidstakerPersonident(nyIdent, gammelIdent)
                    }
                log.info("Identhendelse: Updated $numberOfUpdatedIdenter motedeltakere based on Identhendelse from PDL")
            }
        }
    }

    // Erfaringer fra andre team tilsier at vi burde dobbeltsjekke at ting har blitt oppdatert i PDL før vi gjør endringer
    private suspend fun checkThatPdlIsUpdated(nyIdent: String) {
        val pdlIdenter = pdlClient.hentIdenter(nyIdent)
        if (pdlIdenter?.aktivIdent != nyIdent || pdlIdenter.identer.any { it.ident == nyIdent && it.historisk }) {
            throw IllegalStateException("Ny ident er ikke aktiv ident i PDL")
        }
    }
}
