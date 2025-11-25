package no.nav.syfo.client.altinn

import no.nav.syfo.domain.Virksomhetsnummer
import no.nav.syfo.domain.dialogmote.MotedeltakerVarselType
import no.nav.syfo.infrastructure.client.altinn.createAltinnMelding
import no.nav.syfo.infrastructure.client.altinn.mapToInsertCorrespondenceV2WS
import no.nav.syfo.testhelper.UserConstants
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.*

class AltinnUtilTest {

    @Test
    fun `Gives correct output from given input`() {
        val brevId = UUID.randomUUID()
        val brev = byteArrayOf(0x2E, 0x38)
        val virksomhetsnummer = Virksomhetsnummer("123456785")
        val expectedInnkallingstittel =
            "Innkalling til dialogmøte - ${UserConstants.ARBEIDSTAKERNAVN} (${UserConstants.ARBEIDSTAKER_FNR.value})"

        val altinnMelding = createAltinnMelding(
            brevId,
            virksomhetsnummer,
            brev,
            MotedeltakerVarselType.INNKALT,
            UserConstants.ARBEIDSTAKER_FNR,
            UserConstants.ARBEIDSTAKERNAVN,
            false
        )

        val mappedObject = mapToInsertCorrespondenceV2WS(altinnMelding)

        assertEquals(virksomhetsnummer.value, mappedObject.reportee)
        assertEquals(
            "$brevId.pdf",
            mappedObject.content.attachments.binaryAttachments.binaryAttachmentV2.first().sendersReference
        )
        assertEquals(brev, mappedObject.content.attachments.binaryAttachments.binaryAttachmentV2.first().data)
        assertEquals(expectedInnkallingstittel, mappedObject.content.messageTitle)
    }
}
