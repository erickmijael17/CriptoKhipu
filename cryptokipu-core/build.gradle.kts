// Dominio + criptografía. Sin UI, sin red, sin E/S de archivos.
// Única dependencia externa permitida: Bouncy Castle (Argon2id).
plugins {
    id("cryptokipu.java-conventions")
    id("me.champeau.jmh") version "0.7.4"
}

dependencies {
    implementation(libs.bouncycastle.prov)
    testImplementation(libs.jqwik)
}

// Benchmarks: ./gradlew :cryptokipu-core:jmh  (no forman parte de `check`).
jmh {
    jmhVersion = "1.37"
    fork = 1
    warmupIterations = 1
    iterations = 3
    benchmarkMode = listOf("ss")
    timeUnit = "ms"
    jvmArgsAppend = listOf("-Xmx3g") + OpcionesJvm.comunes.filterNot { it.startsWith("-XX:MaxRAMPercentage") }
    resultFormat = "JSON"
}
