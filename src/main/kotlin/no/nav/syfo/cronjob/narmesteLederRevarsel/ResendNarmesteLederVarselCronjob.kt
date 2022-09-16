package no.nav.syfo.cronjob.narmesteLederRevarsel

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.brev.narmesteleder.NarmesteLederVarselService
import no.nav.syfo.client.narmesteleder.NarmesteLederClient
import no.nav.syfo.cronjob.DialogmoteCronjob
import no.nav.syfo.dialogmote.database.getMotedeltakerArbeidsgiverReferatVarsel
import no.nav.syfo.dialogmote.database.getMotedeltakerArbeidsgiverVarsel
import no.nav.syfo.dialogmote.domain.MotedeltakerVarselType
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.Virksomhetsnummer
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

class ResendNarmesteLederVarselCronjob(
    private val narmesteLederClient: NarmesteLederClient,
    private val narmesteLederVarselService: NarmesteLederVarselService,
    private val database: DatabaseInterface,
) : DialogmoteCronjob {
    override val initialDelayMinutes: Long = 4
    override val intervalDelayMinutes: Long = 9999999

    override suspend fun run() {
        val resendResult = ResendCronjobResult()

        resendVarsler(resendResult)

        log.info(
            "RESEND-TRACE: Completed resending of varsler with result: {}, {}, {}",
            StructuredArguments.keyValue("failed", resendResult.failed),
            StructuredArguments.keyValue("updated", resendResult.updated),
            StructuredArguments.keyValue("no leder", resendResult.noLeder),
        )
    }

    private suspend fun resendVarsler(
        resendResult: ResendCronjobResult
    ) {
        val startDateTime = LocalDateTime.of(2022, 9, 12, 22, 21, 42)
        val endDateTime = LocalDateTime.of(2022, 9, 13, 15, 11, 33)

        val varslerToResend = database.getMotedeltakerArbeidsgiverVarsel(
            startDateTime = startDateTime,
            endDateTime = endDateTime,
        )
        log.info("RESEND-TRACE: Found ${varslerToResend.size} varsler.")

        val referaterToResend = database.getMotedeltakerArbeidsgiverReferatVarsel(
            startDateTime = startDateTime,
            endDateTime = endDateTime,
        )
        log.info("RESEND-TRACE: Found ${referaterToResend.size} referater.")

        val completeList = varslerToResend.plus(referaterToResend)
        log.info("RESEND-TRACE: Found ${completeList.size} total varsler.")

        completeList.forEach { varsel ->
            try {
                val narmesteLeder = narmesteLederClient.activeLederSystemToken(
                    personIdentNumber = PersonIdentNumber(varsel.second),
                    virksomhetsnummer = Virksomhetsnummer(varsel.third),
                )
                if (narmesteLeder == null) {
                    log.info("RESEND-TRACE: Didn't find Narmeste leder for virksomhet ${varsel.third}")
                    resendResult.noLeder++
                } else {
                    narmesteLederVarselService.sendVarsel(narmesteLeder, MotedeltakerVarselType.valueOf(varsel.first), false)
                    resendResult.updated++
                }
            } catch (e: Exception) {
                log.info("RESEND-TRACE: Varsel for virksomhet ${varsel.third}} failed, e")
                resendResult.failed++
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ResendNarmesteLederVarselCronjob::class.java)
    }
}

data class ResendCronjobResult(
    var updated: Int = 0,
    var failed: Int = 0,
    var noLeder: Int = 0,
)
