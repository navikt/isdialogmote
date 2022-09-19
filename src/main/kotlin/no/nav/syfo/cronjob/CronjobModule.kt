package no.nav.syfo.cronjob

import io.ktor.server.application.*
import no.nav.syfo.application.*
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.mq.MQSenderInterface
import no.nav.syfo.brev.narmesteleder.NarmesteLederVarselService
import no.nav.syfo.brev.narmesteleder.dinesykmeldte.DineSykmeldteVarselProducer
import no.nav.syfo.client.azuread.AzureAdV2Client
import no.nav.syfo.client.dokarkiv.DokarkivClient
import no.nav.syfo.client.ereg.EregClient
import no.nav.syfo.client.journalpostdistribusjon.JournalpostdistribusjonClient
import no.nav.syfo.client.narmesteleder.NarmesteLederClient
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.client.tokendings.TokendingsClient
import no.nav.syfo.cronjob.journalforing.DialogmoteVarselJournalforingCronjob
import no.nav.syfo.cronjob.journalpostdistribusjon.DialogmoteJournalpostDistribusjonCronjob
import no.nav.syfo.cronjob.leaderelection.LeaderPodClient
import no.nav.syfo.cronjob.narmesteLederRevarsel.ResendNarmesteLederVarselCronjob
import no.nav.syfo.cronjob.statusendring.*
import no.nav.syfo.dialogmote.*
import no.nav.syfo.dialogmote.avro.KDialogmoteStatusEndring
import org.apache.kafka.clients.producer.KafkaProducer

fun Application.cronjobModule(
    applicationState: ApplicationState,
    database: DatabaseInterface,
    environment: Environment,
    cache: RedisStore,
    dineSykmeldteVarselProducer: DineSykmeldteVarselProducer,
    mqSender: MQSenderInterface,
) {
    val azureAdV2Client = AzureAdV2Client(
        aadAppClient = environment.aadAppClient,
        aadAppSecret = environment.aadAppSecret,
        aadTokenEndpoint = environment.aadTokenEndpoint,
        redisStore = cache,
    )
    val dokarkivClient = DokarkivClient(
        azureAdV2Client = azureAdV2Client,
        dokarkivClientId = environment.dokarkivClientId,
        dokarkivBaseUrl = environment.dokarkivUrl,
    )
    val journalpostdistribusjonClient = JournalpostdistribusjonClient(
        azureAdV2Client = azureAdV2Client,
        dokdistFordelingClientId = environment.dokdistFordelingClientId,
        dokdistFordelingBaseUrl = environment.dokdistFordelingUrl,
    )
    val pdlClient = PdlClient(
        azureAdV2Client = azureAdV2Client,
        pdlClientId = environment.pdlClientId,
        pdlUrl = environment.pdlUrl,
    )
    val eregClient = EregClient(
        baseUrl = environment.eregUrl,
    )
    val pdfService = PdfService(
        database = database,
    )
    val dialogmotedeltakerVarselJournalpostService = DialogmotedeltakerVarselJournalpostService(
        database = database,
    )
    val referatJournalpostService = ReferatJournalpostService(
        database = database,
    )
    val leaderPodClient = LeaderPodClient(
        environment = environment,
    )
    val journalforDialogmoteVarslerCronjob = DialogmoteVarselJournalforingCronjob(
        dialogmotedeltakerVarselJournalpostService = dialogmotedeltakerVarselJournalpostService,
        referatJournalpostService = referatJournalpostService,
        pdfService = pdfService,
        dokarkivClient = dokarkivClient,
        pdlClient = pdlClient,
        eregClient = eregClient,
    )

    val narmesteLederVarselService = NarmesteLederVarselService(
        mqSender = mqSender,
        dineSykmeldteVarselProducer = dineSykmeldteVarselProducer,
    )

    val tokendingsClient = TokendingsClient(
        tokenxClientId = environment.tokenxClientId,
        tokenxEndpoint = environment.tokenxEndpoint,
        tokenxPrivateJWK = environment.tokenxPrivateJWK,
    )

    val narmesteLederClient = NarmesteLederClient(
        narmesteLederBaseUrl = environment.narmestelederUrl,
        narmestelederClientId = environment.narmestelederClientId,
        azureAdV2Client = azureAdV2Client,
        tokendingsClient = tokendingsClient,
        cache = cache,
    )

    val resendNarmesteLederVarselCronjob = ResendNarmesteLederVarselCronjob(
        narmesteLederClient = narmesteLederClient,
        narmesteLederVarselService = narmesteLederVarselService,
        database = database,
    )

    val kafkaDialogmoteStatusEndringProducerProperties = kafkaDialogmoteStatusEndringProducerConfig(environment.kafka)
    val kafkaDialogmoteStatusEndringProducer = KafkaProducer<String, KDialogmoteStatusEndring>(
        kafkaDialogmoteStatusEndringProducerProperties
    )

    val dialogmoteStatusEndringProducer = DialogmoteStatusEndringProducer(
        kafkaDialogmoteStatusEndringProducer = kafkaDialogmoteStatusEndringProducer,
    )
    val publishDialogmoteStatusEndringService = PublishDialogmoteStatusEndringService(
        database = database,
        dialogmoteStatusEndringProducer = dialogmoteStatusEndringProducer,
    )
    val publishDialogmoteStatusEndringCronjob = PublishDialogmoteStatusEndringCronjob(
        publishDialogmoteStatusEndringService = publishDialogmoteStatusEndringService,
    )
    val cronjobRunner = DialogmoteCronjobRunner(
        applicationState = applicationState,
        leaderPodClient = leaderPodClient
    )
    val journalpostDistribusjonCronjob = DialogmoteJournalpostDistribusjonCronjob(
        dialogmotedeltakerVarselJournalpostService = dialogmotedeltakerVarselJournalpostService,
        referatJournalpostService = referatJournalpostService,
        journalpostdistribusjonClient = journalpostdistribusjonClient
    )

    launchBackgroundTask(
        applicationState = applicationState,
    ) {
        cronjobRunner.start(cronjob = journalforDialogmoteVarslerCronjob)
    }
    launchBackgroundTask(
        applicationState = applicationState,
    ) {
        cronjobRunner.start(cronjob = publishDialogmoteStatusEndringCronjob)
    }
    launchBackgroundTask(
        applicationState = applicationState
    ) {
        cronjobRunner.start(cronjob = journalpostDistribusjonCronjob)
    }

    if (environment.resendNarmesteLederVarselEnabled) {
        launchBackgroundTask(
            applicationState = applicationState
        ) {
            cronjobRunner.start(cronjob = resendNarmesteLederVarselCronjob)
        }
    }
}
