import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "no.nav.syfo"
version = "1.0.0"

object Versions {
    const val avro = "1.10.0"
    const val brukernotifikasjonAvro = "1.2021.01.18-11.12-b9c8c40b98d1"
    const val isdialogmoteSchema = "1.0.5"
    const val confluent = "6.1.3"
    const val flyway = "8.0.4"
    const val hikari = "5.0.0"
    const val jackson = "2.13.0"
    const val jedis = "3.7.0"
    const val kafka = "2.7.0"
    const val kafkaEmbedded = "2.7.0"
    const val ktor = "1.6.5"
    const val jaxb = "2.3.1"
    const val kluent = "1.68"
    const val logback = "1.2.6"
    const val logstashEncoder = "6.6"
    const val mockk = "1.12.0"
    const val nimbusjosejwt = "9.11.3"
    const val postgresEmbedded = "0.13.4"
    const val postgres = "42.3.1"
    const val redisEmbedded = "0.7.3"
    const val scala = "2.13.7"
    const val spek = "2.0.17"
    const val mq = "9.2.2.0"
    const val tjenesteSpesifikasjonerGithub = "1.2020.06.11-19.53-1cad83414166"
    const val micrometerRegistry = "1.7.5"
}

plugins {
    kotlin("jvm") version "1.5.31"
    id("com.github.johnrengelman.shadow") version "7.1.0"
    id("org.jlleitschuh.gradle.ktlint") version "10.2.0"
}

val githubUser: String by project
val githubPassword: String by project
repositories {
    mavenCentral()
    maven(url = "https://packages.confluent.io/maven/")
    maven(url = "https://jitpack.io")
    maven {
        url = uri("https://maven.pkg.github.com/navikt/isdialogmote-schema")
        credentials {
            username = githubUser
            password = githubPassword
        }
    }
    maven {
        url = uri("https://maven.pkg.github.com/navikt/tjenestespesifikasjoner")
        credentials {
            username = githubUser
            password = githubPassword
        }
    }
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))

    implementation("io.ktor:ktor-auth-jwt:${Versions.ktor}")
    implementation("io.ktor:ktor-client-apache:${Versions.ktor}")
    implementation("io.ktor:ktor-client-cio:${Versions.ktor}")
    implementation("io.ktor:ktor-client-jackson:${Versions.ktor}")
    implementation("io.ktor:ktor-jackson:${Versions.ktor}")
    implementation("io.ktor:ktor-server-netty:${Versions.ktor}")

    // Logging
    implementation("ch.qos.logback:logback-classic:${Versions.logback}")
    implementation("net.logstash.logback:logstash-logback-encoder:${Versions.logstashEncoder}")

    // Metrics and Prometheus
    implementation("io.ktor:ktor-metrics-micrometer:${Versions.ktor}")
    implementation("io.micrometer:micrometer-registry-prometheus:${Versions.micrometerRegistry}")

    // (De-)serialization
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:${Versions.jackson}")
    implementation("javax.xml.bind:jaxb-api:${Versions.jaxb}")
    implementation("org.glassfish.jaxb:jaxb-runtime:${Versions.jaxb}")

    // Cache
    implementation("redis.clients:jedis:${Versions.jedis}")
    testImplementation("it.ozimov:embedded-redis:${Versions.redisEmbedded}")

    // Database
    implementation("org.postgresql:postgresql:${Versions.postgres}")
    implementation("com.zaxxer:HikariCP:${Versions.hikari}")
    implementation("org.flywaydb:flyway-core:${Versions.flyway}")
    testImplementation("com.opentable.components:otj-pg-embedded:${Versions.postgresEmbedded}")

    // Kafka
    implementation("org.apache.kafka:kafka_2.13:${Versions.kafka}")
    implementation("io.confluent:kafka-avro-serializer:${Versions.confluent}")
    implementation("io.confluent:kafka-schema-registry:${Versions.confluent}")
    implementation("com.github.navikt:brukernotifikasjon-schemas:${Versions.brukernotifikasjonAvro}")
    implementation("no.nav.syfo.dialogmote.avro:isdialogmote-schema:${Versions.isdialogmoteSchema}")
    implementation("org.scala-lang:scala-library") {
        version {
            strictly(Versions.scala)
        }
    }
    testImplementation("no.nav:kafka-embedded-env:${Versions.kafkaEmbedded}")

    // MQ
    implementation("com.ibm.mq:com.ibm.mq.allclient:${Versions.mq}")
    implementation("no.nav.tjenestespesifikasjoner:servicemeldingMedKontaktinformasjon-v1-tjenestespesifikasjon:${Versions.tjenesteSpesifikasjonerGithub}")

    testImplementation("com.nimbusds:nimbus-jose-jwt:${Versions.nimbusjosejwt}")
    testImplementation("io.ktor:ktor-server-test-host:${Versions.ktor}")
    testImplementation("io.mockk:mockk:${Versions.mockk}")
    testImplementation("org.amshove.kluent:kluent:${Versions.kluent}")
    testImplementation("org.spekframework.spek2:spek-dsl-jvm:${Versions.ktor}")
    testImplementation("org.spekframework.spek2:spek-dsl-jvm:${Versions.spek}") {
        exclude(group = "org.jetbrains.kotlin")
    }
    testRuntimeOnly("org.spekframework.spek2:spek-runner-junit5:${Versions.spek}") {
        exclude(group = "org.jetbrains.kotlin")
    }
}

tasks {
    withType<Jar> {
        manifest.attributes["Main-Class"] = "no.nav.syfo.AppKt"
    }

    create("printVersion") {
        doLast {
            println(project.version)
        }
    }

    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "11"
    }

    withType<ShadowJar> {
        archiveBaseName.set("app")
        archiveClassifier.set("")
        archiveVersion.set("")
    }

    withType<Test> {
        useJUnitPlatform {
            includeEngines("spek2")
        }
        testLogging.showStandardStreams = true
    }
}
