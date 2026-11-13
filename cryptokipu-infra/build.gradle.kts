// Adaptadores de salida: almacenamiento del .kipu, llaves, aleatoriedad, temporales.
plugins {
    id("cryptokipu.java-conventions")
}

dependencies {
    api(project(":cryptokipu-app"))
    testImplementation(libs.jqwik)
}
