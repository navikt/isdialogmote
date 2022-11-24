package no.nav.syfo.client.altinn

import java.util.*
import no.nav.syfo.dialogmote.domain.MotedeltakerVarselType
import no.nav.syfo.domain.Virksomhetsnummer
import no.nav.syfo.testhelper.UserConstants
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object AltinnUtilSpek : Spek({

    describe("Object creator puts values in correct place") {

        it("Gives correct output from given input") {
            val brevId = UUID.randomUUID()
            val brev = byteArrayOf(0x2E, 0x38)
            val virksomhetsnummer = Virksomhetsnummer("123456785")
            val expectedInnkallingstittel = "Innkalling til dialogmøte - ${UserConstants.ARBEIDSTAKERNAVN} (${UserConstants.ARBEIDSTAKER_FNR.value})"

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

            mappedObject.reportee shouldBeEqualTo virksomhetsnummer.value
            mappedObject.content.attachments.binaryAttachments.binaryAttachmentV2.first().sendersReference shouldBeEqualTo "$brevId.pdf"
            mappedObject.content.attachments.binaryAttachments.binaryAttachmentV2.first().data shouldBeEqualTo brev
            mappedObject.content.messageTitle shouldBeEqualTo expectedInnkallingstittel
        }
    }
})
