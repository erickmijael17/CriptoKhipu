// Casos de uso. Solo depende de core.
plugins {
    id("cryptokipu.java-conventions")
}

dependencies {
    api(project(":cryptokipu-core"))
}
