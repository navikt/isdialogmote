package no.nav.syfo.dialogmote.tilgang

import no.nav.syfo.client.person.adressebeskyttelse.AdressebeskyttelseClient
import no.nav.syfo.client.person.kontaktinfo.KontaktinformasjonClient
import no.nav.syfo.client.person.kontaktinfo.isDigitalVarselEnabled
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.util.callIdArgument
import org.slf4j.LoggerFactory

class DialogmoteTilgangService(
    private val adressebeskyttelseClient: AdressebeskyttelseClient,
    private val kontaktinformasjonClient: KontaktinformasjonClient,
    private val veilederTilgangskontrollClient: VeilederTilgangskontrollClient
) {
    suspend fun hasAccessToDialogmotePerson(
        personIdentNumber: PersonIdentNumber,
        token: String,
        callId: String,
    ): Boolean {
        val veilederHasAccessToPerson = veilederTilgangskontrollClient.hasAccess(personIdentNumber, token, callId)
        return if (veilederHasAccessToPerson) {
            val personHasAdressebeskyttelse =
                adressebeskyttelseClient.hasAdressebeskyttelse(
                    personIdentNumber = personIdentNumber,
                    token = token,
                    callId = callId,
                )
            !personHasAdressebeskyttelse
        } else false
    }

    suspend fun hasAccessToDialogmotePersonWithOBO(
        personIdentNumber: PersonIdentNumber,
        token: String,
        callId: String,
    ): Boolean {
        val veilederHasAccessToPerson = veilederTilgangskontrollClient.hasAccessWithOBO(
            personIdentNumber = personIdentNumber,
            token = token,
            callId = callId,
        )
        return if (veilederHasAccessToPerson) {
            val personHasAdressebeskyttelse =
                adressebeskyttelseClient.hasAdressebeskyttelseWithOBO(
                    personIdentNumber = personIdentNumber,
                    token = token,
                    callId = callId,
                )
            !personHasAdressebeskyttelse
        } else false
    }

    suspend fun hasAccessToAllDialogmotePersonsWithObo(
        personIdentNumberList: List<PersonIdentNumber>,
        token: String,
        callId: String
    ): Boolean {
        val personListWithVeilederAccess = hasAccessToDialogmotePersonListWithObo(
            personIdentNumberList = personIdentNumberList,
            token = token,
            callId = callId,
        )

        return personListWithVeilederAccess.containsAll(personIdentNumberList)
    }

    suspend fun hasAccessToDialogmotePersonListWithObo(
        personIdentNumberList: List<PersonIdentNumber>,
        token: String,
        callId: String,
    ): List<PersonIdentNumber> {
        return veilederTilgangskontrollClient.hasAccessToPersonListWithOBO(
            personIdentNumberList = personIdentNumberList,
            token = token,
            callId = callId,
        ).filter { personIdentNumber ->
            !adressebeskyttelseClient.hasAdressebeskyttelseWithOBO(personIdentNumber, token, callId)
        }
    }

    suspend fun hasAccessToDialogmotePersonWithDigitalVarselEnabledWithOBO(
        personIdentNumber: PersonIdentNumber,
        token: String,
        callId: String,
    ): Boolean {
        return if (hasAccessToDialogmotePersonWithOBO(personIdentNumber, token, callId)) {
            val kontaktinfo = kontaktinformasjonClient.kontaktinformasjonWithOBO(personIdentNumber, token, callId)
            val isDigitalVarselEnabled = kontaktinfo?.kontaktinfo?.isDigitalVarselEnabled(personIdentNumber)
            if (isDigitalVarselEnabled == false) {
                log.error("$DENIED_ACCESS_LOG_MESSAGE DigitalVarsel is not allowed, {}", callIdArgument(callId))
                false
            } else {
                isDigitalVarselEnabled ?: false
            }
        } else {
            log.warn("$DENIED_ACCESS_LOG_MESSAGE No access to person, {}", callIdArgument(callId))
            false
        }
    }

    suspend fun hasAccessToDialogmotePersonWithDigitalVarselEnabled(
        personIdentNumber: PersonIdentNumber,
        token: String,
        callId: String,
    ): Boolean {
        return if (hasAccessToDialogmotePerson(personIdentNumber, token, callId)) {
            val kontaktinfo = kontaktinformasjonClient.kontaktinformasjon(personIdentNumber, token, callId)
            val isDigitalVarselEnabled = kontaktinfo?.kontaktinfo?.isDigitalVarselEnabled(personIdentNumber)
            if (isDigitalVarselEnabled == false) {
                log.error("$DENIED_ACCESS_LOG_MESSAGE DigitalVarsel is not allowed, {}", callIdArgument(callId))
                false
            } else {
                isDigitalVarselEnabled ?: false
            }
        } else {
            log.warn("$DENIED_ACCESS_LOG_MESSAGE No access to person, {}", callIdArgument(callId))
            false
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(DialogmoteTilgangService::class.java)

        private const val DENIED_ACCESS_LOG_MESSAGE = "Denied access create or update DialogmoteInnkalling:"
    }
}
