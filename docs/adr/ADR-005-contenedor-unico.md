# ADR-005: Contenedor de un solo archivo

**Estado:** aceptada

## Contexto
El usuario transporta la bóveda por USB, disco o Drive.

## Decisión
Un único archivo `.kipu` en lugar de una carpeta con un archivo cifrado por cada original.

## Consecuencias
- Fácil de copiar y mover; no revela cuántos archivos hay ni sus tamaños individuales.
- Drive vuelve a subir el archivo completo con cada cambio.
- Límite de 4 GiB en FAT32 (ver ADR-007).
