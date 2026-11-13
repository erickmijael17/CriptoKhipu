plugins {
    // Descarga el JDK 25 (toolchain) a ~/.gradle/jdks si no está instalado.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "cryptokipu"

include(
    "cryptokipu-core",
    "cryptokipu-app",
    "cryptokipu-infra",
    "cryptokipu-ui",
    "cryptokipu-cli",
    "cryptokipu-visores",
    "cryptokipu-arquitectura",
)

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}
