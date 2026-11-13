# Especificación del formato `.kipu` — v1

> Estado: offsets fijados en la Fase 2 (implementación: `core/format`, `infra/storage/ContenedorKipu`).
> Todos los enteros son **big-endian**. Las cadenas del manifest son UTF-8 con prefijo de longitud de 2 B.

## 1. Disposición general

| Offset | Tamaño | Contenido |
|---|---|---|
| 0 | 16 KiB | Header primario |
| 16 KiB | 16 KiB | Header de respaldo (copia idéntica) |
| 32 KiB | variable | Blobs (append-only, bloques de 1 MiB + tag) |
| variable | variable | Manifest cifrado |
| final | fijo | Footer |

## 2. Header (16 KiB)

### Zona A — inmutable, autenticada (AAD de cada slot)
| Campo | Tamaño | Valor v1 |
|---|---|---|
| magic | 5 B | `CKIPU` |
| formatVersion | 2 B | `1` |
| algAead | 1 B | `1` = AES-256-GCM |
| algKdf | 1 B | `1` = Argon2id |
| algHash | 1 B | `1` = SHA-256 |
| politicaVersion | 2 B | `1` |
| normalizacionF3 | 1 B | `1` |
| argon2 m (KiB) / t / p | 4 + 1 + 1 B | según perfil |
| sal₀ | 32 B | aleatoria (slot principal) |
| salTokens | 32 B | aleatoria (`K_F1`, ADR-011) |
| vaultId | 16 B | aleatorio |
| tamañoBloque | 4 B | `1 048 576` |

### Mapa del bloque de 16 KiB

| Offset | Tamaño | Campo |
|---|---|---|
| 0 | 103 B | Zona A |
| 103 | 8 B | generaciónHeader (sube en cada reescritura; decide entre primario y respaldo) |
| 111 | 4 B | umbralAutodestrucción (0 = desactivada) |
| 115 | 1 B | requiereLlave (0/1): la UI pide F4; antes de desbloquear es solo una pista |
| 116 | 1 B | nSlots (1–7) |
| 117 | 7 × 93 B | slots (posiciones sin usar en cero) |
| 768 | 32 B | headerMAC = HMAC-SHA256(K_hdr, [0, 768)) |
| 800 | 4 B | CRC32C de [0, 800): detecta daño accidental **sin** la clave; elige la copia sana |
| 804 | 4 B | Zona C: intentosFallidos |
| 808 | 8 B | Zona C: pistaFooter (offset del último footer escrito) |
| 816 | resto | relleno aleatorio |

### Zona B — slots
Slot (93 B): `tipo (1 B: 0 vacío, 1 principal, 2 token) · sal (32 B) · nonce (12 B) · MK cifrada (32 B) · tag (16 B)`.
`AAD = Zona A ‖ índiceSlot (1 B) ‖ tipo (1 B)`; el **slot 0** agrega `‖ requiereLlave (1 B) ‖ versiónPolítica (2 B) ‖ umbralAutodestrucción (4 B)`
(ADR-012): manipular esos metadatos en una copia hace fallar su slot 0 y se usa la otra copia. Los slots de token no
llevan metadatos, para que cambiar la llave o el umbral no los invalide. Slot 0 = principal; 1–6 = tokens.
Un slot 0 de tipo vacío en todas las copias = bóveda autodestruida.
Un slot vacío son bytes aleatorios, indistinguibles de uno real. En v1 la sal propia del slot es relleno
aleatorio: las sales que intervienen son sal₀ y salTokens de la Zona A.

### Zona C — mutable, NO autenticada
Solo `intentosFallidos` y `pistaFooter`, en el header **primario**. **No confiable**: no la cubren ni el MAC ni el CRC.

### Elección y reparación del header
1. Se leen ambas copias; se descartan las que fallan el CRC o el formato.
2. Se prueba primero la de mayor generación (a igualdad, el primario).
3. Tras desbloquear, la primera copia cuyo MAC es válido se escribe sobre la otra si difieren.

## 3. Límites del parser

| Campo | Mínimo | Máximo |
|---|---|---|
| Argon2 `m` | 64 MiB | 2 GiB |
| Argon2 `t` | 2 | 10 |
| Argon2 `p` | 1 | 8 |
| Slots | 1 | 7 |
| Manifest | — | 64 MiB |
| Entradas en manifest | — | 1 000 000 |
| Tamaño de bloque | 1 MiB | 1 MiB |

Fuera de rango → `ParametrosFueraDeRango`. Algoritmo o versión desconocidos → `FormatoNoSoportado`.

## 4. Claves y nonces

| Elemento | Clave | Nonce |
|---|---|---|
| Slot 0 | `Argon2id(len‖F1 ‖ len‖F2 ‖ len‖norm(F3), sal₀, secreto = SHA-256(F4) o ninguno)` | aleatorio 12 B por escritura |
| Slots 1–6 | `HKDF-SHA256(K_F1 ‖ token_i, info="ckp/v1/token-slot")`, con `K_F1 = Argon2id(len‖F1, salTokens)` | aleatorio 12 B por escritura |

`len` = 4 B big-endian; los factores se codifican en UTF-8. `norm(F3)` = normalización v1 (NFD, sin
diacríticos, minúsculas, espacios colapsados). Token: 25 símbolos Base32 Crockford en ASCII
(formato visible `CKP-XXXXX-XXXXX-XXXXX-XXXXX-XXXXX-C`, con `C` = valor mod 37).
| Manifest | `HKDF(MK, "ckp/v1/manifest")` | aleatorio 12 B; `AAD = vaultId ‖ generación` |
| Bloques | `HKDF(MK, salt=fileId, "ckp/v1/file")` | `0⁴ ‖ índice (8 B)`; `AAD = fileId ‖ índice ‖ esÚltimo` |
| Header MAC | `HKDF(MK, "ckp/v1/header-mac")` | — |

## 5. Manifest

En disco: `nonce (12 B) ‖ AES-256-GCM(K_manifest, texto) (incluye tag)`.
Texto: `versión (1 B = 1) · generación (8 B) · nEntradas (4 B)` y, por entrada:
`fileId (16 B) · ruta (2 B + UTF-8, ≤ 4096 B) · tamaño (8 B) · modificado ms (8 B) · MIME (2 B + UTF-8, ≤ 255 B) · offset (8 B) · longitudCifrada (8 B)`.
Al desbloquear se verifica que cada `offset + longitudCifrada` quede antes del manifest.

## 6. Footer (33 B)

`offsetManifest (8 B) · longitudManifest (8 B) · generación (8 B) · magic "CKEND" (5 B) · CRC32C de los 29 B anteriores (4 B)`.
Válido solo si además `offsetManifest + longitudManifest` = posición del footer.

Localización al abrir:
1. Footer en los últimos 33 B del archivo.
2. Si no es válido: `pistaFooter` de la zona C. Como la pista **no está autenticada**, después de seguirla se busca entre la pista y el final del archivo un footer válido de **generación mayor**; si existe, se usa ese.
3. Si tampoco: búsqueda hacia atrás del magic (ventanas de 1 MiB) hasta el primer footer válido.

El footer no está autenticado, pero su generación sí queda ligada al manifest: `AAD del manifest = vaultId ‖ generación del footer`, y la generación dentro del manifest descifrado debe coincidir. No se puede combinar un manifest de otra bóveda o de otra generación con un footer distinto.

## 6 bis. Acceso al archivo abierto (ADR-010)

Mientras la bóveda está abierta, todo acceso pasa por un único `FileChannel` bloqueado con lecturas/escrituras posicionales. Nada vuelve a abrir la ruta (en Windows el bloqueo es obligatorio incluso para el mismo proceso).

## 7. Escritura (commit)

1. Verificar sistema de archivos (FAT/FAT32 → máximo 4 GiB − 1 B) y espacio libre (tamaño nuevo × 1,05).
2. Truncar la basura posterior al último footer válido.
3. Blobs → manifest → footer → `fsync` → actualizar `pistaFooter`.
4. Si falla con error de E/S: truncar hasta el final anterior.

Reescritura del header: respaldo → `fsync` → primario → `fsync` (generación + 1).
Compactar: copiar los blobs vivos (sin recifrar) a `<ruta>.tmp` → `fsync` → movimiento atómico → `fsync` del directorio (Linux/macOS; en Windows no se puede abrir un directorio como canal).

### Elección del header (antes y después de desbloquear)
- **Antes de desbloquear no se escribe nada en las zonas A ni B de ninguna copia.** Solo la Zona C del primario (intentos fallidos, pista del footer), que no cubren ni el CRC ni el MAC.
- Se prueba primero la copia de mayor generación con CRC válido; si su slot no abre o su MAC falla, se prueba la otra.
- Solo cuando una copia pasa el MAC se escribe sobre la otra (reparación).
- Un primario falso con generación mayor y CRC recalculado no puede destruir el respaldo.

## 8. Migración entre versiones

1. Abrir con las reglas de la versión del header.
2. Reenvolver la MK con la política nueva y reescribir ambos headers (respaldo primero).
3. Recifrar los bloques solo si cambia el AEAD.
