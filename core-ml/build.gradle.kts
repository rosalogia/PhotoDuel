plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)

}

kotlin {
    jvmToolchain(17)
    android {
        namespace = "dev.rosalogia.photoduel.core.ml"
        compileSdk = 37
        minSdk = 30
    }
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            api(project(":core-data"))
            // ImageBitmap, for decoded photos shown on screen.
            api(libs.cmp.ui)
        }
        // The same OpenCV Java API on both platforms: the Android SDK, and JavaCPP's build of the
        // official bindings on desktop (the desktop app adds the native libraries for its OS).
        androidMain.dependencies {
            api(libs.opencv)
        }
        getByName("desktopMain").dependencies {
            api(libs.bytedeco.opencv)
        }
        getByName("desktopTest").dependencies {
            implementation(libs.junit)
            implementation(libs.truth)
        }
    }
}
