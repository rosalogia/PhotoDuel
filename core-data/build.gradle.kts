plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

kotlin {
    jvmToolchain(17)
    android {
        namespace = "dev.rosalogia.photoduel.core.data"
        compileSdk = 37
        minSdk = 30
    }
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            api(project(":core-common"))
            api(libs.room.runtime)
            api(libs.datastore.preferences.core)
            implementation(libs.kotlinx.serialization.json)
        }
        getByName("desktopTest").dependencies {
            implementation(libs.junit)
            implementation(libs.truth)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspDesktop", libs.room.compiler)
}
