import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
import org.apache.tools.ant.taskdefs.condition.Os

group = "no.nav.syfo"
version = "1.0.0"

object Versions {
    const val altinnCorrespondenceAgencyExternalVersion = "1.2020.01.20-15.44-063ae9f84815"
    const val cxfVersion = "3.5.7"
    const val confluent = "7.5.1"
    const val flyway = "9.22.3"
    const val hikari = "5.0.1"
    const val isdialogmoteSchema = "1.0.5"
    const val jacksonDataType = "2.16.0"
    const val jedis = "5.1.0"
    const val kafka = "3.6.0"
    const val kafkaEmbedded = "3.2.3"
    const val ktor = "2.3.8"
    const val kluent = "1.73"
    const val jaxbApi = "2.3.1"
    const val jaxbRuntime = "2.3.6"
    const val jaxsWsApiVersion = "2.3.1"
    const val jaxwsToolsVersion = "2.3.5"
    const val jetty = "9.4.53.v20231009"
    const val logback = "1.4.14"
    const val logstashEncoder = "7.4"
    const val micrometerRegistry = "1.12.0"
    const val mockk = "1.13.8"
    const val nimbusjosejwt = "9.37.2"
    val postgresEmbedded = if (Os.isFamily(Os.FAMILY_MAC)) "1.0.0" else "0.13.4"
    const val postgres = "42.7.2"
    const val redisEmbedded = "0.7.3"
    const val spek = "2.0.19"
    const val tjenesteSpesifikasjonerGithub = "1.2020.06.11-19.53-1cad83414166"
}

plugins {
    kotlin("jvm") version "1.9.21"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("org.jlleitschuh.gradle.ktlint") version "11.4.2"
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

    implementation("io.ktor:ktor-client-apache:${Versions.ktor}")
    implementation("io.ktor:ktor-client-cio:${Versions.ktor}")
    implementation("io.ktor:ktor-client-content-negotiation:${Versions.ktor}")
    implementation("io.ktor:ktor-serialization-jackson:${Versions.ktor}")
    implementation("io.ktor:ktor-server-auth-jwt:${Versions.ktor}")
    implementation("io.ktor:ktor-server-call-id:${Versions.ktor}")
    implementation("io.ktor:ktor-server-content-negotiation:${Versions.ktor}")
    implementation("io.ktor:ktor-server-netty:${Versions.ktor}")
    implementation("io.ktor:ktor-server-status-pages:${Versions.ktor}")

    // JWT
    implementation("com.nimbusds:nimbus-jose-jwt:${Versions.nimbusjosejwt}")

    // Logging
    implementation("ch.qos.logback:logback-classic:${Versions.logback}")
    implementation("net.logstash.logback:logstash-logback-encoder:${Versions.logstashEncoder}")

    // Metrics and Prometheus
    implementation("io.ktor:ktor-server-metrics-micrometer:${Versions.ktor}")
    implementation("io.micrometer:micrometer-registry-prometheus:${Versions.micrometerRegistry}")

    // (De-)serialization
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:${Versions.jacksonDataType}")
    implementation("javax.xml.bind:jaxb-api:${Versions.jaxbApi}")
    implementation("org.glassfish.jaxb:jaxb-runtime:${Versions.jaxbRuntime}")

    // Cache
    implementation("redis.clients:jedis:${Versions.jedis}")
    testImplementation("it.ozimov:embedded-redis:${Versions.redisEmbedded}")

    // Database
    implementation("org.postgresql:postgresql:${Versions.postgres}")
    implementation("com.zaxxer:HikariCP:${Versions.hikari}")
    implementation("org.flywaydb:flyway-core:${Versions.flyway}")
    testImplementation("com.opentable.components:otj-pg-embedded:${Versions.postgresEmbedded}")

    // Kafka
    val excludeLog4j = fun ExternalModuleDependency.() {
        exclude(group = "log4j")
    }
    implementation("org.apache.kafka:kafka_2.13:${Versions.kafka}", excludeLog4j)
    implementation("io.confluent:kafka-avro-serializer:${Versions.confluent}", excludeLog4j)
    constraints {
        implementation("org.apache.commons:commons-compress") {
            because("org.apache.commons:commons-compress:1.22 -> https://www.cve.org/CVERecord?id=CVE-2012-2098")
            version {
                require("1.26.0")
            }
        }
        implementation("com.google.guava:guava") {
            because("com.google.guava:guava:30.1.1-jre -> https://www.cve.org/CVERecord?id=CVE-2020-8908")
            version {
                require("32.1.3-jre")
            }
        }
    }
    implementation("io.confluent:kafka-schema-registry:${Versions.confluent}", excludeLog4j)
    constraints {
        implementation("org.yaml:snakeyaml") {
            because("io.confluent:kafka-schema-registry:${Versions.confluent} -> https://advisory.checkmarx.net/advisory/vulnerability/CVE-2022-25857/")
            version {
                require("1.31")
            }
        }
        implementation("org.glassfish:jakarta.el") {
            because("io.confluent:kafka-schema-registry:${Versions.confluent} -> https://advisory.checkmarx.net/advisory/vulnerability/CVE-2021-28170/")
            version {
                require("3.0.4")
            }
        }
        implementation("com.google.protobuf:protobuf-java") {
            because("io.confluent:kafka-schema-registry:${Versions.confluent} -> https://www.cve.org/CVERecord?id=CVE-2022-3510")
            version {
                require("3.25.1")
            }
        }
        implementation("org.apache.zookeeper:zookeeper") {
            because("io.confluent:kafka-schema-registry:${Versions.confluent} -> https://www.cve.org/CVERecord?id=CVE-2023-44981")
            version {
                require("3.7.2")
            }
        }
        implementation("org.eclipse.jetty:jetty-server") {
            because("io.confluent:kafka-schema-registry:${Versions.confluent} -> https://www.cve.org/CVERecord?id=CVE-2023-36478")
            version {
                require(Versions.jetty)
            }
        }
        implementation("org.eclipse.jetty:jetty-xml") {
            because("io.confluent:kafka-schema-registry:${Versions.confluent} -> https://www.cve.org/CVERecord?id=CVE-2023-36478")
            version {
                require(Versions.jetty)
            }
        }
        implementation("org.eclipse.jetty:jetty-servlets") {
            because("io.confluent:kafka-schema-registry:${Versions.confluent} -> https://www.cve.org/CVERecord?id=CVE-2023-36478")
            version {
                require(Versions.jetty)
            }
        }
        implementation("org.eclipse.jetty.http2:http2-server") {
            because("io.confluent:kafka-schema-registry:${Versions.confluent} -> https://www.cve.org/CVERecord?id=CVE-2023-36478")
            version {
                require(Versions.jetty)
            }
        }
    }
    implementation("no.nav.syfo.dialogmote.avro:isdialogmote-schema:${Versions.isdialogmoteSchema}")
    constraints {
        implementation("org.apache.avro:avro") {
            because("no.nav.syfo.dialogmote.avro:isdialogmote-schema:${Versions.isdialogmoteSchema} -> https://nvd.nist.gov/vuln/detail/CVE-2023-39410")
            version {
                require("1.11.3")
            }
        }
    }
    testImplementation("no.nav:kafka-embedded-env:${Versions.kafkaEmbedded}", excludeLog4j)

    implementation("no.nav.tjenestespesifikasjoner:servicemeldingMedKontaktinformasjon-v1-tjenestespesifikasjon:${Versions.tjenesteSpesifikasjonerGithub}")

    testImplementation("io.ktor:ktor-server-test-host:${Versions.ktor}")
    testImplementation("io.mockk:mockk:${Versions.mockk}")
    testImplementation("org.amshove.kluent:kluent:${Versions.kluent}")
    testImplementation("org.spekframework.spek2:spek-dsl-jvm:${Versions.spek}") {
        exclude(group = "org.jetbrains.kotlin")
    }
    testRuntimeOnly("org.spekframework.spek2:spek-runner-junit5:${Versions.spek}") {
        exclude(group = "org.jetbrains.kotlin")
    }

    // Soap
    implementation("no.nav.tjenestespesifikasjoner:altinn-correspondence-agency-external-basic:${Versions.altinnCorrespondenceAgencyExternalVersion}")
    implementation("org.apache.cxf:cxf-rt-frontend-jaxws:${Versions.cxfVersion}")
    implementation("org.apache.cxf:cxf-rt-features-logging:${Versions.cxfVersion}")
    implementation("org.apache.cxf:cxf-rt-transports-http:${Versions.cxfVersion}")
    implementation("org.apache.cxf:cxf-rt-ws-security:${Versions.cxfVersion}")
    constraints {
        implementation("org.apache.santuario:xmlsec") {
            because("org.apache.cxf:cxf-rt-ws-security:${Versions.cxfVersion} -> https://nvd.nist.gov/vuln/detail/CVE-2023-44483")
            version {
                require("2.3.4")
            }
        }
    }
    implementation("javax.xml.ws:jaxws-api:${Versions.jaxsWsApiVersion}")
    implementation("com.sun.xml.ws:jaxws-tools:${Versions.jaxwsToolsVersion}") {
        exclude(group = "com.sun.xml.ws", module = "policy")
    }
}

kotlin {
    jvmToolchain(17)
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

    shadowJar {
        isZip64 = true
    }

    withType<ShadowJar> {
        archiveBaseName.set("app")
        archiveClassifier.set("")
        archiveVersion.set("")
        transform(ServiceFileTransformer::class.java) {
            setPath("META-INF/cxf")
            include("bus-extensions.txt")
        }
    }

    withType<Test> {
        useJUnitPlatform {
            includeEngines("spek2")
        }
        testLogging.showStandardStreams = true
    }
}
