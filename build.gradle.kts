plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.serialization)
    alias(libs.plugins.ktlint)
    application
}

kotlin {
    compilerOptions {
        extraWarnings = true
        allWarningsAsErrors = true
        progressiveMode = true
        optIn.add("kotlin.time.ExperimentalTime")
        freeCompilerArgs.add("-Xconsistent-data-class-copy-visibility")
        freeCompilerArgs.add("-Xcontext-sensitive-resolution")
        freeCompilerArgs.add("-Xdata-flow-based-exhaustiveness")
    }
}

application {
    mainClass = "friendly.backend.MainKt"
}

tasks.distZip {
    archiveFileName.set("distribution.zip")
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.status.pages)
    implementation(libs.slf4j.simple)
    implementation(libs.exposed.core)
    implementation(libs.exposed.r2dbc)
    implementation(libs.exposed.datetime)
    implementation(libs.kotlinx.io.core)
    implementation(libs.aqueue)
    implementation(libs.firebase.admin)
    runtimeOnly(libs.postgres.r2dbc)
}
