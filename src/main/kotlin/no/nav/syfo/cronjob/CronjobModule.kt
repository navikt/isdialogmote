package no.nav.syfo.cronjob

import io.ktor.server.application.*
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.launchBackgroundTask
import no.nav.syfo.brev.arbeidstaker.ArbeidstakerVarselService
import no.nav.syfo.brev.arbeidstaker.brukernotifikasjon.BrukernotifikasjonProducer
import no.nav.syfo.client.azuread.AzureAdV2Client
import no.nav.syfo.client.dokarkiv.DokarkivClient
import no.nav.syfo.client.ereg.EregClient
import no.nav.syfo.client.journalpostdistribusjon.JournalpostdistribusjonClient
import no.nav.syfo.client.oppfolgingstilfelle.OppfolgingstilfelleClient
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.client.tokendings.TokendingsClient
import no.nav.syfo.cronjob.dialogmoteOutdated.DialogmoteOutdatedCronjob
import no.nav.syfo.cronjob.dialogmotesvar.DialogmotesvarProducer
import no.nav.syfo.cronjob.dialogmotesvar.PublishDialogmotesvarCronjob
import no.nav.syfo.cronjob.dialogmotesvar.PublishDialogmotesvarService
import no.nav.syfo.cronjob.dialogmotesvar.kafkaDialogmotesvarProducerConfig
import no.nav.syfo.cronjob.journalforing.DialogmoteVarselJournalforingCronjob
import no.nav.syfo.cronjob.journalpostdistribusjon.DialogmoteJournalpostDistribusjonCronjob
import no.nav.syfo.cronjob.leaderelection.LeaderPodClient
import no.nav.syfo.cronjob.statusendring.*
import no.nav.syfo.dialogmote.*
import no.nav.syfo.dialogmote.api.domain.KDialogmotesvar
import no.nav.syfo.dialogmote.avro.KDialogmoteStatusEndring
import org.apache.kafka.clients.producer.KafkaProducer

fun Application.cronjobModule(
    applicationState: ApplicationState,
    database: DatabaseInterface,
    environment: Environment,
    cache: RedisStore,
    brukernotifikasjonProducer: BrukernotifikasjonProducer,
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
    val tokendingsClient = TokendingsClient(
        tokenxClientId = environment.tokenxClientId,
        tokenxEndpoint = environment.tokenxEndpoint,
        tokenxPrivateJWK = environment.tokenxPrivateJWK,
    )
    val oppfolgingstilfelleClient = OppfolgingstilfelleClient(
        azureAdV2Client = azureAdV2Client,
        tokendingsClient = tokendingsClient,
        isoppfolgingstilfelleClientId = environment.isoppfolgingstilfelleClientId,
        isoppfolgingstilfelleBaseUrl = environment.isoppfolgingstilfelleUrl,
        cache = cache,
    )
    val pdfService = PdfService(
        database = database,
    )
    val arbeidstakerVarselService = ArbeidstakerVarselService(
        brukernotifikasjonProducer = brukernotifikasjonProducer,
        dialogmoteArbeidstakerUrl = environment.dialogmoteArbeidstakerUrl,
        namespace = environment.namespace,
        appname = environment.appname,
    )
    val dialogmotestatusService = DialogmotestatusService(
        oppfolgingstilfelleClient = oppfolgingstilfelleClient,
    )
    val dialogmotedeltakerService = DialogmotedeltakerService(
        arbeidstakerVarselService = arbeidstakerVarselService,
        database = database,
    )
    val dialogmoterelasjonService = DialogmoterelasjonService(
        dialogmotedeltakerService = dialogmotedeltakerService,
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

    val kafkaDialogmoteStatusEndringProducerProperties = kafkaDialogmoteStatusEndringProducerConfig(environment.kafka)
    val kafkaDialogmoteStatusEndringProducer = KafkaProducer<String, KDialogmoteStatusEndring>(
        kafkaDialogmoteStatusEndringProducerProperties
    )
    val kafkaDialogmotesvarProducerProperties = kafkaDialogmotesvarProducerConfig(environment.kafka)

    val kafkaDialogmotesvarProducer = KafkaProducer<String, KDialogmotesvar>(
        kafkaDialogmotesvarProducerProperties
    )
    val dialogmoteStatusEndringProducer = DialogmoteStatusEndringProducer(
        kafkaDialogmoteStatusEndringProducer = kafkaDialogmoteStatusEndringProducer,
    )
    val dialogmotesvarProducer = DialogmotesvarProducer(
        kafkaDialogmotesvarProducer = kafkaDialogmotesvarProducer,
    )
    val publishDialogmoteStatusEndringService = PublishDialogmoteStatusEndringService(
        database = database,
        dialogmoteStatusEndringProducer = dialogmoteStatusEndringProducer,
    )
    val publishDialogmotesvarService = PublishDialogmotesvarService(
        database = database,
        dialogmotesvarProducer = dialogmotesvarProducer,
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
    val publishDialogmotesvarCronjob = PublishDialogmotesvarCronjob(
        publishDialogmotesvarService = publishDialogmotesvarService
    )
    val dialogmoteOutdatedCronjob = DialogmoteOutdatedCronjob(
        dialogmotestatusService = dialogmotestatusService,
        dialogmoterelasjonService = dialogmoterelasjonService,
        outdatedDialogmoterCutoff = environment.outdatedDialogmoteCutoff,
        database = database,
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
    if (environment.publishDialogmotesvarEnabled) {
        launchBackgroundTask(
            applicationState = applicationState
        ) {
            cronjobRunner.start(cronjob = publishDialogmotesvarCronjob)
        }
    }
    if (environment.outdatedDialogmoteCronJobEnabled) {
        launchBackgroundTask(
            applicationState = applicationState
        ) {
            cronjobRunner.start(cronjob = dialogmoteOutdatedCronjob)
        }
    }
}
