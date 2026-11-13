# CryptoKipu

Cifrador de archivos de escritorio, 100% offline, escrito en Java 25. En desarrollo: ver [PLAN_IMPLEMENTACION.md](PLAN_IMPLEMENTACION.md).

## Compilar y verificar

Requiere solo un JDK 17 o superior para arrancar Gradle; el JDK 25 (Temurin) lo descarga Gradle la primera vez.

```bash
./gradlew check
```

`check` compila, ejecuta las pruebas, la regla de nombres `Mija` (Checkstyle) y las reglas de arquitectura (ArchUnit).

## Actualizar dependencias

Después de cambiar una versión en `gradle/libs.versions.toml`:

```bash
./gradlew resolverYBloquearTodo check --write-locks --write-verification-metadata sha256
```

Revisa el diff de `gradle/verification-metadata.xml` y de los `gradle.lockfile` antes de confirmarlo.

## Documentación

- [Modelo de amenazas](docs/THREAT_MODEL.md)
- [Formato `.kipu`](docs/FORMAT_SPEC.md)
- [Política de registro](docs/POLITICA_REGISTRO.md)
- [Decisiones de arquitectura (ADR)](docs/adr/)

## Licencia

Apache-2.0. Ver [LICENSE](LICENSE) y [NOTICE](NOTICE).
