package no.nav.syfo.brev.narmesteleder

import no.nav.melding.virksomhet.servicemeldingmedkontaktinformasjon.v1.servicemeldingmedkontaktinformasjon.*
import no.nav.syfo.application.mq.MQSenderInterface
import no.nav.syfo.client.narmesteleder.NarmesteLederRelasjonDTO
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
        narmesteLeder: NarmesteLederRelasjonDTO,
        varseltype: MotedeltakerVarselType
    ) {
        val parameterListe: MutableList<WSParameter> = ArrayList()
        parameterListe.add(createParameter("createdAt", createdAt.format(DateTimeFormatter.ISO_DATE_TIME)))
        parameterListe.add(
            createParameter(
                "tidspunkt", moteTidspunkt.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
            )
        )
        parameterListe.add(createParameter("navn", narmesteLeder.narmesteLederNavn ?: "nærmeste leder"))

        val melding = opprettServiceMelding(narmesteLeder, varseltype, parameterListe)
        val xmlString = marshallServiceMelding(ObjectFactory().createServicemelding(melding))
        mqSender.sendMQMessage(varseltype, xmlString)
    }

    private fun opprettServiceMelding(
        narmesteLeder: NarmesteLederRelasjonDTO,
        varseltype: MotedeltakerVarselType,
        parametere: List<WSParameter>
    ): WSServicemeldingMedKontaktinformasjon {
        return WSServicemeldingMedKontaktinformasjon().apply {
            mottaker = personIdent(narmesteLeder.narmesteLederPersonIdentNumber)
            tilhoerendeOrganisasjon = organisasjon(narmesteLeder.virksomhetsnummer)
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
            MotedeltakerVarselType.REFERAT, MotedeltakerVarselType.REFERAT_ENDRET -> NarmesteLederVarselType.NARMESTE_LEDER_REFERAT
        }
    }

    private fun kontaktinformasjon(narmesteLeder: NarmesteLederRelasjonDTO): List<WSKontaktinformasjon> {
        return listOf(
            opprettKontaktinformasjon(narmesteLeder.narmesteLederEpost, "EPOST"),
            opprettKontaktinformasjon(narmesteLeder.narmesteLederTelefonnummer, "SMS")
        )
    }

    private fun opprettKontaktinformasjon(kontaktinfo: String, type: String): WSKontaktinformasjon {
        val kanal = WSKommunikasjonskanaler().apply { value = type }
        return WSKontaktinformasjon(kanal, kontaktinfo)
    }

    private fun personIdent(fnr: String): WSPerson {
        return WSPerson(fnr)
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
