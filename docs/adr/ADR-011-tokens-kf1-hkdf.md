# ADR-011: Slots de token con K_F1 + HKDF

**Estado:** aceptada (reemplaza la derivación de tokens de ADR-009)

## Contexto
Con Bouncy Castle, Argon2id es secuencial: ≈ 0,9 s (Estándar) a ≈ 4 s (Paranoico) por derivación. Derivar cada slot de token con su propio Argon2id costaba 7 derivaciones al crear la bóveda o cambiar F1, y hasta 6 al recuperar con un token (≈ 28 s en Paranoico). El costo de Argon2 en un slot de token solo protege F1: el token tiene ~125 bits aleatorios y no se puede adivinar.

## Decisión (política v1, regla R7)
```
K_F1  = Argon2id(len‖F1, salTokens, mismo perfil de la bóveda)   — una sola derivación
KEK_i = HKDF-SHA256(IKM = K_F1 ‖ token_i, salt = —, info = "ckp/v1/token-slot")
slot_i = AES-256-GCM(KEK_i, MK, AAD = Zona A ‖ i ‖ TOKEN)
```
- `salTokens` (32 B aleatorios) va en la Zona A, junto a sal₀.
- `token_i` = los 25 símbolos Base32 Crockford canónicos en ASCII (125 bits).
- La sal propia de cada slot no interviene en v1 (es relleno aleatorio).

## Consecuencias
- Crear o cambiar F1: 2 derivaciones (slot 0 + K_F1). Recuperar con token: 1 derivación + 6 HKDF.
- Una hoja de tokens robada no basta: hace falta F1, y probar cada F1 cuesta un Argon2id completo.
- Cambiar F1 cambia K_F1 → los 6 tokens se regeneran (la app no guarda los tokens).
- Cambiar F2, F3 o F4 no toca los slots de token.
