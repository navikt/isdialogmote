package no.nav.syfo.brev.narmesteleder

import no.nav.melding.virksomhet.servicemeldingmedkontaktinformasjon.v1.servicemeldingmedkontaktinformasjon.*
import no.nav.syfo.application.mq.MQSenderInterface
import no.nav.syfo.client.narmesteleder.NarmesteLederRelasjonDTO
import no.nav.syfo.brev.narmesteleder.dinesykmeldte.DineSykmeldteVarselProducer
import no.nav.syfo.brev.narmesteleder.domain.DineSykmeldteHendelse
import no.nav.syfo.brev.narmesteleder.domain.OpprettHendelse
import no.nav.syfo.dialogmote.domain.MotedeltakerVarselType
import no.nav.syfo.dialogmote.domain.toDineSykmeldteVarselTekst
import java.io.StringWriter
import java.time.OffsetDateTime
import java.util.*
import javax.xml.bind.JAXBContext
import javax.xml.bind.Marshaller

class NarmesteLederVarselService(
    private val mqSender: MQSenderInterface,
    private val dineSykmeldteVarselProducer: DineSykmeldteVarselProducer,
) {
    fun sendVarsel(
        narmesteLeder: NarmesteLederRelasjonDTO,
        varseltype: MotedeltakerVarselType,
        sendToDineSykmeldte: Boolean = true,
    ) {
        val parameterListe: MutableList<WSParameter> = ArrayList()
        parameterListe.add(createParameter("navn", narmesteLeder.narmesteLederNavn ?: "nærmeste leder"))

        val melding = opprettServiceMelding(narmesteLeder, varseltype, parameterListe)
        val xmlString = marshallServiceMelding(ObjectFactory().createServicemelding(melding))
        mqSender.sendMQMessage(varseltype, xmlString)

        if (sendToDineSykmeldte) {
            sendVarselTilDineSykmeldte(narmesteLeder, varseltype)
        }
    }

    private fun sendVarselTilDineSykmeldte(
        narmesteLeder: NarmesteLederRelasjonDTO,
        varseltype: MotedeltakerVarselType
    ) {
        val dineSykmeldteOpprettHendelse = OpprettHendelse(
            ansattFnr = narmesteLeder.arbeidstakerPersonIdentNumber,
            orgnummer = narmesteLeder.virksomhetsnummer,
            oppgavetype = getDineSykmeldteOppgavetype(varseltype).name,
            tekst = varseltype.toDineSykmeldteVarselTekst(),
            timestamp = OffsetDateTime.now(),
            utlopstidspunkt = OffsetDateTime.now().plusWeeks(4),
            lenke = null,
        )

        val dineSykmeldteHendelse = DineSykmeldteHendelse(
            id = UUID.randomUUID().toString(),
            opprettHendelse = dineSykmeldteOpprettHendelse,
            ferdigstillHendelse = null
        )

        dineSykmeldteVarselProducer.sendDineSykmeldteVarsel(dineSykmeldteHendelse.id, dineSykmeldteHendelse)
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
            MotedeltakerVarselType.REFERAT -> NarmesteLederVarselType.NARMESTE_LEDER_REFERAT
        }
    }

    private fun getDineSykmeldteOppgavetype(motedeltakerVarselType: MotedeltakerVarselType): DineSykmeldteOppgavetype {
        return when (motedeltakerVarselType) {
            MotedeltakerVarselType.INNKALT -> DineSykmeldteOppgavetype.DIALOGMOTE_INNKALLING
            MotedeltakerVarselType.AVLYST -> DineSykmeldteOppgavetype.DIALOGMOTE_AVLYSNING
            MotedeltakerVarselType.NYTT_TID_STED -> DineSykmeldteOppgavetype.DIALOGMOTE_ENDRING
            MotedeltakerVarselType.REFERAT -> DineSykmeldteOppgavetype.DIALOGMOTE_REFERAT
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
