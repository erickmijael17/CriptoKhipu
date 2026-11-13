# CryptoKipu: arquitectura base (v1)

> **Interpretación:** tomo como autenticadores el **PIN** y la **contraseña alfanumérica** (obligatorios), un **tercer factor que el usuario elige** entre tres tipos, y la **llave de seguridad (archivo)**. Aparte, los **tokens de recuperación de uso único**. Si quieres otra combinación, solo cambia la "política" del encabezado; la arquitectura es la misma.

---

## 1. Stack tecnológico

| Capa | Tecnología | Por qué |
|---|---|---|
| Lenguaje / runtime | **Java 25 LTS** | Versión LTS vigente. Trae la API estándar de derivación de claves (HKDF nativo, JEP 510), *records*, *sealed types*, *pattern matching* e hilos virtuales. |
| Cifrado de datos | **AES-256-GCM** (JCA nativo) | Cifrado autenticado estándar, acelerado por hardware (AES-NI). |
| Derivación de claves | **Argon2id** (Bouncy Castle `bcprov-jdk18on`) | Recomendado por OWASP y RFC 9106. El JDK no lo incluye. |
| Subclaves | **HKDF-SHA256** (`javax.crypto.KDF`, JDK 25) | Deriva claves por archivo, índice y MAC a partir de la clave maestra. |
| Hash de la llave de seguridad | **SHA-256** (o BLAKE2b de Bouncy Castle) | Huella del archivo llave. |
| Memoria sensible | **FFM API** (`MemorySegment`, `Arena`) | Las claves viven fuera del heap y se borran al cerrar `Arena`, en vez de depender del GC. |
| UI | **JavaFX 25** + patrón **MVVM** | Escritorio moderno; se puede añadir AtlantaFX para un tema visual pulido. |
| CLI (opcional) | **picocli** | Facilita pruebas y automatización, y suma en el portafolio. |
| Build | **Gradle 9 (Kotlin DSL)** multi-módulo + **JPMS** (`module-info.java`) | Módulos con dependencias explícitas y encapsulamiento fuerte. |
| Empaquetado | **jpackage** (+ jlink) | Instaladores `.msi`, `.deb` y `.dmg` con el runtime incluido, 100% offline. |
| Pruebas | JUnit 5, **jqwik** (property-based), **ArchUnit**, **Jazzer** (fuzzing), **JMH** | Calidad verificable. |
| CI/CD | GitHub Actions: build en 3 sistemas operativos, SBOM CycloneDX, firma de artefactos | Señal profesional en el portafolio. |

---

## 2. Arquitectura en capas (hexagonal / clean)

```
┌──────────────────────────────────────────────────────────┐
│  cryptokipu-ui (JavaFX, MVVM)     cryptokipu-cli (picocli)│  ← Adaptadores de entrada
└───────────────┬──────────────────────────┬───────────────┘
                │                          │
┌───────────────▼──────────────────────────▼───────────────┐
│  cryptokipu-app  (Casos de uso)                          │
│  CreateVault · UnlockVault · AddFiles · ExtractFile      │
│  RecoverWithToken · RotateCredentials · RegenerateTokens │
│  SelfDestruct · VerifyIntegrity                          │
└───────────────┬──────────────────────────────────────────┘
                │ puertos (interfaces)
┌───────────────▼──────────────────────────────────────────┐
│  cryptokipu-core  (Dominio + criptografía, sin UI ni I/O)│
│  Vault · KeySlot · AuthPolicy · Credentials · Manifest   │
│  crypto/: Kdf, Aead, ChunkCipher, KeyWrap, SecureBytes   │
│  format/: HeaderCodec, ManifestCodec, ContainerLayout    │
└───────────────┬──────────────────────────────────────────┘
                │
┌───────────────▼──────────────────────────────────────────┐
│  cryptokipu-infra  (Adaptadores de salida)               │
│  FileContainerStore · TempWorkspace · KeyfileReader      │
│  ClipboardGuard · SystemRandomSource                     │
└──────────────────────────────────────────────────────────┘
```

**Regla de oro (verificada con ArchUnit):** `core` no depende de nada externo salvo Bouncy Castle. La UI nunca toca criptografía directamente, solo llama a casos de uso.

---

## 3. Estructura del repositorio

```
cryptokipu/
├── build.gradle.kts / settings.gradle.kts
├── docs/
│   ├── THREAT_MODEL.md        ← modelo de amenazas (STRIDE)
│   ├── FORMAT_SPEC.md         ← especificación del formato .kipu
│   └── adr/                   ← decisiones de arquitectura (ADR-001...)
├── cryptokipu-core/
│   └── src/main/java/dev/erick/cryptokipu/core/
│       ├── crypto/   (Argon2idKdf, HkdfDeriver, AesGcmAead, ChunkCipher, SecureBytes)
│       ├── auth/     (AuthPolicy, Factor, CredentialNormalizer, KeyfileDigest, RecoveryToken)
│       ├── vault/    (Vault, KeySlot, SlotType, MasterKey)
│       └── format/   (Header, Manifest, ContainerLayout, codecs)
├── cryptokipu-app/       (usecases/)
├── cryptokipu-infra/     (storage/, fs/, random/)
├── cryptokipu-ui/        (views FXML, viewmodels/, theme/)
├── cryptokipu-cli/
└── .github/workflows/    (ci.yml, release.yml)
```

---

## 4. Modelo de autenticación

### 4.1 Factores

| # | Factor | Obligatorio | Normalización |
|---|---|---|---|
| F1 | PIN numérico (6–10 dígitos) | Sí | Solo dígitos |
| F2 | Contraseña alfanumérica (≥ 12) | Sí | Unicode NFC, sin recortar |
| F3 | Elegible: **pregunta de seguridad**, **frase de paso** o **patrón 3×3** | Sí (uno) | Pregunta: minúsculas, sin tildes ni espacios extra. Patrón: secuencia de nodos |
| F4 | Llave de seguridad (cualquier archivo) | Configurable (recomendado) | SHA-256 de los bytes exactos |

### 4.2 Cómo se combinan

Todos los factores entran **juntos** en una sola derivación. **Nunca se validan por separado**, así el atacante no puede romperlos uno por uno.

```
input   = len‖F1 ‖ len‖F2 ‖ tipoF3‖len‖F3            (codificación con prefijo de longitud)
secret  = SHA-256(keyfile)                             (F4; el parámetro "secret" de Argon2)
KEK     = Argon2id(input, salt=32B, secret, m=512MiB, t=3, p=4) → 32B
```

Cualquier error devuelve un único mensaje: **"Credenciales incorrectas."**

### 4.3 Clave maestra y *key slots* (estilo LUKS / Cryptomator)

```
MasterKey (MK) = 32 bytes aleatorios (SecureRandom), generada al crear la bóveda
                 │
     ┌───────────┼──────────────┬───────────────┐
  Slot 0       Slot 1 .. Slot 10 (tokens de recuperación)
  KEK(F1..F4)  KEK_i = Argon2id(token_i ‖ F1)
  wrap(MK)     wrap(MK)
```

- Cada slot guarda la MK cifrada con AES-256-GCM, con la parte fija del encabezado como AAD.
- Cambiar credenciales solo reescribe el slot 0, **sin recifrar los datos**.

### 4.4 Tokens de uso único

- **10 tokens** con formato `CKP-7H3QK-M9X2D-R4TVA-ZP8NE-5WJ1C`: Base32 Crockford, ~125 bits de entropía, imposibles de adivinar.
- **Por defecto, token + PIN.** Si alguien encuentra la hoja impresa con los tokens, igual necesita el PIN.
- **Al usar un token:** la app abre la bóveda, **borra ese slot** y obliga a definir credenciales nuevas.
- Se muestran **una sola vez**, con opción de exportarlos a TXT o PDF, o imprimirlos. La app nunca los guarda.

> ⚠️ **Limitación honesta (va en el THREAT_MODEL):** en un archivo offline, el "uso único" solo se cumple en la copia que se actualiza. Si alguien tiene una copia vieja del `.kipu`, ese token sigue sirviendo en esa copia. Ningún software puede impedirlo sin un servidor o hardware seguro.

### 4.5 Llave de seguridad (keyfile)

- Puede ser una foto, un PDF o una canción, o una llave aleatoria de 64 bytes que genera la app (`.kipukey`).
- ⚠️ **Riesgo crítico:** WhatsApp, Facebook y a veces Drive o Fotos **recomprimen las imágenes**. Si cambia un solo byte, la llave deja de funcionar. La UI debe advertirlo y recomendar guardarla en un USB aparte o en Drive como archivo, no como "foto".
- La app nunca guarda la llave ni su ruta dentro del `.kipu`.

---

## 5. Formato del contenedor `.kipu`

Un **único archivo**, fácil de mover por USB, disco duro o Drive.

```
┌────────────────────────────────────────────┐
│ HEADER (texto plano, autenticado)          │
│  magic "CKIPU" · formatVersion · policy    │
│  argon2 params · salts · failCounter       │
│  keySlots[] (MK envuelta + nonce + tag)    │
│  headerMAC = HMAC(K_hdr, header)           │
├────────────────────────────────────────────┤
│ BLOB AREA (append-only)                    │
│  file#1: chunk0 | chunk1 | ... | chunkN    │
│  file#2: ...                               │
├────────────────────────────────────────────┤
│ MANIFEST cifrado (índice)                  │
│  rutas, tamaños, fechas, offsets, fileId   │
├────────────────────────────────────────────┤
│ FOOTER: offset del manifest · magic        │
└────────────────────────────────────────────┘
```

### 5.1 Jerarquía de claves (HKDF desde la MK)

```
K_hdr      = HKDF(MK, info="ckp/v1/header-mac")
K_manifest = HKDF(MK, info="ckp/v1/manifest")
K_file     = HKDF(MK, salt=fileId(16B aleatorio), info="ckp/v1/file")
```

### 5.2 Cifrado por bloques (para videos grandes)

- Bloques de **1 MiB** con AES-256-GCM: la memoria se mantiene constante y se evita el `OutOfMemoryError`.
- `nonce = 0x00000000 ‖ chunkIndex(8B)`. Es seguro porque cada archivo tiene su propia `K_file`, y cada reescritura genera un `fileId` nuevo.
- `AAD = fileId ‖ chunkIndex ‖ isLastChunk`. Así se detecta si alguien reordena, elimina o trunca bloques.
- Cada bloque se verifica **antes** de entregar sus bytes.

### 5.3 Escritura segura (por si se desconecta el USB a mitad)

1. Se agregan los blobs nuevos al final.
2. Se escribe el manifest nuevo.
3. Se escribe el footer nuevo con `fsync`.

Si algo falla antes del paso 3, el footer anterior sigue apuntando al manifest anterior y la bóveda no se corrompe. Para quitar el espacio de archivos eliminados existe un comando **Compactar**, que reescribe la bóveda en un archivo temporal y luego hace un *atomic move*.

---

## 6. Flujos principales

**Crear bóveda**
```
Elegir archivos/carpeta → definir F1, F2, F3 (tipo + valor), F4 (opcional)
→ medidor de fortaleza → generar MK → derivar KEK (Argon2id)
→ crear Slot 0 + 10 slots de tokens → cifrar archivos en bloques
→ mostrar tokens UNA vez → guardar .kipu
```

**Abrir bóveda**
```
Seleccionar .kipu → leer header → pedir F1..F4
→ Argon2id → intentar abrir Slot 0
   ├─ OK  → verificar headerMAC → reiniciar failCounter → descifrar manifest → explorador
   └─ ERR → failCounter++ → si llega al límite → autodestrucción
```

**Recuperar con token**
```
Token + PIN → probar slots de tokens → abrir → eliminar slot usado
→ obligar a definir credenciales nuevas → (opcional) regenerar tokens
```

**Autodestrucción (configurable, desactivada por defecto)**
```
failCounter ≥ N → sobrescribir TODOS los keySlots con bytes aleatorios + fsync
→ la MK es irrecuperable → los datos quedan como ruido (borrado criptográfico)
```
> Documentar: protege contra intentos dentro de la app, no contra copias ni ataques offline. El contador vive en el header en texto plano. Esa es su limitación, y la defensa real es el costo de Argon2id.

---

## 7. Gestión de datos sensibles en memoria

- Credenciales en `char[]` / `byte[]`, borradas con `Arrays.fill` apenas se usan. Nunca en `String`.
- MK y subclaves en `MemorySegment` fuera del heap (FFM), borradas al bloquear la bóveda.
- **Bloqueo automático** por inactividad (5 min por defecto) y al cerrar o minimizar (configurable).
- **Portapapeles:** si se copia un token, se limpia a los 30 s.
- **Vista previa en RAM** para imágenes, texto y PDF. Para abrir con una app externa se usa una carpeta temporal privada que se elimina al cerrar, con aviso de que en SSD el borrado no es garantizado.
- Sin logs con datos sensibles y sin telemetría. La app no abre conexiones de red, y una prueba de CI verifica que `java.net` no se use en `core` ni en `app`.

---

## 8. Interfaz (JavaFX + MVVM)

```
Pantallas:
1. Inicio            → Crear bóveda / Abrir bóveda / Recientes
2. Asistente crear   → Archivos → Factores → Llave → Tokens → Resumen
3. Desbloqueo        → F1..F4 (o "Usar token")
4. Explorador        → árbol de archivos, vista previa, agregar/extraer/eliminar
5. Seguridad         → cambiar credenciales, regenerar tokens, autodestrucción, compactar
6. Ajustes           → tema claro/oscuro, idioma, auto-bloqueo, parámetros Argon2 (perfil)
```

- Las tareas pesadas (Argon2, cifrado) corren en hilos virtuales con barra de progreso y opción de cancelar.
- Perfiles de Argon2: **Estándar** (256 MiB), **Alto** (512 MiB) y **Paranoico** (1 GiB), calibrados para tardar alrededor de 1 s en tu equipo.

---

## 9. Estrategia de pruebas

| Tipo | Qué valida |
|---|---|
| Vectores oficiales | Argon2id (RFC 9106), AES-GCM (NIST), HKDF (RFC 5869) |
| Property-based (jqwik) | `decrypt(encrypt(x)) == x` para tamaños aleatorios (0 B … 50 MB) |
| Manipulación | Cambiar 1 bit en header, bloque o manifest → debe fallar con error de integridad |
| Truncado / reordenado | Quitar el último bloque o intercambiar dos → debe detectarse |
| Fuzzing (Jazzer) | El parser del header y del manifest no debe caerse ante entradas basura |
| ArchUnit | `core` no importa JavaFX, `java.net` ni `infra` |
| JMH | Rendimiento de cifrado (MB/s) y tiempo de Argon2 por perfil |
| Escritura interrumpida | Simular un corte entre los pasos 1–3 → la bóveda sigue abriendo |

---

## 10. Roadmap

| Fase | Entregable |
|---|---|
| **0. Fundaciones** | Repo multi-módulo, CI, `THREAT_MODEL.md`, `FORMAT_SPEC.md` v1 |
| **1. Núcleo cripto** | Argon2id, HKDF, AEAD, ChunkCipher + vectores de prueba |
| **2. Formato `.kipu`** | Header, slots, manifest, escritura segura, codecs + fuzzing |
| **3. Autenticación** | Factores F1–F4, normalización, tokens, recuperación, rotación |
| **4. CLI** | `kipu create / open / add / extract / recover`: valida todo sin UI |
| **5. UI JavaFX** | Asistente, desbloqueo, explorador, vista previa en RAM |
| **6. Endurecimiento** | FFM para claves, auto-bloqueo, portapapeles, autodestrucción |
| **7. Release** | jpackage (Win/Linux/macOS), SBOM, firmas, README con capturas y demo GIF |
| **Futuro** | Unidad virtual (WinFsp/FUSE), cascada XChaCha20 (modo paranoico), Reed-Solomon contra corrupción |

---

## 11. Decisiones clave para documentar como ADR

1. **ADR-001:** Java 25 LTS + JavaFX en lugar de Electron, Go o Rust.
2. **ADR-002:** AES-256-GCM por bloques de 1 MiB en lugar de GCM de un solo bloque.
3. **ADR-003:** Argon2id con todos los factores combinados en lugar de validar cada factor por separado.
4. **ADR-004:** Clave maestra con *key slots* (permite tokens y rotación sin recifrar).
5. **ADR-005:** Contenedor de un solo archivo en lugar de carpeta cifrada por archivo. Ventaja: portabilidad. Costo: Drive vuelve a subir el archivo completo con cada cambio.
6. **ADR-006:** Autodestrucción como disuasión, con sus limitaciones documentadas.

---

Esta arquitectura cubre los cuatro autenticadores, los tokens de uso único y el traslado por USB, disco o Drive sin exponer datos. Lo que más pesa en el portafolio son los documentos `THREAT_MODEL.md`, `FORMAT_SPEC.md` y los ADR: muestran que entiendes por qué tomaste cada decisión, no solo que el código funciona.

Puedo seguir con la **fase 0–1**: el `settings.gradle.kts` multi-módulo y las clases `Argon2idKdf`, `ChunkCipher` y `KeySlot` con sus pruebas.