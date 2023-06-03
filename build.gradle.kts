import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "1.8.21"
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "de.randombyte.gp-takeout-tool"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("commons-codec:commons-codec:1.15")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")
}

kotlin {
    jvmToolchain(11)
}

application {
    mainClass.set("de.randombyte.gptakeouttool.MainKt")
}

tasks.withType<ShadowJar> {
    archiveBaseName.set("gp-takeout-tool")
    archiveClassifier.set("")
}