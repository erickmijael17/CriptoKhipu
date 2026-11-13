# ADR-010: Un único FileChannel por bóveda abierta

**Estado:** aceptada

## Contexto
En Windows, `FileChannel.tryLock()` es un bloqueo obligatorio: mientras la bóveda está abierta, ni otros programas ni **otro descriptor del mismo proceso** pueden leer el archivo. El visor y el servidor de medios de la Fase 7 necesitan leer bloques del `.kipu` mientras está abierto, desde varios hilos.

## Decisión
Todo acceso al `.kipu` abierto pasa por el único `FileChannel` de `ContenedorKipu`, con lecturas y escrituras **posicionales** (`read(buffer, posición)` / `write(buffer, posición)`), que son seguras entre hilos y no dependen de la posición del canal. Ningún componente vuelve a abrir la ruta del `.kipu` mientras está abierto: lee mediante `ContenedorKipu` (`extraer`, flujos de rango).

## Consecuencias
- Funciona igual en Windows, Linux y macOS, con el bloqueo activo.
- El visor y el streaming de video (Fase 7) leen en paralelo sin abrir otro descriptor.
- Las escrituras (agregar, eliminar, compactar, rotar) no se ejecutan en paralelo con otras escrituras; la capa de casos de uso las serializa.
- La única excepción es la compactación, que escribe en `<ruta>.tmp` y después cierra, mueve y reabre el canal.
