package no.nav.syfo.client.person.kontaktinfo

import no.nav.syfo.domain.PersonIdentNumber
import java.io.Serializable

data class DigitalKontaktinfoBolk(
    val feil: Map<String, Feil>? = null,
    val personer: Map<String, DigitalKontaktinfo>? = null
) : Serializable

data class DigitalKontaktinfo(
    val epostadresse: String? = null,
    val aktiv: Boolean,
    val kanVarsles: Boolean? = null,
    val reservert: Boolean? = null,
    val mobiltelefonnummer: String? = null,
    val personident: String,
) : Serializable

data class Feil(
    val melding: String
) : Serializable

fun Map<String, DigitalKontaktinfo>.isDigitalVarselEnabled(
    personIdentNumber: PersonIdentNumber,
) = this[personIdentNumber.value]?.kanVarsles ?: false
