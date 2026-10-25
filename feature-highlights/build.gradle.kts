plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvmToolchain(17)
    android {
        namespace = "dev.rosalogia.photoduel.feature.highlights"
        compileSdk = 37
        minSdk = 30
    }
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core-ui"))
        }
        getByName("desktopTest").dependencies {
            implementation(libs.junit)
            implementation(libs.truth)
        }
    }
}
