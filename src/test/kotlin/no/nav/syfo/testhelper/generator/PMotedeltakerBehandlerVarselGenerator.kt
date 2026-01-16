package no.nav.syfo.testhelper.generator

import no.nav.syfo.infrastructure.database.model.PMotedeltakerBehandlerVarsel
import no.nav.syfo.domain.dialogmote.MotedeltakerVarselType
import java.time.LocalDateTime
import java.util.*

fun generatePMotedeltakerBehandlerVarsel() = PMotedeltakerBehandlerVarsel(
    id = 1,
    uuid = UUID.randomUUID(),
    createdAt = LocalDateTime.now(),
    updatedAt = LocalDateTime.now(),
    motedeltakerBehandlerId = 2,
    varselType = MotedeltakerVarselType.INNKALT,
    pdfId = 3,
    status = "status",
    fritekst = "",
    document = emptyList(),
)
