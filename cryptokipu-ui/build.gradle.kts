// Interfaz JavaFX (MVVM). Adaptador de entrada: solo llama a casos de uso.
plugins {
    id("cryptokipu.java-conventions")
    id("cryptokipu.modulos-externos")
}

val versionFx = libs.versions.javafx.get()
val modulosFx = listOf("base", "graphics", "controls", "fxml", "media")
val plataformasFx = listOf("win", "linux", "linux-aarch64", "mac", "mac-aarch64")

/** Classifier de JavaFX del equipo que compila. */
val plataformaFx: String = run {
    val sistemaMija = System.getProperty("os.name").lowercase()
    val arm64Mija = System.getProperty("os.arch").let { it == "aarch64" || it == "arm64" }
    when {
        sistemaMija.contains("win") -> "win"
        sistemaMija.contains("mac") -> if (arm64Mija) "mac-aarch64" else "mac"
        else -> if (arm64Mija) "linux-aarch64" else "linux"
    }
}

// Solo para registrar en verification-metadata los JavaFX de TODAS las plataformas (CI en Linux y macOS).
val javafxTodasLasPlataformas: Configuration by configurations.creating {
    isCanBeConsumed = false
    isTransitive = false
}

dependencies {
    implementation(project(":cryptokipu-app"))
    implementation(project(":cryptokipu-infra"))

    // Solo los jars con classifier: los jars "vacíos" sin classifier duplicarían los módulos javafx.*.
    modulosFx.forEach { moduloMija ->
        implementation("org.openjfx:javafx-$moduloMija:$versionFx:$plataformaFx") { isTransitive = false }
        plataformasFx.forEach { plataformaMija ->
            javafxTodasLasPlataformas("org.openjfx:javafx-$moduloMija:$versionFx:$plataformaMija")
        }
    }
    // Visores de PDF y Office (opcional): solo en tiempo de ejecución, por ServiceLoader. `-PsinVisores` lo deja fuera.
    if (!providers.gradleProperty("sinVisores").isPresent) {
        runtimeOnly(project(":cryptokipu-visores"))
    }
    // Las pruebas de la vista previa generan PDF y Word reales y usan el proveedor de visores directamente.
    testImplementation(project(":cryptokipu-visores"))
    testImplementation(libs.pdfbox)
    testImplementation(libs.poi.ooxml)
    implementation(libs.atlantafx) { exclude(group = "org.openjfx") }
    implementation(libs.ikonli.javafx) { exclude(group = "org.openjfx") }
    implementation(libs.ikonli.feather) { exclude(group = "org.openjfx") }
}

// En la ruta de módulos los recursos deben estar dentro del módulo: FXML, CSS y textos van junto a las clases.
sourceSets.main {
    output.setResourcesDir(java.classesDirectory)
}

val rutaEjecucionUi = sourceSets.main.get().runtimeClasspath
val opcionesFx = listOf("--enable-native-access=javafx.graphics")

// ./gradlew :cryptokipu-ui:ejecutarUi   (modo desarrollo: detector de bloqueos del hilo de JavaFX)
tasks.register<JavaExec>("ejecutarUi") {
    group = "aplicación"
    description = "Abre la interfaz de CryptoKipu."
    classpath = rutaEjecucionUi
    mainModule = "dev.erick.cryptokipu.ui"
    mainClass = "dev.erick.cryptokipu.ui.Launcher"
    javaLauncher = javaToolchains.launcherFor(java.toolchain)
    jvmArgs(OpcionesJvm.comunes + opcionesFx + "-Dcryptokipu.desarrollo=true")
}

// ./gradlew :cryptokipu-ui:generarCapturas   (escribe docs/capturas/*.png; necesita pantalla)
tasks.register<JavaExec>("generarCapturas") {
    group = "documentación"
    description = "Genera capturas de cada pantalla para el README."
    classpath = rutaEjecucionUi
    mainModule = "dev.erick.cryptokipu.ui"
    mainClass = "dev.erick.cryptokipu.ui.capturas.GeneradorCapturas"
    javaLauncher = javaToolchains.launcherFor(java.toolchain)
    jvmArgs(OpcionesJvm.comunes + opcionesFx)
    args(rootProject.layout.projectDirectory.dir("docs/capturas").asFile.absolutePath)
}

// Pruebas sin toolkit (ViewModels): en la JVM de `test` nunca se inicia JavaFX.
tasks.test {
    jvmArgs(opcionesFx)
    useJUnitPlatform { excludeTags("toolkit") }
}

// Pruebas con toolkit (@Tag("toolkit")) en su propia JVM: se omiten sin pantalla; en CI de Linux, xvfb-run.
val pruebasToolkit = tasks.register<Test>("pruebasToolkit") {
    group = "verification"
    description = "Pruebas que necesitan el toolkit de JavaFX (pantalla real o Xvfb)."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs(opcionesFx)
    useJUnitPlatform { includeTags("toolkit") }
    shouldRunAfter(tasks.test)
}
tasks.check { dependsOn(pruebasToolkit) }

// ./gradlew :cryptokipu-ui:opcionesJpackage  → una opción de `--java-options` por línea (modo portátil).
tasks.register("opcionesJpackage") {
    group = "documentación"
    description = "Imprime las opciones de JVM del modo portátil para jpackage --java-options."
    val opcionesMija = OpcionesJvm.portatil
    doLast {
        opcionesMija.forEach { println("--java-options \"$it\"") }
    }
}
