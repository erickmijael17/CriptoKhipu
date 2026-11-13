// Línea de comandos (picocli). Adaptador de entrada: solo llama a casos de uso.
plugins {
    id("cryptokipu.java-conventions")
}

dependencies {
    implementation(project(":cryptokipu-app"))
    implementation(project(":cryptokipu-infra"))
    implementation(libs.picocli)
}

val rutaEjecucion = sourceSets.main.get().runtimeClasspath

tasks.test {
    // La prueba de punta a punta lanza la CLI como subproceso real (y como imagen jlink).
    dependsOn(tasks.jar)
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf("-Dkipu.classpath=" + rutaEjecucion.asPath)
    })
}

// Autocompletado de bash y páginas de ayuda en build/docs (./gradlew :cryptokipu-cli:generarDocsCli).
tasks.register<JavaExec>("generarDocsCli") {
    group = "documentación"
    description = "Genera el script de autocompletado y la ayuda de cada subcomando en build/docs."
    classpath = rutaEjecucion
    mainClass = "dev.erick.cryptokipu.cli.GeneradorDocs"
    javaLauncher = javaToolchains.launcherFor(java.toolchain)
    args(layout.buildDirectory.dir("docs").get().asFile.absolutePath)
}
