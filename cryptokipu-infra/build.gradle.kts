// Adaptadores de salida: almacenamiento del .kipu, llaves, aleatoriedad, temporales.
plugins {
    id("cryptokipu.java-conventions")
    id("me.champeau.jmh") version "0.7.4"
    id("info.solidsoft.pitest") version "1.19.0"
}

dependencies {
    api(project(":cryptokipu-app"))
    testImplementation(libs.jqwik)
    testImplementation(libs.jazzer.junit)
}

// Pruebas pesadas (@Tag("pesada")): fuera de `check`. ./gradlew :cryptokipu-infra:pruebasPesadas
tasks.test {
    useJUnitPlatform { excludeTags("pesada") }
}
tasks.register<Test>("pruebasPesadas") {
    group = "verificación"
    description = "Archivo de 5 GB, 100 000 archivos, compactar 5 GB y el límite de FAT32 (lento; ~15 GB de disco)."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("pesada") }
    maxHeapSize = "2g"
    outputs.upToDateWhen { false }
    testLogging { events("passed", "failed", "skipped"); showStandardStreams = true }
}

// Benchmarks: ./gradlew :cryptokipu-infra:jmh  (no forman parte de `check`).
jmh {
    jmhVersion = "1.37"
    fork = 1
    warmupIterations = 3
    iterations = 5
    timeUnit = "us"
    resultFormat = "JSON"
    jvmArgsAppend = listOf(OpcionesJvm.ACCESO_NATIVO_CLASSPATH) +
        OpcionesJvm.comunes.filterNot { it.startsWith("-XX:MaxRAMPercentage") }
}

registrarObjetivosFuzz(listOf(
    "dev.erick.cryptokipu.infra.storage.FuzzContenedorTest.abrirNuncaFallaConUnErrorNoControlado",
))

// NO regenerar salvo cambio de versión de formato deliberado (entonces nace golden/v2 y v1 se conserva).
tasks.register<JavaExec>("generarGolden") {
    group = "verificación"
    description = "Genera la bóveda golden v1 (compatibilidad de formato) en src/test/resources/golden/v1."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "dev.erick.cryptokipu.infra.compat.GeneradorGolden"
    javaLauncher = javaToolchains.launcherFor(java.toolchain)
    jvmArgs(OpcionesJvm.ACCESO_NATIVO_CLASSPATH)
    args(layout.projectDirectory.dir("src/test/resources").asFile.absolutePath)
}

tasks.register<JavaExec>("generarSemillasFuzz") {
    group = "verificación"
    description = "Genera la plantilla de headers y el corpus semilla del fuzzing de la apertura de bóvedas."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "dev.erick.cryptokipu.infra.storage.GeneradorSemillasFuzz"
    javaLauncher = javaToolchains.launcherFor(java.toolchain)
    jvmArgs(OpcionesJvm.ACCESO_NATIVO_CLASSPATH)
    args(layout.projectDirectory.dir("src/test/resources").asFile.absolutePath)
}

// Mutaciones de los casos de uso (Fase 9): ./gradlew :cryptokipu-infra:pitest
// Las clases mutadas viven en `app` (dev.erick.cryptokipu.app.casos); sus pruebas, en este módulo (bóvedas reales).
pitest {
    pitestVersion = libs.versions.pit.get()
    junit5PluginVersion = libs.versions.pitjunit.get()
    targetClasses = listOf("dev.erick.cryptokipu.app.casos.*")
    targetTests = listOf("dev.erick.cryptokipu.infra.casos.*", "dev.erick.cryptokipu.infra.compat.*",
        "dev.erick.cryptokipu.app.casos.*")
    excludedTestClasses = listOf("*Fuzz*", "*Pesadas*", "*LectorPython*", "*AuditoriaDeFugas*")
    additionalMutableCodePaths = listOf(project(":cryptokipu-app").tasks.named<Jar>("jar").get().archiveFile.get().asFile)
    threads = 4
    mutators = listOf("STRONGER")
    outputFormats = listOf("XML", "HTML")
    timestampedReports = false
    mutationThreshold = 70
    jvmArgs = OpcionesJvm.comunes.filterNot { it.startsWith("-XX:MaxRAMPercentage") } + OpcionesJvm.ACCESO_NATIVO_CLASSPATH
}
