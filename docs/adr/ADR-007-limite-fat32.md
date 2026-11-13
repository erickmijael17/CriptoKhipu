# ADR-007: Límite de 4 GiB en FAT32

**Estado:** aceptada

## Contexto
Muchos USB vienen en FAT32, que no admite archivos de más de 4 GiB − 1 B.

## Decisión
v1: detectar el sistema de archivos del destino y bloquear **antes** de escribir (`LimiteSistemaArchivos`), recomendando exFAT o NTFS. v1.1: volúmenes divididos (`.kipu.001`, `.002`…).

## Consecuencias
- La bóveda nunca queda escrita a medias por este motivo.
- Hasta v1.1, una bóveda grande exige reformatear el USB.
