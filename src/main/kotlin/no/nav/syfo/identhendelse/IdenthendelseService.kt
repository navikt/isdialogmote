package no.nav.syfo.identhendelse

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.dialogmote.database.getAllMotedeltakerArbeidstakerByIdenter
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

    /*
        * Sjekk på FOLKEREGISTERIDENT og se om lista er større enn 2
        * Finn gjeldende og gamle identer
        * Get på de verdiene i databasen og se om vi har noen entries
        * Evt spør PDL om det er oppdatert der?
        * Update på de id'ene som får hit
        * Success?
        * */
    fun handleIdenthendelse(identhendelse: KafkaIdenthendelseDTO) {
        val identer = identhendelse.identifikatorer.filter { it.type == IdentType.FOLKEREGISTERIDENT }
        if (identer.size > 1) {
            val nyIdent = identer.find { it.gjeldende }?.idnummer ?: throw IllegalStateException("Mangler gyldig ident fra PDL")
            val gamleIdenter = identer.filter { !it.gjeldende }.map { it.idnummer }

            val queryString = "(${gamleIdenter.joinToString(",")})"
            val motedeltakereMedGammelIdent = database.getAllMotedeltakerArbeidstakerByIdenter(queryString)

            if (motedeltakereMedGammelIdent.isNotEmpty()) {
                // TODO: Dobbeltsjekke PDL at det har blitt oppdatert der?
                val oppdaterteMotedeltakere = database.updateMotedeltakerArbeidstakerPersonident(nyIdent, queryString)
                log.info("Updated ${oppdaterteMotedeltakere.size} motedeltaker-rows based on Identhendelse from PDL")
            }
        }
    }
}
