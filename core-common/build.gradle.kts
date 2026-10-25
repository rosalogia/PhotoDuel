plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    jvmToolchain(17)
    android {
        namespace = "dev.rosalogia.photoduel.core.common"
        compileSdk = 37
        minSdk = 30
    }
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
        }
        getByName("desktopTest").dependencies {
            implementation(libs.junit)
        }
    }
}
