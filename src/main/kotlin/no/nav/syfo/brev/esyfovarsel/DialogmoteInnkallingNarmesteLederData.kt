package no.nav.syfo.brev.esyfovarsel

import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.io.*

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
data class DialogmoteInnkallingNarmesteLederData(
    val narmesteLederNavn: String?,
) : Serializable
