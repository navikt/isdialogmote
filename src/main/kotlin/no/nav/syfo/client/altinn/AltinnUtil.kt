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
import no.nav.syfo.domain.Virksomhetsnummer
import java.lang.Boolean.FALSE
import java.util.*

private const val DIALOGMOTE_TJENESTEKODE =
    "5793" // OBS! VIKTIG! Denne må ikke endres, da kan feil personer få tilgang til innkallinger i Altinn!
private const val DIALOGMOTE_TJENESTEVERSJON = "1"
private const val NORSK_BOKMAL = "1044"
private const val AVSENDER_NAV = "NAV"
private const val MESSAGE_BODY = "Det er ikke registrert noen nærmeste leder for denne arbeidstakeren. Det " +
    "må registreres en leder, og lederen må deretter gå inn på Dine Sykmeldte på nav.no for å svare på " +
    "innkallingen."
private const val FRA_EPOST_ALTINN = "noreply@altinn.no"
private const val NOTIFICATION_TYPE = "TokenTextOnly"

fun createVirksomhetsBrevAltinnWSRequest(
    brevUuid: UUID,
    brev: ByteArray,
    virksomhetsnummer: Virksomhetsnummer,
): InsertCorrespondenceV2 {

    return InsertCorrespondenceV2()
        .withAllowForwarding(FALSE)
        .withReportee(virksomhetsnummer.value)
        .withMessageSender(AVSENDER_NAV)
        .withServiceCode(DIALOGMOTE_TJENESTEKODE)
        .withServiceEdition(DIALOGMOTE_TJENESTEVERSJON)
        .withNotifications(createNotifications())
        .withContent(
            ExternalContentV2()
                .withLanguageCode(NORSK_BOKMAL)
                .withMessageTitle("Dialogmøte hos NAV")
                .withMessageBody(MESSAGE_BODY)
                .withCustomMessageData(null)
                .withAttachments(
                    AttachmentsV2()
                        .withBinaryAttachments(
                            BinaryAttachmentExternalBEV2List()
                                .withBinaryAttachmentV2(
                                    createBinaryAttachment(
                                        brev,
                                        "moteinkalling.pdf",
                                        "Møteinnkalling",
                                        "$brevUuid.pdf"
                                    ),
                                )
                        )
                )
        ).withArchiveReference(null)
}

fun createNotifications(): NotificationBEList {
    return NotificationBEList()
        .withNotification(epostNotification(), smsNotification())
}

private fun epostNotification(): Notification {
    val title = "Innkalling til dialogmøte "
    val body = "<p>En ansatt i \$reporteeName$ (\$reporteeNumber$) skal ha dialogmøte.</p>" +
        "<p>Vennlig hilsen NAV.</p>"
    return createNotification(FRA_EPOST_ALTINN, TransportType.EMAIL, convertToTextTokens(title, body))
}

private fun smsNotification(): Notification {
    val infoText = "En ansatt i \$reporteeName$ (\$reporteeNumber$) skal ha dialogmøte. "
    val avsenderText = "Vennlig hilsen NAV."
    return createNotification(null, TransportType.SMS, convertToTextTokens(infoText, avsenderText))
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
