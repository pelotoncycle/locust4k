import com.vanniktech.maven.publish.SonatypeHost
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    id("com.vanniktech.maven.publish")
    id("me.qoomon.git-versioning")
    id("org.jlleitschuh.gradle.ktlint")
    kotlin("jvm")
    `java-library`
    jacoco
}

repositories {
    mavenCentral()
}

buildscript {
    repositories {
        mavenCentral()
    }
}

val jacocoVersion: String by project
jacoco {
    toolVersion = jacocoVersion
}

val eclipseCollectionsVersion: String by project
val jacksonModuleKotlinVersion: String by project
val jeromqVersion: String by project
val junitJupiterVersion: String by project
val kotlinLoggingJvmVersion: String by project
val kotlinVersion: String by project
val kotlinxCoroutinesVersion: String by project
val ktlintVersion: String by project
val logbackVersion: String by project
val mockitoKotlinVersion: String by project
val msgpackVersion: String by project
val nettyVersion: String by project
val slf4jVersion: String by project

dependencies {
    runtimeOnly("com.pinterest.ktlint:ktlint-core:$ktlintVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:$kotlinxCoroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:$kotlinxCoroutinesVersion")
    implementation("io.github.oshai:kotlin-logging-jvm:$kotlinLoggingJvmVersion")
    implementation("org.zeromq:jeromq:$jeromqVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonModuleKotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
    implementation("org.msgpack:msgpack-core:$msgpackVersion")
    implementation("io.netty:netty-buffer:$nettyVersion")
    implementation("org.eclipse.collections:eclipse-collections:$eclipseCollectionsVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")
    testImplementation("org.mockito.kotlin:mockito-kotlin:$mockitoKotlinVersion")
}

group = "com.onepeloton"
description = "Locust Worker Client for Kotlin"
version = "0.0.0-SNAPSHOT"
gitVersioning.apply {
    refs {
        branch(".+") { version = "\${ref}-SNAPSHOT" }
        tag("v(?<version>.*)") { version = "\${ref.version}" }
    }
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()
    coordinates(groupId = group as String, artifactId = rootProject.name, version = version as String)
    pom {
        name.set("Locust4k")
        inceptionYear.set("2024")
        url.set("https://github.com/pelotoncycle/locust4k/")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://github.com/pelotoncycle/locust4k/blob/main/LICENSE")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("travispeloton")
                name.set("Travis Haagen")
                email.set("travis.haagen@onepeloton.com")
                url.set("https://github.com/travispeloton/")
                organization.set("Peloton Interactive, Inc.")
                organizationUrl.set("https://www.onepeloton.com/")
            }
        }
        scm {
            url.set("https://github.com/pelotoncycle/locust4k/")
            connection.set("scm:git:git://github.com/pelotoncycle/locust4k.git")
            developerConnection.set("scm:git:ssh://git@github.com/pelotoncycle/locust4k.git")
        }
    }
}

tasks {
    compileKotlin {
        kotlinOptions {
            jvmTarget = "17"
            freeCompilerArgs = listOf(
                "-opt-in=kotlin.RequiresOptIn"
            )
        }
    }
    compileTestKotlin {
        kotlinOptions {
            jvmTarget = "17"
            freeCompilerArgs = listOf(
                "-opt-in=kotlin.RequiresOptIn"
            )
        }
    }
}

tasks.test {
    dependsOn("cleanTest")
    useJUnitPlatform()
    testLogging {
        events(
            TestLogEvent.FAILED,
            TestLogEvent.PASSED,
            TestLogEvent.SKIPPED,
            TestLogEvent.STANDARD_OUT
        )
        exceptionFormat = TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
    finalizedBy(tasks.jacocoTestReport) // report is always generated after tests run
}

tasks.jacocoTestReport {
    dependsOn(tasks.test) // tests are required to run before generating the report
    reports {
        xml.required.set(true)
        csv.required.set(false)
        html.outputLocation.set(layout.buildDirectory.dir("jacocoHtml"))
    }
}

tasks.jar {
    manifest {
        attributes(
            mapOf(
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version
            )
        )
    }
}

tasks.register("runExample") {
    description = "Run an example app by name."
    val appName = project.providers.gradleProperty("name")
    if (appName.isPresent) {
        dependsOn(tasks.compileKotlin)
        javaexec {
            mainClass.set("com.onepeloton.locust4k.examples.${appName.get()}")
            classpath = sourceSets["main"].runtimeClasspath
        }
    }
}
