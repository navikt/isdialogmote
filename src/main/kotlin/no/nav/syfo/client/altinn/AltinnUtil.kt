package no.nav.syfo.client.altinn

import no.altinn.schemas.serviceengine.formsengine._2009._10.TransportType
import no.altinn.schemas.services.serviceengine.correspondence._2010._10.AttachmentsV2
import no.altinn.schemas.services.serviceengine.correspondence._2010._10.ExternalContentV2
import no.altinn.schemas.services.serviceengine.correspondence._2010._10.InsertCorrespondenceV2
import no.altinn.schemas.services.serviceengine.correspondence._2010._10.UserTypeRestriction
import no.altinn.schemas.services.serviceengine.notification._2009._10.*
import no.altinn.schemas.services.serviceengine.subscription._2009._10.AttachmentFunctionType
import no.altinn.services.serviceengine.reporteeelementlist._2010._10.BinaryAttachmentExternalBEV2List
import no.altinn.services.serviceengine.reporteeelementlist._2010._10.BinaryAttachmentV2
import java.lang.Boolean.FALSE

private const val DIALOGMOTE_TJENESTEKODE =
    "5793" // OBS! VIKTIG! Denne må ikke endres, da kan feil personer få tilgang til innkallinger i Altinn!
private const val DIALOGMOTE_TJENESTEVERSJON = "1"
private const val NORSK_BOKMAL = "1044"
private const val AVSENDER_NAV = "NAV"
private const val FRA_EPOST_ALTINN = "noreply@altinn.no"
private const val NOTIFICATION_TYPE = "TokenTextOnly"

fun mapToInsertCorrespondenceV2WS(
    altinnMelding: AltinnMelding,
): InsertCorrespondenceV2 {
    val insertCorrespondenceV2 = InsertCorrespondenceV2()
        .withAllowForwarding(FALSE)
        .withReportee(altinnMelding.virksomhetsnummer.value)
        .withMessageSender(AVSENDER_NAV)
        .withServiceCode(DIALOGMOTE_TJENESTEKODE)
        .withServiceEdition(DIALOGMOTE_TJENESTEVERSJON)
        .withContent(
            ExternalContentV2()
                .withLanguageCode(NORSK_BOKMAL)
                .withMessageTitle(altinnMelding.title)
                .withMessageBody(altinnMelding.body)
                .withCustomMessageData(null)
                .withAttachments(
                    AttachmentsV2()
                        .withBinaryAttachments(
                            BinaryAttachmentExternalBEV2List()
                                .withBinaryAttachmentV2(
                                    createBinaryAttachment(
                                        fil = altinnMelding.file,
                                        filnavn = altinnMelding.filename,
                                        navn = altinnMelding.displayFilename,
                                        sendersRef = "${altinnMelding.reference}.pdf"
                                    ),
                                )
                        )
                )
        ).withArchiveReference(null)

    if (!altinnMelding.hasNarmesteLeder) {
        insertCorrespondenceV2.withNotifications(createNotifications(altinnMelding))
    }

    return insertCorrespondenceV2
}

fun createNotifications(altinnMelding: AltinnMelding): NotificationBEList {
    return NotificationBEList()
        .withNotification(
            epostNotification(altinnMelding.emailTitle, altinnMelding.emailBody),
            smsNotification(altinnMelding.smsBody, altinnMelding.smsSender)
        )
}

private fun epostNotification(emailTitle: String, emailBody: String): Notification {
    return createNotification(FRA_EPOST_ALTINN, TransportType.EMAIL, convertToTextTokens(emailTitle, emailBody))
}

private fun smsNotification(smsNotification: String, smsSender: String): Notification {
    return createNotification(null, TransportType.SMS, convertToTextTokens(smsNotification, smsSender))
}

private fun createNotification(fromEmail: String?, type: TransportType, textTokens: Array<TextToken?>): Notification {
    if (textTokens.size != 2) {
        throw IllegalArgumentException("Antall textTokens må være 2. Var ${textTokens.size}")
    }
    return Notification()
        .withLanguageCode(NORSK_BOKMAL)
        .withNotificationType(NOTIFICATION_TYPE)
        .withFromAddress(fromEmail)
        .withReceiverEndPoints(
            ReceiverEndPointBEList()
                .withReceiverEndPoint(
                    ReceiverEndPoint()
                        .withTransportType(
                            type
                        )
                )
        )
        .withTextTokens(
            TextTokenSubstitutionBEList().withTextToken(
                *textTokens
            )
        )
}

private fun convertToTextTokens(vararg text: String): Array<TextToken?> {
    val textTokens = arrayOfNulls<TextToken>(text.size)
    for (i in text.indices) {
        textTokens[i] = TextToken().withTokenNum(i).withTokenValue(
            text[i]
        )
    }
    return textTokens
}

private fun createBinaryAttachment(
    fil: ByteArray,
    filnavn: String,
    navn: String,
    sendersRef: String,
): BinaryAttachmentV2? {
    return BinaryAttachmentV2()
        .withDestinationType(UserTypeRestriction.SHOW_TO_ALL)
        .withFileName(filnavn)
        .withName(navn)
        .withFunctionType(AttachmentFunctionType.UNSPECIFIED)
        .withEncrypted(false)
        .withSendersReference(sendersRef)
        .withData(fil)
}
