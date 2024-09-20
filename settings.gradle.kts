rootProject.name = "locust4k"

pluginManagement {
    val dokkaVersion: String by settings
    val gitVersioningPluginVersion: String by settings
    val gradleMavenPublishPluginVersion: String by settings
    val kotlinVersion: String by settings
    val ktlintPluginVersion: String by settings

    repositories {
        gradlePluginPortal()
        mavenCentral()
    }

    plugins {
        id("com.vanniktech.maven.publish") version gradleMavenPublishPluginVersion
        id("me.qoomon.git-versioning") version gitVersioningPluginVersion
        id("org.jetbrains.dokka") version dokkaVersion
        id("org.jlleitschuh.gradle.ktlint") version ktlintPluginVersion
        kotlin("jvm") version kotlinVersion
    }
}
