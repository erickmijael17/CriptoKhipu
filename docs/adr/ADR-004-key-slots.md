# ADR-004: Clave maestra con key slots

**Estado:** aceptada

## Contexto
Se necesitan tokens de recuperación y rotación de credenciales sin recifrar gigabytes.

## Decisión
Clave maestra aleatoria de 32 B envuelta en 7 slots (principal + 6 tokens), estilo LUKS/Cryptomator.

## Consecuencias
- Rotar credenciales solo reescribe slots.
- Cambiar F1 obliga a regenerar los tokens (ver ADR-009).
- El uso único de un token solo aplica a la copia actualizada del archivo.
