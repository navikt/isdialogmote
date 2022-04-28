package no.nav.syfo.util

import io.ktor.http.HttpHeaders.Authorization
import io.ktor.server.application.*
import io.ktor.util.pipeline.*
import net.logstash.logback.argument.StructuredArguments

const val NAV_PERSONIDENT_HEADER = "nav-personident"
const val TEMA_HEADER = "Tema"
const val ALLE_TEMA_HEADERVERDI = "GEN"

const val NAV_CALL_ID_HEADER = "Nav-Call-Id"
fun PipelineContext<out Unit, ApplicationCall>.getCallId(): String {
    return this.call.getCallId()
}

fun ApplicationCall.getCallId(): String {
    return this.request.headers[NAV_CALL_ID_HEADER].toString()
}

fun callIdArgument(callId: String) = StructuredArguments.keyValue("callId", callId)!!

const val NAV_CONSUMER_ID_HEADER = "Nav-Consumer-Id"
fun ApplicationCall.getConsumerId(): String {
    return this.request.headers[NAV_CONSUMER_ID_HEADER].toString()
}

private fun ApplicationCall.getHeader(header: String): String? {
    return this.request.headers[header]
}

fun ApplicationCall.getBearerHeader(): String? {
    return getHeader(Authorization)?.removePrefix("Bearer ")
}

fun PipelineContext<out Unit, ApplicationCall>.getBearerHeader(): String? {
    return this.call.getBearerHeader()
}

fun PipelineContext<out Unit, ApplicationCall>.getPersonIdentHeader(): String? {
    return this.call.getHeader(NAV_PERSONIDENT_HEADER)
}
