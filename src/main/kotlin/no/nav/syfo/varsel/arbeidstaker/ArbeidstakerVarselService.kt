package no.nav.syfo.varsel.arbeidstaker

import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.syfo.varsel.VarselType
import no.nav.syfo.varsel.arbeidstaker.brukernotifikasjon.BrukernotifikasjonProducer
import java.net.URL

class ArbeidstakerVarselService(
    private val brukernotifikasjonProducer: BrukernotifikasjonProducer,
    private val dialogmoteArbeidstakerUrl: String,
    private val serviceuserUsername: String,
) {
    fun sendVarsel(
        arbeidstakerVarsel: ArbeidstakerVarsel,
    ) {
        if (arbeidstakerVarsel.type == VarselType.INNKALT) {
            val nokkel = Nokkel(
                serviceuserUsername,
                arbeidstakerVarsel.uuid.toString()
            )
            val oppgave = arbeidstakerVarsel.toBrukernotifikasjonOppgave(
                tekst = "Du har mottatt en innkalling til Dialogmøte",
                link = URL(dialogmoteArbeidstakerUrl),
            )
            brukernotifikasjonProducer.sendOppgave(
                nokkel,
                oppgave,
            )
        }
    }

    fun lesVarsel(
        arbeidstakerVarsel: ArbeidstakerVarsel,
    ) {
        if (arbeidstakerVarsel.type == VarselType.INNKALT) {
            val nokkel = Nokkel(
                serviceuserUsername,
                arbeidstakerVarsel.uuid.toString()
            )
            val done = arbeidstakerVarsel.toBrukernotifikasjonDone()
            brukernotifikasjonProducer.sendDone(
                nokkel,
                done,
            )
        }
    }
}
