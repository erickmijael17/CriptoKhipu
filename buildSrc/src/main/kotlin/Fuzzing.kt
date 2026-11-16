import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register

/**
 * Jazzer solo admite UN @FuzzTest por JVM cuando fuzzea de verdad (JAZZER_FUZZ=1): los demás se omiten. Por eso cada
 * objetivo (`paquete.Clase.método`) tiene su propia tarea `fuzz_<método>` y `fuzzLargo` las ejecuta una tras otra.
 *
 * Esas JVM llevan `allowAttachSelf` y NO `-XX:+DisableAttachMechanism` (Jazzer se acopla con un agente auto-adjuntado)
 * y no miden cobertura de JaCoCo (dos agentes no se llevan bien). Es solo para pruebas.
 */
fun Project.registrarObjetivosFuzz(objetivos: List<String>) {
    val fuentes = extensions.getByType<SourceSetContainer>()
    val carpetaPruebas = layout.buildDirectory.dir("jvm-pruebas").get().asFile
    val tareas = objetivos.map { objetivo ->
        val metodo = objetivo.substringAfterLast('.')
        tasks.register<Test>("fuzz_$metodo") {
            group = "verificación"
            description = "Fuzzing real de $objetivo (Jazzer)."
            testClassesDirs = fuentes.getByName("test").output.classesDirs
            classpath = fuentes.getByName("test").runtimeClasspath
            useJUnitPlatform()
            filter {
                includeTestsMatching(objetivo)
                isFailOnNoMatchingTests = true
            }
            environment("JAZZER_FUZZ", "1")
            workingDir = layout.buildDirectory.dir("fuzz").get().asFile
            outputs.upToDateWhen { false }
            val argumentos = OpcionesJvm.conCarpeta(carpetaPruebas.invariantSeparatorsPath)
                .filterNot { it == "-XX:+DisableAttachMechanism" } + "-Djdk.attach.allowAttachSelf=true"
            doFirst {
                workingDir.mkdirs()
                java.io.File(carpetaPruebas, "tmp").mkdirs()
                java.io.File(carpetaPruebas, "logs").mkdirs()
                jvmArgs = argumentos
            }
            extensions.configure(org.gradle.testing.jacoco.plugins.JacocoTaskExtension::class.java) { isEnabled = false }
        }
    }
    // Una tras otra: cada una usa toda la CPU y la memoria.
    tareas.zipWithNext().forEach { (anterior, siguiente) -> siguiente.configure { mustRunAfter(anterior) } }
    tasks.named("fuzzLargo") { dependsOn(tareas) }
}
