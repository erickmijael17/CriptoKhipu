# ADR-008: Header primario + respaldo en zona fija

**Estado:** aceptada

## Contexto
1. El `failCounter` se incrementa sin la clave maestra, así que no puede estar bajo el `headerMAC`.
2. Copias del header en la zona append-only dejarían slots viejos enterrados: credenciales y tokens anteriores seguirían abriendo la bóveda.

## Decisión
Header de 16 KiB en tres zonas (A inmutable, B slots, C mutable no autenticada) y una copia idéntica a continuación, ambas reescritas en el mismo lugar (respaldo primero, luego primario). El footer nunca contiene slots.

## Consecuencias
- Rotar y autodestruir eliminan todas las copias de los slots viejos dentro del archivo.
- Un header dañado se recupera desde el respaldo.
- El `failCounter` es no confiable y así se documenta.
