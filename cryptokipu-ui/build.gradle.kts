// Interfaz JavaFX (MVVM). JavaFX, AtlantaFX e Ikonli se agregan en la Fase 6.
plugins {
    id("cryptokipu.java-conventions")
}

dependencies {
    implementation(project(":cryptokipu-app"))
    implementation(project(":cryptokipu-infra"))
}
