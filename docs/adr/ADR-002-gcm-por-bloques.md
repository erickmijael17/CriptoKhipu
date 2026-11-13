# ADR-002: AES-256-GCM por bloques de 1 MiB

**Estado:** aceptada

## Contexto
Videos de varios GB; GCM de un solo bloque obliga a tener todo en memoria y no permite acceso aleatorio.

## Decisión
Cifrar cada archivo en bloques de 1 MiB con AES-256-GCM, `nonce = 0⁴ ‖ índice`, `AAD = fileId ‖ índice ‖ esÚltimo` y una clave por archivo derivada con HKDF.

## Consecuencias
- Memoria constante y acceso aleatorio (necesario para el streaming de video).
- Se detecta reordenamiento, truncado y eliminación de bloques.
- Sobrecarga de 16 B por MiB.
