plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
}

dependencies {
    // Agrega module-info a los jars de terceros que no lo traen (PDFBox, commons-math3…): jlink no acepta
    // módulos automáticos.
    implementation("org.gradlex:extra-java-module-info:1.14.2")
}
