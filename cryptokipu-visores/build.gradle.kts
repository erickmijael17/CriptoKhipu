// Visores de documentos en RAM (PDF con PDFBox, Office con Apache POI). Módulo opcional: implementa el puerto
// VisorDocumentos y la interfaz lo encuentra con ServiceLoader. Sin él solo hay vista previa de imágenes, texto y
// medios; así el núcleo nunca carga POI ni PDFBox.
plugins {
    id("cryptokipu.java-conventions")
    id("cryptokipu.modulos-externos")
}

dependencies {
    implementation(project(":cryptokipu-app"))
    implementation(libs.pdfbox)
    implementation(libs.poi.ooxml)
}

tasks.test {
    // Java2D sin pantalla (diapositivas de PowerPoint, páginas de PDF).
    jvmArgs("-Djava.awt.headless=true")
}

// ./gradlew :cryptokipu-visores:verificarImagenJlink
// Construye una imagen jlink SOLO con el módulo de visores (y lo que arrastra: PDFBox, POI, xmlbeans…) y ejecuta
// una comprobación de humo dentro de ella. Demuestra que todos los jars tienen module-info y funcionan sin classpath.
val imagenJlink = layout.buildDirectory.dir("imagen-jlink")
tasks.register("verificarImagenJlink") {
    group = "verificación"
    description = "Crea una imagen jlink con los visores y abre un PDF, un Word, un Excel y un PowerPoint en ella."
    dependsOn(tasks.jar)
    notCompatibleWithConfigurationCache("Ejecuta jlink con la ruta de módulos resuelta")
    val herramientasMija = javaToolchains.launcherFor(java.toolchain).map { it.metadata.installationPath.asFile }
    val rutaModulosMija = provider { (configurations.runtimeClasspath.get().files + tasks.jar.get().archiveFile.get().asFile)
        .joinToString(File.pathSeparator) }
    doLast {
        val jdkMija = herramientasMija.get()
        val extension = if (System.getProperty("os.name").lowercase().contains("win")) ".exe" else ""
        val salidaMija = imagenJlink.get().asFile
        salidaMija.deleteRecursively()
        fun ejecutar(vararg orden: String) {
            val procesoMija = ProcessBuilder(*orden).redirectErrorStream(true).start()
            val salidaTextoMija = procesoMija.inputStream.bufferedReader().readText()
            println(salidaTextoMija)
            check(procesoMija.waitFor() == 0) { "Falló ${File(orden.first()).name}: $salidaTextoMija" }
        }
        ejecutar(File(jdkMija, "bin/jlink$extension").absolutePath, "--module-path", rutaModulosMija.get(),
            "--add-modules", "dev.erick.cryptokipu.visores", "--output", salidaMija.absolutePath,
            "--strip-debug", "--no-header-files", "--no-man-pages", "--ignore-signing-information")
        ejecutar(File(salidaMija, "bin/java$extension").absolutePath, "-Djava.awt.headless=true",
            "-m", "dev.erick.cryptokipu.visores/dev.erick.cryptokipu.visores.Autoverificacion")
    }
}
