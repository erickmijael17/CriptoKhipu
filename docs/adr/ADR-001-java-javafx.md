# ADR-001: Java 25 LTS + JavaFX

**Estado:** aceptada

## Contexto
Aplicación de escritorio 100% offline, portable por USB, que maneja claves en memoria.

## Decisión
Java 25 LTS con JavaFX 25, en lugar de Electron, Go o Rust.

## Consecuencias
- API estándar de derivación (`javax.crypto.KDF`, JEP 510) y FFM para memoria fuera del heap.
- `jlink` + `jpackage` generan una imagen con el runtime incluido, ejecutable desde un USB.
- Imagen de ~80–120 MB por sistema operativo.
