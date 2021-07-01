package no.nav.syfo.brev.narmesteleder

import no.nav.melding.virksomhet.servicemeldingmedkontaktinformasjon.v1.servicemeldingmedkontaktinformasjon.*
import no.nav.syfo.application.mq.MQSenderInterface
import no.nav.syfo.client.narmesteleder.NarmesteLederDTO
import no.nav.syfo.dialogmote.domain.MotedeltakerVarselType
import java.io.StringWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.xml.bind.JAXBContext
import javax.xml.bind.Marshaller

class NarmesteLederVarselService(
    private val mqSender: MQSenderInterface
) {
    fun sendVarsel(
        createdAt: LocalDateTime,
        moteTidspunkt: LocalDateTime,
        narmesteLeder: NarmesteLederDTO,
        varseltype: MotedeltakerVarselType
    ) {
        val parameterListe: MutableList<WSParameter> = ArrayList()
        parameterListe.add(createParameter("createdAt", createdAt.format(DateTimeFormatter.ISO_DATE_TIME)))
        parameterListe.add(createParameter("navn", narmesteLeder.navn))
        parameterListe.add(
            createParameter("tidspunkt", moteTidspunkt.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")))
        )

        val melding = opprettServiceMelding(narmesteLeder, varseltype, parameterListe)
        val xmlString = marshallServiceMelding(ObjectFactory().createServicemelding(melding))
        mqSender.sendMQMessage(varseltype, xmlString)
    }

    private fun opprettServiceMelding(
        narmesteLeder: NarmesteLederDTO,
        varseltype: MotedeltakerVarselType,
        parametere: List<WSParameter>
    ): WSServicemeldingMedKontaktinformasjon {
        return WSServicemeldingMedKontaktinformasjon().apply {
            mottaker = aktoer(narmesteLeder.aktoerId)
            tilhoerendeOrganisasjon = organisasjon(narmesteLeder.orgnummer)
            varseltypeId = getNaermesteLederVarselType(varseltype).id
            parameterListe.addAll(parametere)
            kontaktinformasjonListe.addAll(kontaktinformasjon(narmesteLeder))
        }
    }

    private fun getNaermesteLederVarselType(motedeltakerVarselType: MotedeltakerVarselType): NarmesteLederVarselType {
        return when (motedeltakerVarselType) {
            MotedeltakerVarselType.INNKALT -> NarmesteLederVarselType.NARMESTE_LEDER_MOTE_INNKALT
            MotedeltakerVarselType.AVLYST -> NarmesteLederVarselType.NARMESTE_LEDER_MOTE_AVLYST
            MotedeltakerVarselType.NYTT_TID_STED -> NarmesteLederVarselType.NARMESTE_LEDER_MOTE_NYTID
            MotedeltakerVarselType.REFERAT -> NarmesteLederVarselType.NARMESTE_LEDER_REFERAT
        }
    }

    private fun kontaktinformasjon(narmesteLeder: NarmesteLederDTO): List<WSKontaktinformasjon> {
        return listOf(
            opprettKontaktinformasjon(narmesteLeder.epost, "EPOST"),
            opprettKontaktinformasjon(narmesteLeder.tlf, "SMS")
        )
    }

    private fun opprettKontaktinformasjon(kontaktinfo: String?, type: String): WSKontaktinformasjon {
        val kanal = WSKommunikasjonskanaler().apply { value = type }
        return WSKontaktinformasjon(kanal, kontaktinfo ?: "")
    }

    private fun aktoer(aktoerId: String): WSAktoer {
        return WSAktoerId(aktoerId)
    }

    private fun organisasjon(orgnummer: String): WSOrganisasjon {
        return WSOrganisasjon(orgnummer)
    }

    private fun marshallServiceMelding(element: Any): String {
        val marshaller = JAXBContext.newInstance(WSServicemeldingMedKontaktinformasjon::class.java).createMarshaller()
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, false)
        StringWriter().use { writer ->
            marshaller.marshal(element, writer)
            return writer.toString()
        }
    }

    private fun createParameter(key: String, value: String): WSParameter {
        return WSParameter(key, value)
    }
}
