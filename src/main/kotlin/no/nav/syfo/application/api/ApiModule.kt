package no.nav.syfo.application.api

import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.routing.*
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import no.nav.syfo.application.api.authentication.*
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.mq.MQSenderInterface
import no.nav.syfo.client.behandlendeenhet.BehandlendeEnhetClient
import no.nav.syfo.client.moteplanlegger.MoteplanleggerClient
import no.nav.syfo.client.narmesteleder.NarmesteLederClient
import no.nav.syfo.client.pdfgen.PdfGenClient
import no.nav.syfo.client.person.adressebeskyttelse.AdressebeskyttelseClient
import no.nav.syfo.client.person.kontaktinfo.KontaktinformasjonClient
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.dialogmote.DialogmoteService
import no.nav.syfo.dialogmote.DialogmotedeltakerService
import no.nav.syfo.dialogmote.api.*
import no.nav.syfo.dialogmote.tilgang.DialogmoteTilgangService
import no.nav.syfo.varsel.arbeidstaker.ArbeidstakerVarselService
import no.nav.syfo.varsel.arbeidstaker.brukernotifikasjon.BrukernotifikasjonProducer
import no.nav.syfo.varsel.arbeidstaker.registerArbeidstakerVarselApi
import no.nav.syfo.varsel.narmesteleder.NarmesteLederVarselService
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import redis.clients.jedis.Protocol

fun Application.apiModule(
    applicationState: ApplicationState,
    brukernotifikasjonProducer: BrukernotifikasjonProducer,
    database: DatabaseInterface,
    mqSender: MQSenderInterface,
    environment: Environment,
    wellKnownSelvbetjening: WellKnown,
    wellKnownVeileder: WellKnown,
) {
    installCallId()
    installContentNegotiation()
    installJwtAuthentication(
        jwtIssuerList = listOf(
            JwtIssuer(
                accectedAudienceList = environment.loginserviceIdportenAudience,
                jwtIssuerType = JwtIssuerType.selvbetjening,
                wellKnown = wellKnownSelvbetjening,
            ),
            JwtIssuer(
                accectedAudienceList = listOf(environment.loginserviceClientId),
                jwtIssuerType = JwtIssuerType.veileder,
                wellKnown = wellKnownVeileder,
            )
        ),
    )
    installStatusPages()

    val moteplanleggerClient = MoteplanleggerClient(
        syfomoteadminBaseUrl = environment.syfomoteadminUrl
    )
    val narmesteLederClient = NarmesteLederClient(
        modiasyforestBaseUrl = environment.modiasyforestUrl
    )

    val cache = RedisStore(
        JedisPool(
            JedisPoolConfig(),
            environment.redisHost,
            environment.redisPort,
            Protocol.DEFAULT_TIMEOUT,
            environment.redisSecret
        )
    )
    val adressebeskyttelseClient = AdressebeskyttelseClient(
        cache = cache,
        syfopersonBaseUrl = environment.syfopersonUrl,
    )
    val behandlendeEnhetClient = BehandlendeEnhetClient(
        syfobehandlendeenhetBaseUrl = environment.syfobehandlendeenhetUrl
    )
    val kontaktinformasjonClient = KontaktinformasjonClient(
        cache = cache,
        syfopersonBaseUrl = environment.syfopersonUrl
    )
    val pdfGenClient = PdfGenClient(
        pdfGenBaseUrl = environment.isdialogmotepdfgenUrl
    )
    val veilederTilgangskontrollClient = VeilederTilgangskontrollClient(
        tilgangskontrollBaseUrl = environment.syfotilgangskontrollUrl
    )
    val dialogmoteTilgangService = DialogmoteTilgangService(
        adressebeskyttelseClient = adressebeskyttelseClient,
        kontaktinformasjonClient = kontaktinformasjonClient,
        veilederTilgangskontrollClient = veilederTilgangskontrollClient
    )

    val arbeidstakerVarselService = ArbeidstakerVarselService(
        brukernotifikasjonProducer = brukernotifikasjonProducer,
        dialogmoteArbeidstakerUrl = environment.dialogmoteArbeidstakerUrl,
        serviceuserUsername = environment.serviceuserUsername,
    )

    val narmesteLederVarselService = NarmesteLederVarselService(
        env = environment,
        mqSender = mqSender
    )

    val dialogmotedeltakerService = DialogmotedeltakerService(
        arbeidstakerVarselService = arbeidstakerVarselService,
        database = database,
    )

    val dialogmoteService = DialogmoteService(
        database = database,
        arbeidstakerVarselService = arbeidstakerVarselService,
        narmesteLederVarselService = narmesteLederVarselService,
        dialogmotedeltakerService = dialogmotedeltakerService,
        behandlendeEnhetClient = behandlendeEnhetClient,
        moteplanleggerClient = moteplanleggerClient,
        narmesteLederClient = narmesteLederClient,
        pdfGenClient = pdfGenClient,
    )

    routing {
        registerPodApi(applicationState)
        registerPrometheusApi()
        authenticate(JwtIssuerType.veileder.name) {
            registerDialogmoteApi(
                dialogmoteService = dialogmoteService,
                dialogmoteTilgangService = dialogmoteTilgangService,
            )
            registerDialogmoteActionsApi(
                dialogmoteService = dialogmoteService,
                dialogmoteTilgangService = dialogmoteTilgangService,
            )
            registerDialogmoteEnhetApi(
                dialogmoteService = dialogmoteService,
                dialogmoteTilgangService = dialogmoteTilgangService,
                veilederTilgangskontrollClient = veilederTilgangskontrollClient,
            )
        }
        authenticate(JwtIssuerType.selvbetjening.name) {
            registerArbeidstakerVarselApi(
                dialogmoteService = dialogmoteService,
                dialogmotedeltakerService = dialogmotedeltakerService,
            )
        }
    }
}
