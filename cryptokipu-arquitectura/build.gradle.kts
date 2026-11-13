// Solo pruebas de arquitectura (ArchUnit) sobre todos los módulos.
plugins {
    id("cryptokipu.java-conventions")
}

dependencies {
    testImplementation(project(":cryptokipu-core"))
    testImplementation(project(":cryptokipu-app"))
    testImplementation(project(":cryptokipu-infra"))
    testImplementation(project(":cryptokipu-ui"))
    testImplementation(project(":cryptokipu-cli"))
    testImplementation(project(":cryptokipu-visores"))
    testImplementation(libs.archunit.junit)
}

// Los recursos de la interfaz van dentro del directorio de clases (ruta de módulos): sin esta dependencia Gradle
// detecta que se leen las clases de `ui` sin esperar a su `processResources`.
tasks.compileTestJava {
    dependsOn(":cryptokipu-ui:processResources")
}
