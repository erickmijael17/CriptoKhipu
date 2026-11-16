// Dominio + criptografía. Sin UI, sin red, sin E/S de archivos.
// Única dependencia externa permitida: Bouncy Castle (Argon2id).
plugins {
    id("cryptokipu.java-conventions")
    id("me.champeau.jmh") version "0.7.4"
    id("info.solidsoft.pitest") version "1.19.0"
}

dependencies {
    implementation(libs.bouncycastle.prov)
    testImplementation(libs.jqwik)
    testImplementation(libs.jazzer.junit)
}

registrarObjetivosFuzz(listOf(
    "dev.erick.cryptokipu.core.format.FuzzFormatoTest.headerNuncaFallaConUnErrorNoControlado",
    "dev.erick.cryptokipu.core.format.FuzzFormatoTest.manifestNuncaFallaConUnErrorNoControlado",
    "dev.erick.cryptokipu.core.format.FuzzFormatoTest.footerNuncaFallaConUnErrorNoControlado",
    "dev.erick.cryptokipu.core.auth.FuzzTokenTest.leerTokenSoloAceptaOFallaConTokenMalEscrito",
    "dev.erick.cryptokipu.core.vault.FuzzRutaInternaTest.rutaInternaNuncaLanzaYLasSegurasNoEscapan",
))

// ./gradlew :cryptokipu-core:generarSemillasFuzz → regenera el corpus semilla en src/test/resources (se versiona).
tasks.register<JavaExec>("generarSemillasFuzz") {
    group = "verificación"
    description = "Genera el corpus semilla del fuzzing de header, manifest, footer, tokens y rutas."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "dev.erick.cryptokipu.core.format.GeneradorSemillasFuzz"
    javaLauncher = javaToolchains.launcherFor(java.toolchain)
    jvmArgs(OpcionesJvm.ACCESO_NATIVO_CLASSPATH)
    args(layout.projectDirectory.dir("src/test/resources").asFile.absolutePath)
}

// Benchmarks: ./gradlew :cryptokipu-core:jmh  (no forman parte de `check`).
jmh {
    jmhVersion = "1.37"
    fork = 1
    warmupIterations = 1
    iterations = 3
    // El modo de cada benchmark va en su anotación: Argon2 es SingleShotTime; cifrado y manifest, AverageTime con calentamiento.
    jvmArgsAppend = listOf("-Xmx3g", OpcionesJvm.ACCESO_NATIVO_CLASSPATH) +
        OpcionesJvm.comunes.filterNot { it.startsWith("-XX:MaxRAMPercentage") }
    resultFormat = "JSON"
}

// Mutaciones (Fase 9): ./gradlew :cryptokipu-core:pitest → build/reports/pitest/index.html
// Alcance: core.crypto, core.format y core.auth. Umbral mínimo de mutantes detectados.
pitest {
    pitestVersion = libs.versions.pit.get()
    junit5PluginVersion = libs.versions.pitjunit.get()
    targetClasses = listOf(
        "dev.erick.cryptokipu.core.crypto.*",
        "dev.erick.cryptokipu.core.format.*",
        "dev.erick.cryptokipu.core.auth.*",
    )
    // Exclusiones justificadas: BloqueoMemoria es pegamento FFM con el sistema operativo (VirtualLock/mlock/
    // SetProcessWorkingSetSize): sus mutantes dependen del SO y se prueban de forma integrada en SecureBytes.
    // hashCode solo tiene que ser coherente con equals, que sí se prueba campo por campo.
    excludedTestClasses = listOf("*Fuzz*")
    excludedClasses = listOf("dev.erick.cryptokipu.core.crypto.BloqueoMemoria*")
    excludedMethods = listOf("hashCode")
    targetTests = listOf("dev.erick.cryptokipu.core.*")
    threads = 4
    mutators = listOf("STRONGER")
    outputFormats = listOf("XML", "HTML")
    timestampedReports = false
    mutationThreshold = 80
    jvmArgs = OpcionesJvm.comunes.filterNot { it.startsWith("-XX:MaxRAMPercentage") } + OpcionesJvm.ACCESO_NATIVO_CLASSPATH
}
