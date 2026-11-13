# CryptoKipu: plan de implementación por fases

> **Fuentes:** `Aequitectura.md` (arquitectura oficial) + requisitos del usuario (13-11-2026) + `Informe_Ingenieria_Inversa_crud-persona-spring.md` (referencia de rendimiento y estructura de escritorio) + revisión 1 del plan y secciones reescritas por el usuario (integradas en este documento).
> **Regla de precedencia:** cuando un requisito del usuario contradice la arquitectura, **manda el requisito del usuario**.

---

## 1. Reglas de trabajo (obligatorias)

| # | Regla | Cómo se aplica |
|---|---|---|
| R1 | **El agente NO hace `commit` ni `push`.** | Todo `git commit`, `git push` y operaciones sobre el remoto las hace solo el usuario. El agente deja los cambios en el árbol de trabajo y avisa qué archivos tocó. |
| R2 | **Toda variable termina en `Mija`.** | Variables locales, campos, parámetros, parámetros de lambda, variables de `catch`, variables de patrón y componentes de `record`. Constantes (`static final`) terminan en `_MIJA`. Ver 1.1. |
| R3 | 100% Java, escritorio, 100% offline. | Sin dependencias que requieran red. ArchUnit prohíbe `java.net` en `core` y `app`. |
| R4 | No se inventa criptografía. | Solo JCA (JDK) y Bouncy Castle. Nada de algoritmos propios. |
| R5 | La regla `Mija` aplica también a **tests**. | Checkstyle corre sobre `main` y `test`. Se permite la variable sin nombre `_` (Java 22+). |
| R6 | Ningún dato sensible en logs, excepciones ni `toString()`. | Logging con `System.Logger`. Los `record` con secretos sobrescriben `toString()` con `"***"`. |
| R7 | Todo valor que afecte cómo se derivan las claves queda **versionado en el header**. | Normalización, algoritmos y parámetros. Cambiarlos después exige subir de versión. |
| R8 | Nuevas reglas se agregan a esta tabla a medida que se avanza. | — |

### 1.1 Convención de nombres `Mija`

```java
// Campos y constantes
private final char[] contrasenaNumericaMija;
private static final int TAMANO_BLOQUE_BYTES_MIJA = 1 << 20;      // 1 MiB

// Parámetros y locales
public Bloque cifrar(byte[] datosMija, long indiceBloqueMija) {
    byte[] nonceMija = construirNonce(indiceBloqueMija);
    ...
}

// Lambdas, catch, bucles, patrones, records, variable sin nombre
archivosMija.forEach(archivoMija -> agregar(archivoMija));
for (int indiceMija = 0; indiceMija < totalMija; indiceMija++) { ... }
catch (IOException errorMija) { ... }
catch (NumberFormatException _) { ... }
if (factorMija instanceof FraseDePaso fraseMija) { ... }
public record Credenciales(char[] pinMija, char[] contrasenaMija, char[] fraseMija) {}
```

- **No aplica a:** nombres de clases, interfaces, métodos, paquetes, módulos ni constantes de `enum` (no son variables), ni a los scripts de build en Kotlin (`*.gradle.kts`, `buildSrc`), que no son código de la aplicación.
- **Checkstyle** (Fase 0), en `main` y `test`: `LocalVariableName`, `LocalFinalVariableName`, `MemberName`, `ParameterName`, `LambdaParameterName`, `CatchParameterName`, `PatternVariableName`, `RecordComponentName` con el formato
  ```
  ^([a-z][a-zA-Z0-9]*Mija|_)$
  ```
  y `ConstantName` (constantes `static final`) con `^[A-Z][A-Z0-9_]*_MIJA$`. Los campos `static` **no** finales son variables: `StaticVariableName` con `^[a-z][a-zA-Z0-9]*Mija$`. Si una variable no cumple, el build falla.
- Los IDs `fx:id` de FXML deben coincidir con el campo `@FXML`, por lo que también llevan `Mija` (`fx:id="campoPinMija"`).
- Efecto visible: los componentes de `record` generan accesores con el sufijo (`credencialesMija.pinMija()`). Es válido y se acepta.

---

## 2. Ajustes y decisiones

| Tema | Decisión final |
|---|---|
| F1 contraseña numérica | Solo `0-9`, **12–25** dígitos. |
| F2 contraseña alfanumérica | 12–25, ≥1 mayúscula, ≥1 minúscula, ≥1 dígito. **Solo ASCII imprimible (0x20–0x7E)**, para poder escribirla en cualquier teclado. Sin normalización. |
| F3 frase de paso | 15–25 caracteres. **Normalización v1:** NFD → quitar diacríticos (á→a, ñ→n) → minúsculas (`Locale.ROOT`) → recortar extremos → colapsar espacios múltiples. Queda guardada como `normalizacionF3 = 1` en la política. |
| F4 llave de seguridad | Cualquier archivo o `.kipukey` de 64 B. SHA-256 calculado **en streaming** con barra de progreso. |
| Tokens | **6 tokens**, exportables a TXT. Slot = `Argon2id(token_i ‖ F1)`. **Si cambia F1, se regeneran los 6 automáticamente.** |
| Apertura en otro equipo | Visor portátil, solo en memoria (Fase 7). |
| Contenido | Cualquier archivo. La vista previa depende del formato (tabla 7.2). |
| Tamaño máximo | En FAT32: bóveda ≤ 4 GiB − 1 B, con detección y aviso. **Volúmenes divididos en v1.1.** |
| Nombre | **CryptoKipu** en todo: paquete `dev.erick.cryptokipu`, magic `CKIPU`, extensión `.kipu`. La carpeta `CriptoKhipu` la renombra el usuario (ver 2.2). |
| Licencia | **Apache-2.0** (ya está en el repositorio), compatible con POI, PDFBox, Bouncy Castle, AtlantaFX e Ikonli. |

**Pendientes resueltos:** mínimo 12 (constante en `AuthPolicy`), token + F1 se mantiene, y el nombre queda como arriba.

### 2.1 Codificación de la entrada a Argon2 (política v1)

- Cada factor va en UTF-8 con prefijo de longitud de 4 B: `len‖F1 ‖ len‖F2 ‖ len‖F3`.
- Token: Base32 Crockford, sin distinguir mayúsculas; guiones y espacios se ignoran al tipearlo.

### 2.2 Renombrar la carpeta

El renombrado de `D:\proyect\CriptoKhipu` → `CryptoKipu` lo hace el usuario fuera de una sesión del agente (la sesión trabaja dentro de esa carpeta). Hacerlo **antes** de la Fase 0 evita rutas viejas en la configuración de Gradle y del IDE (`.idea/`).

---

## 3. Qué se toma del proyecto de referencia (`crud-persona-spring`) y qué no

El proyecto de referencia es rápido porque es **un solo proceso JVM local**, sin servidor, sin red y con la UI cacheada. Se replica eso, corrigiendo sus puntos débiles.

| Se adopta | Detalle en CryptoKipu |
|---|---|
| `Launcher` separado de `Application` | `Launcher.main()` → `Application.launch(CryptoKipuApp.class)`; evita el error de módulos JavaFX al ejecutar el JAR. |
| Shell único con navegación por menú | `ShellController` + `ModalPane` de AtlantaFX, tema claro/oscuro. |
| Caché de vistas + interfaz `Refrescable` | Cada pantalla se carga una vez y se reutiliza; `refrescar()` al mostrarla. |
| `controllerFactory` para inyección | `loader.setControllerFactory(contenedorMija::obtener)`. |
| Utilidades de UI reutilizables | `Notificador` (toasts y diálogos), `Task` en segundo plano estilo `Exportador`, `Formularios` (validación inline). |
| AtlantaFX + Ikonli | Mismo tema visual pulido. |
| Pruebas de integración con datos temporales | Bóvedas de prueba en `@TempDir`, nunca en archivos reales. |

| Se corrige / no se adopta | Motivo |
|---|---|
| **Spring Boot** | Se reemplaza por un **contenedor de composición manual** (clase `Contenedor`). Arranque más rápido, imagen `jlink` más pequeña y nada de reflexión. |
| Trabajo pesado en el hilo de JavaFX (hallazgo H-07) | **Todo** Argon2id, cifrado y lectura de bóveda corre en hilos virtuales con `Task`, barra de progreso y cancelación. |
| SQLite / JPA | No se necesita base de datos: los datos viven en el `.kipu`. Las preferencias van a un `.properties`. |
| Datos versionados en Git (H-02) | `.gitignore` excluye `*.kipu`, `*.kipukey`, `tokens*.txt` y carpetas temporales. |
| Sin autenticación (H-01) | Es el núcleo del proyecto. |

---

## 4. Vista general de fases

```
F0 Fundaciones ─► F1 Núcleo cripto ─► F2 Formato .kipu ─► F3 Autenticación ─► F4 Casos de uso + infra
                                                                                     │
                                         ┌───────────────────────────────────────────┤
                                         ▼                                           ▼
                                     F5 CLI                                    F6 UI JavaFX
                                         └───────────────┬───────────────────────────┘
                                                         ▼
                                     F7 Visor portátil y transporte ─► F8 Endurecimiento ─► F9 Calidad ─► F10 Release
```

| Fase | Nombre | Resultado verificable |
|---|---|---|
| 0 | Fundaciones | `gradlew build` verde con módulos vacíos, Checkstyle `Mija` y ArchUnit activos, dependencias verificadas |
| 1 | Núcleo criptográfico | Argon2id, HKDF, AES-GCM y cifrado por bloques pasan vectores oficiales |
| 2 | Formato `.kipu` | Crear/leer/compactar; manipulación detectada; corte, disco lleno y FAT32 no corrompen; recupera desde el header de respaldo |
| 3 | Autenticación | 3 contraseñas + llave + 6 tokens; rotación sin recifrar; cambiar F1 regenera tokens |
| 4 | Casos de uso + infraestructura | Todos los casos de uso probados de punta a punta sin UI |
| 5 | CLI | `kipu create/open/list/extract/recover` operativos |
| 6 | UI JavaFX | Asistente de creación, desbloqueo y explorador funcionando |
| 7 | Visor portátil y transporte | Abrir el `.kipu` desde un USB en otro equipo sin dejar archivos en él |
| 8 | Endurecimiento | Claves fuera del heap, auto-bloqueo, portapapeles, autodestrucción opcional |
| 9 | Calidad y rendimiento | Fuzzing, property-based, JMH, pruebas de corte y de hardware real |
| 10 | Release | Instaladores + versión portátil para Windows/Linux/macOS |

---

## 5. Fases en detalle

### Fase 0 — Fundaciones

**Objetivo:** esqueleto del repositorio que ya obliga a cumplir las reglas.

1. `settings.gradle.kts` multi-módulo: `cryptokipu-core`, `cryptokipu-app`, `cryptokipu-infra`, `cryptokipu-ui`, `cryptokipu-cli`.
2. Toolchain **Java 25** (Temurin, aprovisionado por Gradle con `foojay-resolver`), Gradle 9 con Kotlin DSL, catálogo de versiones `gradle/libs.versions.toml` (Bouncy Castle, JavaFX 25, AtlantaFX, Ikonli, picocli, PDFBox 3, POI, JUnit 6 (sucesor de JUnit 5, misma API Jupiter), jqwik, ArchUnit). Convenciones comunes en `buildSrc` (`cryptokipu.java-conventions`).
3. `module-info.java` en cada módulo (JPMS).
4. **Checkstyle** con las reglas `Mija` de la sección 1.1, en `main` y `test`, ejecutado en `check`.
5. **ArchUnit**: `core` no importa JavaFX, `java.net` ni `infra`; `ui` no importa `core.crypto`; el servidor de medios no puede estar en `core` ni `app`.
6. `.gitignore` (incluye `*.kipu`, `*.kipukey`, `tokens*.txt`, `*.kipu.tmp`, `.idea/`, `build/`).
7. Documentos: `docs/THREAT_MODEL.md` (STRIDE, con las limitaciones de 7.5), `docs/FORMAT_SPEC.md` v1 (borrador), `docs/adr/` con ADR-001..006 de la arquitectura más:
   - **ADR-007:** límite FAT32: detección y bloqueo en v1, volúmenes divididos en v1.1.
   - **ADR-008:** header primario + respaldo en zona fija al inicio; `failCounter` fuera de la zona autenticada.
   - **ADR-009:** política v1 (F2 solo ASCII, normalización de F3, token + F1 y regeneración de tokens al cambiar F1).
8. Workflow de CI (`.github/workflows/ci.yml`) en Windows/Linux/macOS. *(El push que lo dispara lo hace el usuario.)*
9. **Cadena de suministro:** `gradle/verification-metadata.xml` (verificación SHA-256 de dependencias), *dependency locking* y `distributionSha256Sum` en el wrapper.
10. **`LICENSE`** (Apache-2.0, ya existe) y `NOTICE` con las dependencias de terceros:

    | Dependencia | Licencia | ¿Compatible al distribuir? |
    |---|---|---|
    | Bouncy Castle | MIT (Bouncy Castle License) | Sí |
    | Apache POI, Apache PDFBox | Apache-2.0 | Sí |
    | AtlantaFX | MIT | Sí |
    | Ikonli, picocli | Apache-2.0 | Sí |
    | OpenJFX (JavaFX) | GPLv2 + Classpath Exception | Sí, por la excepción de classpath (se incluye su aviso) |
    | JUnit 5, jqwik | EPL-2.0 | Solo pruebas, no se distribuyen |

11. **Jerarquía de errores** en `core`:
    ```java
    public sealed interface ErrorBoveda permits
        CredencialesIncorrectas, IntegridadFallida, FormatoNoSoportado,
        ParametrosFueraDeRango, EspacioInsuficiente, LimiteSistemaArchivos,
        BovedaEnUso, BovedaDestruida, MemoriaInsuficiente, OperacionCancelada {}
    ```
    La UI traduce cada error a un mensaje. `CredencialesIncorrectas` **nunca** indica qué factor falló.
12. **Política de logging:** `System.Logger`, nivel `INFO` por defecto, prohibido registrar rutas de archivos internos, nombres de archivos de la bóveda o cualquier `byte[]`/`char[]`.
13. **Opciones de JVM centralizadas** en un solo lugar del build; las usan `run`, los tests de integración y `jpackage` (ver 7.4).

**Criterio de salida:** `./gradlew check` pasa; una variable sin `Mija` (en `main` o en `test`) hace fallar el build (probado a propósito y revertido); una dependencia con checksum alterado hace fallar el build.

---

### Fase 1 — Núcleo criptográfico (`cryptokipu-core/crypto`)

**Objetivo:** primitivas envueltas en clases pequeñas, probadas contra vectores oficiales.

| Clase | Responsabilidad | Librería |
|---|---|---|
| `Argon2idKdf` | `derivar(entradaMija, salMija, secretoMija, parametrosMija)` → 32 B | Bouncy Castle `Argon2BytesGenerator` |
| `ParametrosArgon2` (record) | Perfiles Estándar 256 MiB / Alto 512 MiB / Paranoico 1 GiB, `t=2`, `p=4`. Recalibrado con JMH: BC es secuencial (p=4 no acelera); ≈ 0,9 / 2 / 4 s por derivación | — |
| `HkdfDeriver` | Subclaves `K_hdr`, `K_manifest`, `K_file` | `javax.crypto.KDF` (`HKDF-SHA256`, final en JDK 25, JEP 510) |
| `AesGcmAead` | Cifrar/descifrar con AAD, nonce de 12 B, tag 128 bits | JCA `AES/GCM/NoPadding` |
| `ChunkCipher` | Cifrado por bloques de 1 MiB, `nonce = 0⁴ ‖ índice(8B)`, `AAD = fileId ‖ índice ‖ esÚltimo` | `AesGcmAead` |
| `KeyWrap` | Envolver/desenvolver la MK en un slot con **nonce aleatorio de 12 B** en cada envoltura | `AesGcmAead` |
| `ProveedorNonce` | Única fuente de nonces: aleatorio de 12 B (slots, manifest) o `0⁴ ‖ índice` (bloques) | `SecureRandom` |
| `SecureBytes` | Bytes sensibles con `close()` que los borra (`Arrays.fill`); `toString()` = `"***"` | — (en Fase 8 pasa a FFM) |
| `FuenteAleatoria` (puerto) | Abstracción de `SecureRandom` para poder probar | — |

Pruebas:
- Vectores **RFC 9106** (Argon2id), **NIST** (AES-GCM), **RFC 5869** (HKDF).
- `ChunkCipher`: 0 B, 1 B, exactamente 1 MiB, 1 MiB + 1 B, 50 MB; reordenar / truncar / quitar el último bloque → error.
- `KeyWrap`: envolver dos veces la misma MK con la misma KEK produce nonces distintos.

**Criterio de salida:** 100% de los vectores pasan; ninguna clave se guarda en `String`.

---

### Fase 2 — Formato del contenedor `.kipu` (`cryptokipu-core/format` + `infra/storage`)

**Objetivo:** un único archivo portable, autenticado, resistente a cortes y a archivos manipulados.

#### 2.1 Estructura

```
┌──────────────────────────────────────────────────────┐ offset 0
│ HEADER PRIMARIO  (16 KiB fijos, se reescribe en sitio)│
│  Zona A (inmutable, autenticada)                     │
│    magic "CKIPU" · formatVersion · algoritmos        │
│    (AEAD=1 AES256GCM, KDF=1 ARGON2ID, HASH=1 SHA256) │
│    política (factores, normalizacionF3=1)            │
│    parámetros Argon2 · sales · vaultId (16 B)        │
│  Zona B (slots)                                      │
│    slot[0..6]: tipo · sal · nonce · MK cifrada · tag │
│  headerMAC = HMAC-SHA256(K_hdr, Zona A ‖ Zona B)     │
│  Zona C (mutable, NO autenticada)                    │
│    failCounter · relleno aleatorio hasta 16 KiB      │
├──────────────────────────────────────────────────────┤ offset 16 KiB
│ HEADER RESPALDO  (copia idéntica, 16 KiB fijos)      │
├──────────────────────────────────────────────────────┤ offset 32 KiB
│ BLOBS (append-only, bloques de 1 MiB)                │
├──────────────────────────────────────────────────────┤
│ MANIFEST cifrado (K_manifest, nonce aleatorio)       │
├──────────────────────────────────────────────────────┤
│ FOOTER: offset manifest · generación · magic · CRC32C│
└──────────────────────────────────────────────────────┘
```

**Reglas del header:**
- Los slots **solo existen** en las dos zonas fijas. El footer **nunca** contiene slots. Así, rotar o autodestruir sobrescribe todas las copias dentro del archivo (no quedan slots viejos enterrados en la zona *append-only*).
- Al abrir: se lee el primario; si su MAC o su estructura fallan, se usa el respaldo y se repara el primario.
- `failCounter` está en la Zona C, fuera del MAC (se incrementa sin tener la MK). Se documenta como **no confiable** (un atacante puede reiniciarlo).
- Al rotar: se escribe el respaldo, `fsync`, luego el primario, `fsync`. Un corte deja siempre al menos una copia válida.
- *(Implementación)* Cada copia lleva un **CRC32C** de Zona A ‖ Zona B ‖ MAC para detectar daño accidental sin la clave, y una **generación de header** en la Zona B: tras un corte entre respaldo y primario gana la copia más nueva. La Zona C guarda además una **pista del último footer**. Detalle en `docs/FORMAT_SPEC.md`.
- **Agilidad criptográfica:** los IDs de algoritmo permiten agregar otros sin romper bóvedas viejas. `FORMAT_SPEC.md` define la migración v1 → v2: abrir con v1, reenvolver la MK con la política nueva y reescribir ambos headers; los bloques solo se recifran si cambia el AEAD.

#### 2.2 Límites del parser (anti-DoS y anti-debilitamiento)

| Campo | Mínimo | Máximo |
|---|---|---|
| Argon2 `m` | 64 MiB | 2 GiB |
| Argon2 `t` | 2 | 10 |
| Argon2 `p` | 1 | 8 |
| Slots | 1 | 7 |
| Manifest | — | 64 MiB |
| Entradas en manifest | — | 1 000 000 |
| Tamaño de bloque | 1 MiB (fijo en v1) | 1 MiB |

Fuera de rango → `ParametrosFueraDeRango`, sin intentar derivar. Algoritmo o versión desconocidos → `FormatoNoSoportado`.

#### 2.3 Claves y nonces

| Elemento | Clave | Nonce |
|---|---|---|
| Slots | KEK del slot | **Aleatorio 12 B** en cada escritura |
| Manifest | `K_manifest = HKDF(MK, "ckp/v1/manifest")` | **Aleatorio 12 B** en cada escritura; `AAD = vaultId ‖ generación` |
| Bloques | `K_file = HKDF(MK, salt=fileId, "ckp/v1/file")` | `0⁴ ‖ índice(8 B)`; `fileId` nuevo cada vez que se escribe un archivo |
| Header MAC | `K_hdr = HKDF(MK, "ckp/v1/header-mac")` | — |

#### 2.4 Escritura segura
1. Verificar **espacio libre** (agregar: tamaño + 5%; compactar: 2× el tamaño de la bóveda) y el **límite del sistema de archivos** (`Files.getFileStore(rutaMija).type()`: si es FAT32/vfat/msdos, el máximo es 4 GiB − 1 B). Si no alcanza → `EspacioInsuficiente` o `LimiteSistemaArchivos` **antes** de escribir.
2. Agregar los blobs → escribir el manifest nuevo → escribir el footer nuevo (`generación + 1`) → `fsync`.
3. Un corte antes del footer deja el footer anterior válido. Como en ese caso el final del archivo queda con basura, al abrir **se busca hacia atrás el último footer válido** (magic + CRC32C) y la siguiente escritura trunca la basura.
4. **Bloqueo:** `FileChannel.tryLock()` al abrir; si falla → `BovedaEnUso`. Si el medio es de solo lectura, se abre en modo solo lectura. En carpetas sincronizadas (Drive) el bloqueo puede no funcionar: se documenta no abrir la misma bóveda en dos equipos a la vez.

#### 2.5 Eliminar y compactar
- "Eliminar" quita el archivo del manifest, pero sus bloques siguen cifrados en el blob. La UI lo indica y ofrece **"Eliminar y compactar"**.
- Compactar reescribe en `boveda.kipu.tmp` (mismo directorio) y hace *atomic move*.

#### 2.6 Pruebas
- 1 bit cambiado en la Zona A, la Zona B, un blob o el manifest → `IntegridadFallida` (o fallo de desbloqueo si es la Zona A).
- 1 bit cambiado en la Zona C → la bóveda abre igual.
- Header primario destruido → abre con el respaldo y repara el primario.
- Corte simulado en cada paso de la escritura → la bóveda abre con el último estado válido.
- Header con `m = 64 GiB` → `ParametrosFueraDeRango` en menos de 10 ms.
- Destino FAT32 simulado y escritura que supera 4 GiB → error previo; la bóveda queda intacta.
- Disco lleno simulado (un `FileChannel` que falla a mitad) → `EspacioInsuficiente`; la bóveda queda intacta.
- Dos commits seguidos → nonces del manifest distintos.
- Dos aperturas simultáneas → la segunda falla con `BovedaEnUso`.
- Rotación → no queda ningún slot viejo en ninguna parte del archivo (búsqueda binaria de los bytes del slot anterior).
- Parser con entradas basura → nunca lanza excepciones no controladas ni reserva memoria desmedida (preparado para Jazzer en Fase 9).

**Criterio de salida:** crear, agregar, leer, compactar y recuperar desde el header de respaldo con una clave maestra fija de prueba.

---

### Fase 3 — Autenticación (`cryptokipu-core/auth` + `vault`)

#### 3.1 Factores y políticas de validación

| Factor | Regla | Normalización |
|---|---|---|
| F1 contraseña numérica | Solo `0-9`, 12–25 | Ninguna |
| F2 contraseña alfanumérica | 12–25, ≥1 mayúscula, ≥1 minúscula, ≥1 dígito, solo ASCII `0x20–0x7E` | Ninguna |
| F3 frase de paso | 15–25 caracteres (contados **después** de normalizar) | v1: NFD → sin diacríticos → minúsculas `Locale.ROOT` → recortar → colapsar espacios |
| F4 llave de seguridad | Archivo cualquiera, o `.kipukey` aleatorio de 64 B | SHA-256 de los bytes exactos, **en streaming** con barra de progreso y cancelación |

> La UI advierte: *"Las tildes y la ñ se ignoran: 'Mi Pérro Ñato' = 'mi perro nato'."* Así funciona en cualquier teclado.

Clases: `AuthPolicy` (constantes de longitud, p. ej. `LONGITUD_MINIMA_F1_MIJA = 12`), `ValidadorFactores` (devuelve la lista de reglas incumplidas para que la UI las muestre), `CredentialNormalizer`, `KeyfileDigest`, `MedidorFortaleza`.

#### 3.2 Derivación combinada (sin validar factores por separado)

```
entradaMija = len‖F1 ‖ len‖F2 ‖ len‖F3          (prefijo de longitud de 4 B, UTF-8)
secretoMija = SHA-256(llave)                      (F4)
kekMija     = Argon2id(entradaMija, sal₀ 32 B, secretoMija, perfil)
```
Cualquier fallo devuelve un único mensaje: **"Credenciales incorrectas."**

#### 3.3 Clave maestra y tokens

- `MasterKey` = 32 B de `SecureRandom`.
- **Slot 0** = MK envuelta con `KEK = Argon2id(len‖F1 ‖ len‖F2 ‖ len‖F3, sal₀, secreto = SHA-256(F4))`.
- **Slots 1–6** = MK envuelta con `KEK_i = HKDF(K_F1 ‖ token_i, info="ckp/v1/token-slot")`, donde `K_F1 = Argon2id(len‖F1, salTokens, perfil de la bóveda)` se deriva **una sola vez** (`salTokens` en la Zona A). Decisión del usuario, ADR-011.
- **Envoltura:** AES-256-GCM, **nonce aleatorio de 12 B en cada escritura**, `AAD = Zona A del header` (ver Fase 2). Si alguien cambia los parámetros, las sales o la política, el slot no abre.
- **Formato del token:** `CKP-XXXXX-XXXXX-XXXXX-XXXXX-XXXXX` (Base32 Crockford, ~125 bits). Se muestran una sola vez y se exportan a TXT con `ExportadorTokensTxt` (encabezado con nombre de la bóveda, fecha y aviso de guardarlo separado del `.kipu`). La app nunca los guarda.
- **Costo:** crear la bóveda o rotar F1 ejecuta **2 derivaciones Argon2id** (slot 0 + `K_F1`); recuperar con token, 1 derivación + 6 HKDF. Siempre con barra de progreso, en segundo plano.
- **Formato del token:** 25 símbolos Base32 Crockford + 1 de verificación (mod 37): `CKP-XXXXX-XXXXX-XXXXX-XXXXX-XXXXX-C`. Lectura tolerante (minúsculas, guiones, espacios, `O→0`, `I/L→1`); un token mal escrito da `TokenMalEscrito` antes de derivar.
- **F4 opcional:** la Zona B guarda `requiereLlave` para que la interfaz sepa si pedir la llave; se puede agregar o quitar al rotar.

**Reglas de rotación** (nunca recifran datos):

| Qué cambia | Qué se reescribe |
|---|---|
| F2, F3 o F4 | Solo slot 0 |
| **F1** | Slot 0 **+ los 6 slots de token regenerados**. Se muestran los tokens nuevos y se exige guardar el TXT antes de cerrar el diálogo. |
| Uso de un token | Se borra ese slot (bytes aleatorios) → se obliga a rotar. Si en esa rotación cambia F1, se regeneran todos; si no, quedan los restantes. |

**Pruebas:**
- Cada factor equivocado (uno por vez) → "Credenciales incorrectas".
- Rotar F1 → los tokens viejos fallan y los nuevos funcionan.
- Rotar F2 → los tokens siguen funcionando.
- Token usado → rechazado en la misma copia.
- Dos rotaciones seguidas → nonces distintos en todos los slots.
- Normalización de F3: `"Mi  Pérro Ñato "` y `"mi perro nato"` derivan la misma clave (property-based con jqwik).
- F2 con `ñ` o tilde → rechazado por el validador con mensaje claro.
- Llave de varios GB → hash en streaming sin cargarla entera en memoria.

#### 3.4 Llave de seguridad en distintos medios

| Medio | Cómo funciona |
|---|---|
| Imagen / documento / audio | El usuario elige el archivo; se usa su SHA-256. Advertencia en la UI: **no enviarlo por WhatsApp/Facebook ni subirlo como "foto"** (lo recomprimen y la llave deja de servir). |
| USB / disco externo | La llave es un archivo dentro del USB. El asistente ofrece **generar un `.kipukey`** directamente en la unidad elegida. |
| Teléfono | Conectado por cable (almacenamiento/MTP) o tarjeta SD: la llave es un archivo dentro de él. Sin Bluetooth ni red (regla offline). |
| Drive / nube | Se guarda **como archivo** (no como foto) y se descarga antes de abrir. |

> Vincular la llave al **número de serie físico** del USB/teléfono queda en "Futuro".

**Criterio de salida:** crear y abrir bóveda con F1–F4; recuperar con token + F1; rotar credenciales según la tabla.

---

### Fase 4 — Casos de uso e infraestructura (`cryptokipu-app`, `cryptokipu-infra`)

Casos de uso (un método público por clase, entradas como `record` de comando):

| Caso de uso | Entrada | Salida |
|---|---|---|
| `CrearBoveda` | archivos/carpetas, F1–F3, llave, perfil Argon2, destino | ruta `.kipu` + 6 tokens (una vez) |
| `DesbloquearBoveda` | ruta, F1–F4 | `SesionBoveda` (MK en memoria + manifest) |
| `AgregarArchivos` | sesión, archivos | manifest actualizado |
| `ExtraerArchivo` | sesión, fileId, destino | archivo descifrado (acción explícita del usuario) |
| `AbrirEnMemoria` | sesión, fileId | `InputStream` descifrado bloque a bloque (base del visor) |
| `EliminarArchivo` / `EliminarYCompactar` | sesión, fileId | manifest actualizado (y bóveda compactada) |
| `RecuperarConToken` | ruta, token, F1 | sesión + obligación de rotar |
| `RotarCredenciales` | sesión, nuevos F1–F4 | slot 0 reescrito; si cambió F1, además 6 tokens nuevos (una vez) |
| `RegenerarTokens` | sesión | 6 tokens nuevos |
| `VerificarIntegridad` | sesión | reporte por archivo |
| `Compactar` | sesión | `.kipu` reescrito |
| `Autodestruir` | (interno) | slots sobrescritos en ambos headers |

Infraestructura: `FileContainerStore`, `KeyfileReader`, `SystemRandomSource`, `TempWorkspace` (carpeta privada temporal para "abrir con app externa", borrada al cerrar), `PreferenciasStore` (`.properties`; nunca `java.util.prefs`, que escribe en el registro de Windows), `Contenedor` (composición manual).

*(Implementación)* Paquete `app.casos`, un método `ejecutar(Comando)` por clase. Se agregó `InspeccionarBoveda` (qué pedir antes del desbloqueo: llave, destruida, solo lectura). `SesionBoveda` rechaza todo tras `close()` (`IllegalStateException`) y, en rotación obligatoria, todo salvo rotar y cerrar (`RotacionPendiente`). Tras usar un token, la rotación siempre regenera los 6 tokens (ADR-012). Composición manual en `infra.configuracion.Contenedor`.

Concurrencia: los casos de uso son síncronos; la UI y la CLI los llaman desde hilos virtuales. Progreso mediante `ReporteProgreso` (porcentaje + cancelación → `OperacionCancelada`).

Memoria: antes de ejecutar Argon2 se compara la `m` del header con `Runtime.maxMemory()`; si no alcanza → `MemoriaInsuficiente` con la RAM que pide la bóveda, en lugar de un `OutOfMemoryError`.

**Criterio de salida:** pruebas de integración en `@TempDir` que crean, abren, agregan, extraen, recuperan y rotan sin UI.

---

### Fase 5 — CLI (`cryptokipu-cli`, picocli)

```
kipu create  --out boveda.kipu --keyfile foto.jpg  archivos...
kipu open    boveda.kipu  (lista el contenido)
kipu add     boveda.kipu  archivos...
kipu extract boveda.kipu  --id <fileId> --to carpeta
kipu recover boveda.kipu  (pide token + F1)
kipu verify  boveda.kipu
kipu compact boveda.kipu
```
- Las contraseñas se leen con `Console.readPassword()` (`char[]`), nunca como argumentos de línea de comandos.

**Criterio de salida:** guion de prueba que ejecuta el ciclo completo y compara hashes de los archivos originales y extraídos.

---

### Fase 6 — UI JavaFX (`cryptokipu-ui`, MVVM)

Estructura heredada de la referencia (sección 3): `Launcher` → `CryptoKipuApp` → `ShellController` con vistas cacheadas, `Notificador`, AtlantaFX, Ikonli.

| Pantalla | Contenido |
|---|---|
| 1. Inicio | Crear bóveda · Abrir bóveda · Recientes (solo rutas, nunca credenciales) |
| 2. Asistente crear | Archivos → Contraseña numérica → Contraseña alfanumérica → Frase → Llave de seguridad → Perfil Argon2 → Tokens (mostrar + guardar TXT) → Resumen |
| 3. Desbloqueo | F1, F2, F3, selector de llave · enlace "Usar token" |
| 4. Explorador | Árbol de archivos, vista previa en RAM, agregar / extraer / eliminar / eliminar y compactar |
| 5. Seguridad | Cambiar credenciales, regenerar tokens, autodestrucción, compactar, verificar |
| 6. Ajustes | Tema, idioma, auto-bloqueo |

Detalles:
- **ViewModels** con propiedades JavaFX; los controladores solo enlazan (evita los controladores gigantes de la referencia, H-13).
- Validación en vivo de cada contraseña (✓ / ✗ por regla) y medidor de fortaleza. F3 muestra la frase normalizada y el aviso de tildes; F2 rechaza `ñ`/tildes explicando el motivo.
- `PasswordField`; al confirmar, se copian a `char[]` y se limpia el campo.
- Toda operación pesada en `Task` + hilo virtual, con barra de progreso y botón cancelar.
- Advertencia sobre la recompresión de imágenes al elegir la llave.
- **Perfil Argon2:** *"Perfil Paranoico: necesitará ~1.5 GB de RAM libre en cualquier equipo donde la abras."* Por defecto, Estándar.
- **Carpetas sincronizadas:** si la bóveda está dentro de una carpeta de Drive, OneDrive o Dropbox conocida, avisar: *"Cierra la bóveda antes de que se sincronice."*

**Criterio de salida:** un usuario crea una bóveda con un video, una foto, un Word y un Excel, la cierra y la vuelve a abrir desde la UI.

---

### Fase 7 — Visor portátil y transporte

#### 7.1 Modo portátil
- `jpackage --type app-image` + `jlink` (runtime incluido; las librerías nativas de JavaFX van dentro de la imagen y no se extraen a `~/.openjfx`).
- En modo portátil, preferencias y recientes se guardan **junto a la app en el USB** o no se guardan.
- Estructura en el USB:
  ```
  USB/
  ├── CryptoKipu-win/     ← app portátil (una carpeta por sistema operativo)
  ├── mis-archivos.kipu
  └── (llave y TXT de tokens en OTRO medio)
  ```

#### 7.2 Vista previa solo en RAM

| Tipo | Cómo se muestra | Configuración obligatoria |
|---|---|---|
| Imágenes (PNG, JPG, GIF, BMP) | `javafx.scene.image.Image` desde el stream descifrado | `ImageIO.setUseCache(false)` al iniciar |
| Texto (TXT, CSV, MD, JSON, código) | Visor de texto, máximo 10 MB | — |
| PDF | PDFBox 3 renderiza páginas a imagen | `Loader.loadPDF(new RandomAccessReadBuffer(flujoMija), IOUtils.createMemoryOnlyStreamCache())`. En PDFBox 3 ya no se pasa `MemoryUsageSetting` a `loadPDF`. Nunca `createTempFileOnlyStreamCache()` |
| Word / Excel / PowerPoint | Apache POI (fidelidad parcial) | `TempFile.setTempFileCreationStrategy(...)` que **lanza excepción** si POI intenta escribir a disco |
| Audio: MP3, WAV, AIFF, AAC/M4A | `MediaPlayer` vía servidor local | — |
| Video: MP4 (H.264 + AAC) | `MediaPlayer` vía servidor local | — |
| Video: MP4 (H.265/HEVC + AAC) | `MediaPlayer` **si la plataforma lo decodifica** (soportado desde JavaFX 18; en Windows usa Media Foundation) | Si `MediaPlayer` da error de formato → "Abrir con aplicación externa" |
| MKV, AVI, WebM/VP9, FLV, FLAC, OGG y otros | **No soportado por JavaFX** → "Abrir con aplicación externa" (copia temporal con aviso) | — |

**Servidor de medios:** solo en `127.0.0.1`, puerto aleatorio, token de un solo uso en la URL, soporte de `Range`, se apaga al cerrar el archivo. Vive en `ui`/`infra`; ArchUnit lo prohíbe en `core`/`app`.

#### 7.3 Requisitos del equipo anfitrión
- Mismo sistema operativo que la carpeta portátil.
- **RAM:** al abrir, la app consulta la memoria disponible (`OperatingSystemMXBean`). Si es menor que la que pide el perfil de Argon2 + 512 MiB, advierte antes de intentar; si el heap máximo no alcanza, `MemoriaInsuficiente`.
- **Linux:** el video requiere las librerías GStreamer/libavcodec del sistema.
- **macOS:** la app sin firmar se bloquea; la guía explica cómo permitirla en *Ajustes → Privacidad y seguridad*. La firma con Developer ID queda en "Futuro".

#### 7.4 Opciones de JVM (en `jpackage --java-options` y en `run`)
```
-XX:-UsePerfData                                  # no escribe hsperfdata en %TEMP%
-XX:ErrorFile=$APPDIR/../logs/hs_err_%p.log       # crash log en el USB, no en el anfitrión
-XX:-HeapDumpOnOutOfMemoryError                   # nunca volcar el heap (contiene claves)
-XX:-CreateCoredumpOnCrash                        # sin core dump del proceso al caerse
-XX:+DisableAttachMechanism                       # jcmd/jmap no pueden conectarse a la JVM
-XX:MaxRAMPercentage=70                           # espacio para Argon2 hasta 1 GiB
-Djava.io.tmpdir=$APPDIR/../tmp                   # cualquier temporal residual queda en el USB
-Dprism.order=sw                                  # opcional, solo para equipos con drivers gráficos problemáticos
```
- `$APPDIR` lo expande el lanzador de `jpackage`; al pasarlo por la línea de comandos hay que escaparlo (`\$APPDIR`).
- Al cerrar, la app vacía la carpeta `tmp` de la app.

#### 7.5 Limitaciones honestas (van en `THREAT_MODEL.md` y en la ayuda)
- Capturas de pantalla, malware o volcados de memoria en un equipo comprometido: no se pueden impedir.
- **Pagefile/swap e hibernación:** el sistema operativo puede volcar claves de la RAM al disco anfitrión.
- "Abrir con aplicación externa" deja rastros (caché, recientes, miniaturas); en SSD el borrado no es garantizado.
- Los diálogos nativos de "Abrir archivo" pueden registrar la carpeta en los recientes del sistema operativo.
- Tokens de uso único y rotación de credenciales solo protegen la copia actualizada. **Una copia vieja del `.kipu` se abre con las credenciales y tokens viejos.**
- **Rollback:** alguien con acceso al archivo puede truncarlo al footer anterior y obtener un estado viejo pero válido.
- `failCounter` y autodestrucción: un atacante con una copia los evita; la defensa real es el costo de Argon2id.

**Criterio de salida:** desde un USB, en un segundo equipo, se ven una foto, un PDF y un Word, y se reproduce un MP4. Al cerrar no queda ningún archivo nuevo en `%TEMP%`, `~/.openjfx` ni el perfil del usuario (verificado con una comparación del directorio antes y después).

---

### Fase 8 — Endurecimiento

1. MK y subclaves en `MemorySegment` fuera del heap (FFM, `Arena.ofConfined()`), borradas al bloquear.
2. **Auto-bloqueo** por inactividad (5 min por defecto) y opcional al minimizar.
3. **Portapapeles**: si se copia un token, se limpia a los 30 s.
4. Retardo creciente entre intentos fallidos en la UI.
5. Sin logs con datos sensibles; sin telemetría; revisión de que ningún mensaje de error filtre qué factor falló.
6. **Autodestrucción** configurable (desactivada por defecto): `failCounter ≥ N` → sobrescribe los slots en **ambos** headers (respaldo primero, luego primario) + `fsync`, y deja la Zona A intacta para que la app pueda mostrar *"Esta bóveda fue destruida"* (`BovedaDestruida`).
7. **Records con secretos** sobrescriben `toString()`, y sus `char[]`/`byte[]` se borran en `close()` (implementan `AutoCloseable`).
8. **Archivo de bloqueo auxiliar** (`<ruta>.lock`) mantenido durante toda la compactación, para cerrar la ventana entre cerrar y reabrir el archivo compactado.

**Criterio de salida:** checklist del `THREAT_MODEL.md` revisada punto por punto.

---

### Fase 9 — Calidad y rendimiento

| Tipo | Qué valida |
|---|---|
| Vectores oficiales | Argon2id, AES-GCM, HKDF (de Fase 1, en CI) |
| Property-based (jqwik) | `descifrar(cifrar(x)) == x`, 0 B … 50 MB; normalización de F3 |
| Manipulación | 1 bit cambiado en header / bloque / manifest → error de integridad |
| Truncado / reordenado | Bloques faltantes o intercambiados → detectado |
| Fuzzing (Jazzer) | Parsers de header y manifest |
| Escritura interrumpida | Corte en cada paso → la bóveda sigue abriendo |
| Header de respaldo | Primario corrupto → abre y repara |
| Sin rastros de slots viejos | Después de rotar o autodestruir, los bytes del slot anterior no aparecen en el archivo |
| Límites del parser | Valores fuera de rango rechazados antes de derivar |
| Nonces | Ningún par (clave, nonce) se repite entre escrituras de manifest y slots |
| ArchUnit | Reglas de capas, `java.net` y servidor de medios |
| Checkstyle | Regla `Mija` en `main` y `test` |
| JMH | MB/s de cifrado y tiempo de Argon2 por perfil (meta: ~1 s por derivación en el equipo del usuario) |
| Rendimiento de UI | Arranque en frío < 2 s; ninguna operación de > 100 ms en el hilo de JavaFX |
| Sin escritura en anfitrión | Comparación de `%TEMP%` y del perfil del usuario antes y después de una sesión del visor |
| Hardware real | Ver tabla siguiente |

**Pruebas en hardware real** (checklist en `docs/PRUEBAS_HARDWARE.md`):

| Escenario | Resultado esperado |
|---|---|
| USB en **FAT32** con bóveda que pasaría de 4 GiB | Bloqueo previo con mensaje claro, bóveda intacta |
| USB en **exFAT** con bóveda > 4 GiB | Funciona |
| Rutas y nombres con tildes y `ñ` | Se guardan, listan y extraen igual |
| Rutas de > 260 caracteres en Windows | Se agregan y extraen sin error |
| Equipo **sin internet** | Todo el ciclo funciona |
| PC con 4 GB de RAM y bóveda "Paranoica" | Aviso / `MemoriaInsuficiente`, no se cae |
| Llave de seguridad de varios GB | Hash con barra de progreso, sin quedarse sin memoria |
| Teclado con otra distribución (inglés EE. UU.) | F1, F2 y F3 se escriben y abren la bóveda |
| Desconectar el USB a mitad de agregar archivos | Al reconectar, la bóveda abre en el estado anterior |

---

### Fase 10 — Empaquetado y release

1. `jlink` + `jpackage`: instalador `.msi` (Windows), `.deb` (Linux), `.dmg` (macOS) **y** versión portátil (`app-image`) para USB, con las opciones de JVM de 7.4.
2. SBOM CycloneDX y firma de artefactos en el workflow de release.
3. `README.md` con capturas, GIF de demostración, guía "Cómo llevar tu bóveda en un USB" (formatear en exFAT; llave y TXT de tokens en otro medio) y las limitaciones de 7.5.
4. `NOTICE` y avisos de licencia de las dependencias incluidos en la imagen.
5. Etiquetado de versión y publicación: **los realiza el usuario** (regla R1).

---

## 6. Orden de trabajo sugerido y entregas

| Iteración | Fases | Qué puedes probar al final |
|---|---|---|
| 1 | 0 + 1 | `gradlew check` verde, vectores criptográficos pasando |
| 2 | 2 | Contenedor `.kipu` con clave de prueba, resistente a cortes y con header de respaldo |
| 3 | 3 + 4 | Ciclo completo por pruebas de integración |
| 4 | 5 | Ciclo completo desde la terminal |
| 5 | 6 | Ciclo completo desde la UI |
| 6 | 7 | Abrir desde USB en otro equipo |
| 7 | 8 + 9 | Endurecimiento y batería de calidad |
| 8 | 10 | Instaladores y versión portátil |

Al terminar cada iteración el agente entrega: lista de archivos modificados, resultado de `./gradlew check` y los pasos para probarlo. **El commit y el push los hace el usuario.**

---

## 7. Futuro

- **Volúmenes divididos** (`.kipu.001`, `.002`…) para superar 4 GiB en FAT32. Planificado para **v1.1** (ADR-007).
- Firma de la app para macOS (Developer ID) y Windows (Authenticode).
- Unidad virtual (WinFsp / FUSE) para navegar la bóveda como un disco.
- Vincular la llave al número de serie de un USB/teléfono.
- Cascada XChaCha20 para modo paranoico.
- Reed-Solomon para tolerar corrupción parcial del archivo.
- Vista previa de Office con mayor fidelidad.
