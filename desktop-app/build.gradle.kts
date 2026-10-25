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

/** One OS/CPU the native libraries are bundled for, under each library's own name for it. */
data class NativeTarget(val javacpp: String, val compose: String, val onnx: String)

/** The current OS, or -PnativeTarget=windows-x86_64 to stage another OS's libraries. */
val nativeTarget: NativeTarget = run {
    val os = System.getProperty("os.name").lowercase()
    val requested = (findProperty("nativeTarget") as String?)
        ?: if (os.startsWith("windows")) "windows-x86_64" else "linux-x86_64"
    when (requested) {
        "windows-x86_64" -> NativeTarget("windows-x86_64", "windows-x64", "win-x64")
        "linux-x86_64" -> NativeTarget("linux-x86_64", "linux-x64", "linux-x64")
        else -> error("Unsupported nativeTarget $requested")
    }
}

/** Jars the native libraries are unpacked from; they aren't on the classpath themselves. */
val nativeJars: Configuration by configurations.creating { isTransitive = false }

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
    implementation(libs.onnxruntime)
    nativeJars(variantOf(libs.bytedeco.opencv) { classifier(nativeTarget.javacpp) })
    nativeJars(variantOf(libs.bytedeco.openblas) { classifier(nativeTarget.javacpp) })
    nativeJars(variantOf(libs.bytedeco.javacpp) { classifier(nativeTarget.javacpp) })
    nativeJars(libs.onnxruntime)
    implementation(libs.jna)
    implementation(libs.metadata.extractor)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}

/**
 * Stages the native libraries for [nativeTarget] as plain files, which the packaged app loads
 * directly (see DesktopNatives): OpenCV's Java bindings and the modules they link, minus highgui
 * (GTK on Linux) and JavaCPP's own wrappers; OpenBLAS; the MSVC runtime on Windows; ONNX Runtime;
 * and on Windows libheif from desktop-app/natives/windows-x64. Other platforms' copies stay out.
 */
val prepareNatives by tasks.registering(Sync::class) {
    val t = nativeTarget
    into(layout.buildDirectory.dir("natives/${t.compose}"))
    includeEmptyDirs = false
    val jars = nativeJars
    from(provider { jars.map { zipTree(it) } }) {
        include("org/bytedeco/opencv/${t.javacpp}/*", "org/bytedeco/openblas/${t.javacpp}/*", "org/bytedeco/javacpp/${t.javacpp}/*")
        include("ai/onnxruntime/native/${t.onnx}/*")
        // Only libraries, and not JavaCPP's own wrappers (libjniopencv_core.so, jniopencv_core.dll).
        val library = Regex(".*\\.(so(\\.\\d+)*|dll)")
        val unused = Regex("(lib)?jni.*|.*highgui.*")
        eachFile { if (!name.matches(library) || name.matches(unused)) exclude() else path = name }
    }
    from(layout.projectDirectory.dir("natives/${t.compose}"))
}

/** ONNX Runtime's classes without its bundled libraries for every OS (~50 MB); prepareNatives supplies ours. */
val onnxRuntimeClasses by tasks.registering(Jar::class) {
    archiveFileName.set("onnxruntime-classes.jar")
    destinationDirectory.set(layout.buildDirectory.dir("onnxruntime"))
    val ort = configurations.detachedConfiguration(dependencies.create(libs.onnxruntime.get())).apply { isTransitive = false }
    from(provider { ort.map { zipTree(it) } }) { exclude("ai/onnxruntime/native/**") }
}

configurations.runtimeClasspath {
    exclude(group = "com.microsoft.onnxruntime")
}
dependencies {
    runtimeOnly(files(onnxRuntimeClasses))
}

compose.desktop {
    application {
        mainClass = "dev.rosalogia.photoduel.desktop.MainKt"
        nativeDistributions {
            // From the task's output, so packaging depends on it and sees when its files change.
            appResourcesRootDir.set(layout.dir(prepareNatives.map { it.destinationDir.parentFile }))
            targetFormats(TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "PhotoDuel"
            packageVersion = "0.3.0"
            description = "Pick the best shot from each burst"
            vendor = "rosalogia"
            // From suggestRuntimeModules: ONNX Runtime and JNA need these beyond what Compose adds.
            modules("java.instrument", "java.management", "jdk.unsupported")
            linux {
                iconFile.set(project.file("icons/icon.png"))
                menuGroup = "Graphics"
            }
            windows {
                iconFile.set(project.file("icons/icon.ico"))
                menuGroup = "Photo Duel"
                perUserInstall = true
                // Keeps upgrades replacing the installed version instead of installing side by side.
                upgradeUuid = "6f4d1c1e-6b0a-4d39-9a51-2f6a8f2c7d10"
            }
        }
    }
}
