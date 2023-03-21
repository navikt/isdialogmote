package no.nav.syfo.brev.esyfovarsel

import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.io.*

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
data class DialogmoteInnkallingArbeidatakerData(
    val varselUuid: String
) : Serializable
