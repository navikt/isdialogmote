import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "no.nav.syfo"
version = "1.0.0"

object Versions {
    const val jacksonVersion = "2.11.3"
    const val ktorVersion = "1.5.0"
    const val logbackVersion = "1.2.3"
    const val logstashEncoderVersion = "6.3"
    const val prometheusVersion = "0.9.0"
}

plugins {
    kotlin("jvm") version "1.4.21"
    id("com.github.johnrengelman.shadow") version "6.1.0"
    id("org.jlleitschuh.gradle.ktlint") version "9.4.1"
}

repositories {
    jcenter()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))

    implementation("io.ktor:ktor-auth-jwt:${Versions.ktorVersion}")
    implementation("io.ktor:ktor-client-apache:${Versions.ktorVersion}")
    implementation("io.ktor:ktor-client-jackson:${Versions.ktorVersion}")
    implementation("io.ktor:ktor-jackson:${Versions.ktorVersion}")
    implementation("io.ktor:ktor-server-netty:${Versions.ktorVersion}")

    implementation("ch.qos.logback:logback-classic:${Versions.logbackVersion}")
    implementation("net.logstash.logback:logstash-logback-encoder:${Versions.logstashEncoderVersion}")

    implementation("io.prometheus:simpleclient_hotspot:${Versions.prometheusVersion}")
    implementation("io.prometheus:simpleclient_common:${Versions.prometheusVersion}")

    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:${Versions.jacksonVersion}")
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
    }

    withType<Test> {
        useJUnitPlatform {
            includeEngines("spek2")
        }
        testLogging.showStandardStreams = true
    }
}
