package no.nav.syfo.infrastructure.client.person.kontaktinfo

import no.nav.syfo.domain.PersonIdent
import java.io.Serializable

data class DigitalKontaktinfoBolk(
    val feil: Map<String, String>? = null,
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

fun Map<String, DigitalKontaktinfo>.isDigitalVarselEnabled(
    personIdent: PersonIdent,
) = this[personIdent.value]?.kanVarsles ?: false
