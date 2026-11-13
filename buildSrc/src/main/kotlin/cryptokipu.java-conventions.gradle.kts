// Convenciones comunes a todos los módulos Java de CryptoKipu.

plugins {
    `java-library`
    checkstyle
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

dependencies {
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
