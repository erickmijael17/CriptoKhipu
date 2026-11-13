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
    testImplementation(libs.archunit.junit)
}
