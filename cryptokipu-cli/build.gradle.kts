// Línea de comandos (picocli se agrega en la Fase 5).
plugins {
    id("cryptokipu.java-conventions")
}

dependencies {
    implementation(project(":cryptokipu-app"))
    implementation(project(":cryptokipu-infra"))
}
