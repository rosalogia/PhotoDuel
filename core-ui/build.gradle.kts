plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvmToolchain(17)
    android {
        namespace = "dev.rosalogia.photoduel.core.ui"
        compileSdk = 37
        minSdk = 30
    }
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            api(project(":core-ml"))
            api(libs.cmp.runtime)
            api(libs.cmp.foundation)
            api(libs.cmp.ui)
            api(libs.cmp.material3)
            api(libs.cmp.material.icons)
            api(libs.jb.lifecycle.viewmodel.compose)
            api(libs.jb.lifecycle.runtime.compose)
            api(libs.coil.compose)
        }
    }
}
