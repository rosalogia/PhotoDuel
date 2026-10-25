import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

kotlin {
    jvmToolchain(17)
}

/** JavaCPP's name for the OS the native libraries are for; packaging for another OS overrides it. */
val nativePlatform: String = (findProperty("nativePlatform") as String?) ?: run {
    val os = System.getProperty("os.name").lowercase()
    when {
        os.startsWith("windows") -> "windows-x86_64"
        os.startsWith("mac") -> if (System.getProperty("os.arch") == "aarch64") "macosx-arm64" else "macosx-x86_64"
        else -> "linux-x86_64"
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":core-ui"))
    implementation(project(":feature-duel"))
    implementation(project(":feature-groups"))
    implementation(project(":feature-highlights"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.sqlite.bundled)
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)

    implementation(libs.bytedeco.opencv)
    implementation(variantOf(libs.bytedeco.opencv) { classifier(nativePlatform) })
    implementation(variantOf(libs.bytedeco.openblas) { classifier(nativePlatform) })
    implementation(libs.onnxruntime)
    implementation(libs.jna)
    implementation(libs.metadata.extractor)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}

compose.desktop {
    application {
        mainClass = "dev.rosalogia.photoduel.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Deb, TargetFormat.AppImage)
            packageName = "PhotoDuel"
            packageVersion = "0.3.0"
            description = "Pick the best shot from each burst"
            vendor = "rosalogia"
        }
    }
}
