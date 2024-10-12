import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.SonatypeHost
import groovy.util.Node
import groovy.util.NodeList
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    id("com.google.cloud.tools.jib")
    id("com.vanniktech.maven.publish")
    id("me.qoomon.git-versioning")
    id("org.jetbrains.dokka")
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
val junitPlatformLauncherVersion: String by project
val kotlinLoggingJvmVersion: String by project
val kotlinVersion: String by project
val kotlinxCoroutinesVersion: String by project
val ktlintVersion: String by project
val logbackVersion: String by project
val msgpackVersion: String by project
val nettyVersion: String by project
val restAssuredVersion: String by project
val slf4jVersion: String by project
val testcontainersVersion: String by project

dependencies {
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

    // lint
    compileOnly("com.pinterest.ktlint:ktlint-core:$ktlintVersion")

    // test
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:$junitPlatformLauncherVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:$junitJupiterVersion")
    testImplementation("org.testcontainers:junit-jupiter:$testcontainersVersion")
    testImplementation("io.rest-assured:rest-assured:$restAssuredVersion")
    testImplementation("io.rest-assured:kotlin-extensions:$restAssuredVersion")
}

group = "com.onepeloton.locust4k"
description = "Locust Worker Client for Kotlin"
version = "0.0.0-SNAPSHOT"
gitVersioning.apply {
    refs {
        tag("v(?<version>.*)") { version = "\${ref.version}" }
        branch(".+") { version = "\${ref}-SNAPSHOT" }
    }
}

mavenPublishing {
    configure(
        KotlinJvm(
            javadocJar = JavadocJar.Dokka("dokkaHtml"),
            sourcesJar = true,
        ),
    )
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()
    coordinates(groupId = group as String, artifactId = rootProject.name, version = version as String)
    pom {
        name.set("Locust4k")
        description.set(project.description)
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
                id.set("Travis Haagen <travis.haagen@onepeloton.com>")
                name.set("Travis Haagen")
                email.set("travis.haagen@onepeloton.com")
                url.set("https://travishaagen.github.io/")
                organization.set("Peloton Interactive, Inc.")
                organizationUrl.set("https://www.onepeloton.com/")
            }
        }
        scm {
            url.set("https://github.com/pelotoncycle/locust4k/")
            connection.set("scm:git:git://github.com/pelotoncycle/locust4k.git")
            developerConnection.set("scm:git:ssh://git@github.com/pelotoncycle/locust4k.git")
        }
        withXml {
            val dependencies = (asNode().get("dependencies") as NodeList).first() as Node
            val dependencyList = dependencies.get("dependency") as NodeList
            dependencyList.forEach {
                val dependency = it as Node
                val artifactId = (dependency.get("artifactId") as NodeList).last() as Node
                // remove logback from the POM, which makes it optional
                if (artifactId.text().contains("logback")) {
                    dependency.parent().remove(dependency)
                }
            }
        }
    }
}

jib {
    to {
        image = "locust4k/example"
    }
    container {
        mainClass = "com.onepeloton.locust4k.examples.ExampleApp"
    }
}

tasks.named("compileKotlin", org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask::class.java) {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
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
            TestLogEvent.STANDARD_OUT,
        )
        exceptionFormat = TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
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
                "Implementation-Version" to project.version,
            ),
        )
    }
}

tasks.register("runExample") {
    description = "Run an example app by name."
    val appName = project.providers.gradleProperty("name")
    if (appName.isPresent) {
        javaexec {
            mainClass.set("com.onepeloton.locust4k.examples.${appName.get()}")
            classpath = sourceSets["main"].runtimeClasspath
        }
    }
}
