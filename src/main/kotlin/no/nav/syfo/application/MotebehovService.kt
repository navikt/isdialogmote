package no.nav.syfo.application

import io.ktor.client.plugins.ResponseException
import no.nav.syfo.application.client.IMotebehovClient
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.motebehov.Tilbakemelding
import org.slf4j.LoggerFactory

class MotebehovService(
    private val motebehovClient: IMotebehovClient,
    private val avventRepository: IAvventRepository,
    private val transactionManager: ITransactionManager,
) {
    suspend fun behandleMotebehov(
        personident: PersonIdent,
        harBehovForMote: Boolean,
        tilbakemeldinger: List<Tilbakemelding>,
        token: String,
    ) {
        motebehovClient.behandleMotebehov(
            personIdent = personident,
            token = token,
        )
        sendTilbakemeldinger(
            tilbakemeldinger = tilbakemeldinger,
            token = token,
        )
        if (!harBehovForMote) {
            transactionManager.run { transaction ->
                avventRepository.getActiveAvvent(personident, transaction)?.let {
                    avventRepository.setLukket(it.uuid, transaction)
                }
            }
        }
    }

    private suspend fun sendTilbakemeldinger(
        tilbakemeldinger: List<Tilbakemelding>,
        token: String,
    ) {
        tilbakemeldinger.forEach { tilbakemelding ->
            try {
                motebehovClient.sendTilbakemelding(
                    tilbakemelding = tilbakemelding,
                    token = token,
                )
            } catch (e: ResponseException) {
                log.error(
                    "Failed to send tilbakemelding with motebehovId ${tilbakemelding.motebehovId}. Response status: ${e.response.status}",
                    e,
                )
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(MotebehovService::class.java)
    }
}
