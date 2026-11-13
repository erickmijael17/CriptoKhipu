# ADR-006: Autodestrucción como disuasión

**Estado:** aceptada

## Decisión
Opcional y desactivada por defecto. Al llegar a N intentos fallidos se sobrescriben los slots de ambos headers; la Zona A queda para informar `BovedaDestruida`.

## Consecuencias
- Protege contra intentos dentro de la app, no contra copias ni ataques offline: el contador no está autenticado (ADR-008).
- La defensa real sigue siendo el costo de Argon2id.
