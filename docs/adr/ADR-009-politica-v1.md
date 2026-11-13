# ADR-009: Política de derivación v1

**Estado:** aceptada

## Decisión
- F1: solo dígitos, 12–25.
- F2: 12–25, mayúscula + minúscula + dígito, solo ASCII imprimible (0x20–0x7E), sin normalización.
- F3: 15–25 tras normalizar con NFD → sin diacríticos → minúsculas (`Locale.ROOT`) → recortar → colapsar espacios.
- Tokens: derivación reemplazada por ADR-011 (`K_F1` + HKDF); cambiar F1 regenera los 6.

## Consecuencias
- Las credenciales se pueden escribir en cualquier distribución de teclado.
- Cambiar cualquiera de estas reglas exige una política v2 (regla R7).
