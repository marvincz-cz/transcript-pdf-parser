plugins {
    application
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
}

group = "cz.marvincz"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.apache.pdfbox:pdfbox:3.0.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.github.ajalt.clikt:clikt:5.0.1")
    implementation("com.github.ajalt.clikt:clikt-markdown:5.0.1")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass = "cz.marvincz.transcript.pdfparser.MainKt"
}