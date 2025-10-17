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
        freeCompilerArgs.add("-Xconsistent-data-class-copy-visibility")
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
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.status.pages)
    implementation(libs.slf4j.simple)
    implementation(libs.exposed.core)
    implementation(libs.exposed.r2dbc)
    runtimeOnly(libs.postgres.r2dbc)
}
