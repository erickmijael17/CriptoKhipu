// Convenciones comunes a todos los módulos Java de CryptoKipu.

import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort
import com.github.spotbugs.snom.SpotBugsTask

plugins {
    `java-library`
    checkstyle
    jacoco
    id("com.github.spotbugs")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

group = "dev.erick.cryptokipu"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(libs.findVersion("java").get().requiredVersion)
        vendor = JvmVendorSpec.ADOPTIUM
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = libs.findVersion("java").get().requiredVersion.toInt()
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

// Regla R2/R5: sufijo `Mija` en todas las variables, en main y en test.
checkstyle {
    toolVersion = libs.findVersion("checkstyle").get().requiredVersion
    configFile = rootProject.file("config/checkstyle/checkstyle.xml")
    isIgnoreFailures = false
    maxWarnings = 0
}

// Cobertura (Fase 9): reporte en build/reports/jacoco; sin umbral rígido (el porcentaje va en el README).
jacoco {
    toolVersion = libs.findVersion("jacoco").get().requiredVersion
}
tasks.withType<JacocoReport>().configureEach {
    reports {
        xml.required = true
        html.required = true
    }
}
tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}
tasks.jacocoTestReport {
    dependsOn(tasks.test)
}

// Análisis estático (Fase 9): SpotBugs + FindSecBugs. Falla con CUALQUIER hallazgo de prioridad alta; los falsos
// positivos se suprimen en config/spotbugs/excluir.xml, cada uno con su justificación escrita.
spotbugs {
    toolVersion = libs.findVersion("spotbugs").get().requiredVersion
    effort = Effort.MAX
    // -PspotbugsMedio muestra también los hallazgos de prioridad media (informativo).
    reportLevel = if (providers.gradleProperty("spotbugsMedio").isPresent) Confidence.MEDIUM else Confidence.HIGH
    excludeFilter = rootProject.file("config/spotbugs/excluir.xml")
    ignoreFailures = false
}
// Solo se analiza el código de producción: las pruebas usan reflexión, aleatoriedad y rutas a propósito.
tasks.named("spotbugsTest") { enabled = false }
tasks.withType<SpotBugsTask>().configureEach {
    reports {
        create("html") { required = true }
        create("xml") { required = true }
    }
}

dependencies {
    spotbugsPlugins(libs.findLibrary("findsecbugs").get())
    testImplementation(platform(libs.findLibrary("junit-bom").get()))
    testImplementation(libs.findLibrary("junit-jupiter").get())
    testRuntimeOnly(libs.findLibrary("junit-launcher").get())
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    val carpetaPruebas = layout.buildDirectory.dir("jvm-pruebas").get().asFile
    doFirst {
        File(carpetaPruebas, "tmp").mkdirs()
        File(carpetaPruebas, "logs").mkdirs()
    }
    jvmArgs(OpcionesJvm.conCarpeta(carpetaPruebas.invariantSeparatorsPath))
}

// Fuzzing (Fase 9). `check` repite el corpus de los @FuzzTest como regresión (rápido). `fuzzLargo` fuzzea de verdad
// (JAZZER_FUZZ=1; cada método tiene su maxDuration): ./gradlew fuzzLargo. Los hallazgos quedan en
// build/fuzz/<módulo>; cada uno se corrige y su entrada se agrega al corpus de src/test/resources.
// Jazzer se acopla a la JVM con un agente (auto-adjuntado): por eso estas JVM llevan `allowAttachSelf` y NO
// `-XX:+DisableAttachMechanism`. Es solo para pruebas; la aplicación real mantiene el mecanismo desactivado.
tasks.test {
    filter {
        excludeTestsMatching("*Fuzz*Test")
        isFailOnNoMatchingTests = false
    }
}
fun Test.configurarFuzz() {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    filter {
        includeTestsMatching("*Fuzz*Test")
        isFailOnNoMatchingTests = false
    }
    val carpetaPruebas = layout.buildDirectory.dir("jvm-pruebas").get().asFile
    workingDir = layout.buildDirectory.dir("fuzz").get().asFile
    doFirst {
        workingDir.mkdirs()
        File(carpetaPruebas, "tmp").mkdirs()
        File(carpetaPruebas, "logs").mkdirs()
    }
    // Se asigna al ejecutar (doFirst) para REEMPLAZAR los argumentos que `withType<Test>` agregó (con DisableAttach).
    val argumentosFuzz = OpcionesJvm.conCarpeta(carpetaPruebas.invariantSeparatorsPath)
        .filterNot { it == "-XX:+DisableAttachMechanism" } + "-Djdk.attach.allowAttachSelf=true"
    doFirst { jvmArgs = argumentosFuzz }
    // Dos agentes (JaCoCo y el de Jazzer) no se llevan bien con el auto-adjuntado: el fuzzing no mide cobertura de JaCoCo.
    extensions.configure<JacocoTaskExtension> { isEnabled = false }
}
val fuzzRegresion = tasks.register<Test>("fuzzRegresion") {
    group = "verificación"
    description = "Repite el corpus semilla de los @FuzzTest como pruebas de regresión (corre en `check`)."
    configurarFuzz()
}
// Jazzer solo fuzzea UN método por JVM: cada objetivo tiene su tarea fuzz_<método> (ver Fuzzing.kt) y esta las agrupa.
tasks.register("fuzzLargo") {
    group = "verificación"
    description = "Fuzzing real con Jazzer (lento, maxDuration por método). La regresión corta corre en `check`."
}
tasks.check { dependsOn(fuzzRegresion) }

// Builds reproducibles (Fase 9): los JAR no llevan fechas y sus entradas van en orden fijo. Dos builds limpios dan los
// mismos SHA-256 (tools/verificar_reproducible.py lo comprueba y corre en CI).
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

// Builds reproducibles: versiones bloqueadas en gradle.lockfile.
dependencyLocking {
    lockAllConfigurations()
}

tasks.register("resolverYBloquearTodo") {
    group = "verificación"
    description = "Resuelve todas las configuraciones para escribir los lockfiles (usar con --write-locks)."
    notCompatibleWithConfigurationCache("Resuelve configuraciones en tiempo de ejecución")
    doFirst {
        require(gradle.startParameter.isWriteDependencyLocks) { "Ejecutar con --write-locks" }
    }
    doLast {
        configurations.filter { it.isCanBeResolved }.forEach { it.resolve() }
    }
}
