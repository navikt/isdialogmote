package no.nav.syfo.infrastructure.cronjob

import io.ktor.server.application.*
import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import no.nav.syfo.application.*
import no.nav.syfo.dialogmote.avro.KDialogmoteStatusEndring
import no.nav.syfo.infrastructure.client.azuread.AzureAdV2Client
import no.nav.syfo.infrastructure.client.cache.ValkeyStore
import no.nav.syfo.infrastructure.client.dialogmelding.DialogmeldingClient
import no.nav.syfo.infrastructure.client.dokarkiv.DokarkivClient
import no.nav.syfo.infrastructure.client.ereg.EregClient
import no.nav.syfo.infrastructure.client.pdl.PdlClient
import no.nav.syfo.infrastructure.cronjob.dialogmoteOutdated.DialogmoteOutdatedCronjob
import no.nav.syfo.infrastructure.cronjob.dialogmotesvar.*
import no.nav.syfo.infrastructure.cronjob.journalforing.DialogmoteVarselJournalforingCronjob
import no.nav.syfo.infrastructure.cronjob.journalpostdistribusjon.DialogmoteJournalpostDistribusjonCronjob
import no.nav.syfo.infrastructure.cronjob.leaderelection.LeaderPodClient
import no.nav.syfo.infrastructure.cronjob.statusendring.DialogmoteStatusEndringProducer
import no.nav.syfo.infrastructure.cronjob.statusendring.PublishDialogmoteStatusEndringCronjob
import no.nav.syfo.infrastructure.cronjob.statusendring.PublishDialogmoteStatusEndringService
import no.nav.syfo.infrastructure.cronjob.statusendring.kafkaDialogmoteStatusEndringProducerConfig
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.repository.MoteStatusEndretRepository
import no.nav.syfo.launchBackgroundTask
import org.apache.kafka.clients.producer.KafkaProducer

fun Application.cronjobModule(
    applicationState: ApplicationState,
    database: DatabaseInterface,
    environment: Environment,
    cache: ValkeyStore,
    dialogmotestatusService: DialogmotestatusService,
    dialogmoterelasjonService: DialogmoterelasjonService,
    arbeidstakerVarselService: ArbeidstakerVarselService,
    moteStatusEndretRepository: MoteStatusEndretRepository,
    pdfRepository: IPdfRepository,
) {
    val azureAdV2Client = AzureAdV2Client(
        aadAppClient = environment.aadAppClient,
        aadAppSecret = environment.aadAppSecret,
        aadTokenEndpoint = environment.aadTokenEndpoint,
        valkeyStore = cache,
    )
    val dokarkivClient = DokarkivClient(
        azureAdV2Client = azureAdV2Client,
        dokarkivClientId = environment.dokarkivClientId,
        dokarkivBaseUrl = environment.dokarkivUrl,
    )
    val pdlClient = PdlClient(
        azureAdV2Client = azureAdV2Client,
        pdlClientId = environment.pdlClientId,
        pdlUrl = environment.pdlUrl,
        valkeyStore = cache,
    )
    val eregClient = EregClient(
        baseUrl = environment.eregUrl,
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
    val dialogmeldingClient = DialogmeldingClient(
        azureAdClient = azureAdV2Client,
        url = environment.dialogmeldingUrl,
        clientId = environment.dialogmeldingClientId,
    )
    val journalforDialogmoteVarslerCronjob = DialogmoteVarselJournalforingCronjob(
        dialogmotedeltakerVarselJournalpostService = dialogmotedeltakerVarselJournalpostService,
        referatJournalpostService = referatJournalpostService,
        dokarkivClient = dokarkivClient,
        pdlClient = pdlClient,
        eregClient = eregClient,
        dialogmeldingClient = dialogmeldingClient,
        isJournalforingRetryEnabled = environment.isJournalforingRetryEnabled,
        pdfRepository = pdfRepository,
    )
    val kafkaDialogmoteStatusEndringProducerProperties = kafkaDialogmoteStatusEndringProducerConfig(environment.kafka)
    val kafkaDialogmoteStatusEndringProducer = KafkaProducer<String, KDialogmoteStatusEndring>(
        kafkaDialogmoteStatusEndringProducerProperties
    )
    val kafkaDialogmotesvarProducerProperties = KafkaDialogmotesvarProducerConfig(environment.kafka)

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
        moteStatusEndretRepository = moteStatusEndretRepository,
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
        arbeidstakerVarselService = arbeidstakerVarselService,
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
    launchBackgroundTask(
        applicationState = applicationState
    ) {
        cronjobRunner.start(cronjob = publishDialogmotesvarCronjob)
    }
    launchBackgroundTask(
        applicationState = applicationState
    ) {
        cronjobRunner.start(cronjob = dialogmoteOutdatedCronjob)
    }
}
