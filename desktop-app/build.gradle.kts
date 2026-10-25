import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.util.zip.ZipFile

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

/** The desktop app's version, for installers and the launcher. */
val appVersion = "0.3.0"

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
val nativeJars: Configuration = configurations.create("nativeJars") { isTransitive = false }

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":core-ui"))
    implementation(project(":feature-duel"))
    implementation(project(":feature-groups"))
    implementation(project(":feature-highlights"))

    // Skia for the OS being packaged, so a Windows build can be assembled on Linux.
    implementation(if (nativeTarget.compose == "windows-x64") compose.desktop.windows_x64 else compose.desktop.currentOs)
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
    // Only libraries, and not JavaCPP's own wrappers (libjniopencv_core.so, jniopencv_core.dll), nor
    // the Universal CRT, which Windows 10 and later always provide themselves.
    val library = Regex(".*\\.(so(\\.\\d+)*|dll)")
    val unused = Regex("(lib)?jni.*|.*highgui.*|api-ms-win-.*|ucrtbase\\.dll")
    // Inputs, so editing the filters reruns the task (Gradle can't see into the eachFile lambda).
    inputs.property("library", library.pattern)
    inputs.property("unused", unused.pattern)
    into(layout.buildDirectory.dir("natives/${t.compose}"))
    includeEmptyDirs = false
    val jars = nativeJars
    from(provider { jars.map { zipTree(it) } }) {
        include("org/bytedeco/opencv/${t.javacpp}/*", "org/bytedeco/openblas/${t.javacpp}/*", "org/bytedeco/javacpp/${t.javacpp}/*")
        include("ai/onnxruntime/native/${t.onnx}/*")
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
            packageVersion = appVersion
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

/**
 * A Windows build assembled on any OS, laid out like jpackage's app image: PhotoDuel.exe (jpackage's
 * own GUI launcher, taken from a Windows JDK), app/ with the jars, Skia and the native libraries,
 * and runtime/ made by jlink from the Windows JDK's modules. jlink's version must match that JDK's.
 *   ./gradlew :desktop-app:packageWindowsPortable -PnativeTarget=windows-x86_64 -PwindowsJdk=/path/to/jdk-17-windows
 * On Windows itself, packageMsi makes a proper installer instead.
 */
val windowsJdk = providers.gradleProperty("windowsJdk")
val packagingWindows = nativeTarget.compose == "windows-x64"

/** Jar names made unique (two libraries ship lifecycle-runtime-desktop-2.11.0.jar), as the Compose packager does. */
fun uniqueJarName(f: File) = "${f.nameWithoutExtension}-${f.absolutePath.hashCode().toUInt().toString(16)}.jar"

val windowsRuntime by tasks.registering(Exec::class) {
    val enabled = packagingWindows
    onlyIf { enabled }
    val modules = File(windowsJdk.getOrElse("missing"), "jmods")
    val out = layout.buildDirectory.dir("windows/runtime").get().asFile
    inputs.dir(modules).optional()
    outputs.dir(out)
    doFirst { out.deleteRecursively() }
    executable(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(17)) }.get().metadata.installationPath.file("bin/jlink").asFile)
    args(
        "--module-path", modules.path,
        "--add-modules", "java.base,java.desktop,java.logging,jdk.crypto.ec,java.instrument,java.management,jdk.unsupported",
        "--strip-debug", "--no-header-files", "--no-man-pages", "--strip-native-commands",
        "--output", out.path,
    )
}

/** jpackage's launcher reads this to find the jars and Java options; same format as on Linux. */
val windowsCfg by tasks.registering {
    val names = configurations.runtimeClasspath.map { cp -> cp.files.map(::uniqueJarName) }
    val appJar = tasks.jar.flatMap { it.archiveFileName }
    val version = appVersion
    val out = layout.buildDirectory.file("windows/cfg/PhotoDuel.cfg")
    inputs.property("jars", names)
    inputs.property("appJar", appJar)
    outputs.file(out)
    doLast {
        out.get().asFile.writeText(
            buildString {
                appendLine("[Application]")
                appendLine("app.mainclass=dev.rosalogia.photoduel.desktop.MainKt")
                (listOf(appJar.get()) + names.get()).forEach { appendLine("app.classpath=\$APPDIR\\$it") }
                appendLine()
                appendLine("[JavaOptions]")
                appendLine("java-options=-Djpackage.app-version=$version")
                appendLine("java-options=-Dcompose.application.resources.dir=\$APPDIR\\resources")
                appendLine("java-options=-Dcompose.application.configure.swing.globals=true")
                appendLine("java-options=-Dskiko.library.path=\$APPDIR")
            },
        )
    }
}

/** jpackage's GUI launcher, out of the Windows JDK's jdk.jpackage module (a zip with a 4-byte header). */
val windowsLauncher by tasks.registering {
    val jmod = File(windowsJdk.getOrElse("missing"), "jmods/jdk.jpackage.jmod")
    val out = layout.buildDirectory.file("windows/launcher/PhotoDuel.exe")
    inputs.file(jmod).optional()
    outputs.file(out)
    doLast {
        val bytes = jmod.readBytes()
        val zip = File(temporaryDir, "jdk.jpackage.zip").apply { writeBytes(bytes.copyOfRange(4, bytes.size)) }
        ZipFile(zip).use { z ->
            z.getInputStream(z.getEntry("classes/jdk/jpackage/internal/resources/jpackageapplauncherw.exe")).use { input ->
                out.get().asFile.outputStream().use(input::copyTo)
            }
        }
    }
}

val windowsAppImage by tasks.registering(Sync::class) {
    val enabled = packagingWindows
    onlyIf { enabled }
    into(layout.buildDirectory.dir("windows/PhotoDuel"))
    from(windowsLauncher)
    // jlink makes some files read-only, which would stop the next sync from replacing them.
    into("runtime") {
        from(windowsRuntime)
        filePermissions { user { write = true } }
    }
    into("app") {
        from(tasks.jar)
        from(windowsCfg)
        // Skia's DLL next to the jars, where skiko.library.path points, as the Compose packager does.
        val skiko = configurations.runtimeClasspath.map { cp -> cp.filter { it.name.startsWith("skiko-awt-runtime-windows") } }
        from(skiko.map { jars -> jars.map { zipTree(it) } }) { include("*.dll") }
    }
    into("app") {
        from(configurations.runtimeClasspath)
        eachFile { name = "${file.nameWithoutExtension}-${file.absolutePath.hashCode().toUInt().toString(16)}.jar" }
    }
    into("app/resources") { from(prepareNatives) }
}

val packageWindowsPortable by tasks.registering(Zip::class) {
    archiveFileName.set("PhotoDuel-windows-x64.zip")
    destinationDirectory.set(layout.buildDirectory.dir("windows"))
    from(windowsAppImage) { into("PhotoDuel") }
}
