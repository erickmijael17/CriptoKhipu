# Modelo de amenazas — CryptoKipu v1

> Estado: borrador de la Fase 0. Se completa en cada fase y se revisa punto por punto en la Fase 8.

## 1. Qué se protege

| Activo | Dónde vive |
|---|---|
| Contenido de los archivos | Bloques cifrados del `.kipu` |
| Nombres, rutas, tamaños y fechas de los archivos | Manifest cifrado |
| Clave maestra (MK) | Solo en memoria mientras la bóveda está abierta; en disco, envuelta en los slots |
| Credenciales F1–F3, llave F4 y tokens | Nunca se guardan; solo en memoria durante la derivación |

## 2. Atacantes considerados

| Atacante | Capacidad | ¿Cubierto? |
|---|---|---|
| A1. Encuentra o roba el USB / copia el `.kipu` | Lectura y modificación offline, tiempo ilimitado | **Sí**: la defensa es Argon2id con todos los factores combinados |
| A2. Encuentra la hoja de tokens | Tiene los tokens, no F1 | **Sí**: los slots de token exigen token + F1 |
| A3. Manipula el archivo para debilitarlo o tumbar la app | Cambia parámetros, sales, tamaños | **Sí**: zona A como AAD de cada slot, límites del parser |
| A4. Usuario del equipo anfitrión donde se abre la bóveda | Revisa `%TEMP%`, perfil, logs después de la sesión | **Parcial**: sin escrituras a disco salvo "abrir con aplicación externa" |
| A5. Malware o administrador en el equipo anfitrión **durante** la sesión | Captura pantalla, teclado y memoria | **No**: fuera del alcance de cualquier software |

## 3. STRIDE

| Categoría | Amenaza | Mitigación | Fase |
|---|---|---|---|
| **S**uplantación | Probar credenciales hasta acertar | Argon2id (256 MiB–1 GiB), un solo mensaje "Credenciales incorrectas", retardo creciente en la UI | 1, 3, 8 |
| **T**ampering | Alterar header, slots, manifest o bloques | AES-GCM con AAD, `headerMAC`, Zona A como AAD de los slots, CRC del footer | 1, 2 |
| **T**ampering | Reordenar, truncar o quitar bloques | `AAD = fileId ‖ índice ‖ esÚltimo` | 1 |
| **R**epudio | — | No aplica (uso personal, sin auditoría) | — |
| **I**nformation disclosure | Nombres de archivos visibles | Manifest cifrado | 2 |
| **I**nformation disclosure | Fugas a disco en el anfitrión (temporales, caché de imágenes, hsperfdata, volcados) | Configuración de PDFBox, POI, ImageIO y opciones de JVM | 7 |
| **I**nformation disclosure | Claves en el heap o en logs | FFM fuera del heap, `char[]`/`byte[]` borrados, `toString()` = `***`, política de registro | 0, 8 |
| **D**enial of service | Header con `m = 64 GiB` o manifest gigante | Límites del parser antes de derivar | 2 |
| **T**ampering | Bóveda creada por otra persona con rutas `../`, absolutas, UNC, letras de unidad, nombres reservados de Windows ("zip-slip") | `RutaInterna.problema` al extraer + comprobación de que el destino queda dentro de la carpeta elegida | 4 |
| **T**ampering | Enlaces simbólicos en carpetas agregadas (bucles, escapes) | No se siguen; se reportan como omitidos | 4 |
| **I**nformation disclosure | Archivo extraído a medias si un bloque está manipulado | Se escribe `.part` y se renombra solo tras verificar todos los bloques | 4 |
| **D**enial of service | Invertir `requiereLlave` o el umbral en una copia del header | Metadatos en la AAD del slot 0 (ADR-012); la UI pide la llave si cualquier copia la requiere | 4 |
| **I**nformation disclosure | Hash rápido de F1 en memoria | Eliminado: la rotación declara explícitamente qué cambia (ADR-012) | 4 |
| **D**enial of service | Primario falso con generación mayor y CRC recalculado que "repara" encima del respaldo bueno | Antes de desbloquear no se escribe en las zonas A/B; se repara solo después de un MAC válido | 2 |
| **T**ampering | Pista del footer (Zona C, no autenticada) apuntando a un footer viejo | Tras seguir la pista se busca un footer de generación mayor hasta el final del archivo; AAD del manifest = vaultId ‖ generación | 2 |
| **E**levation of privilege | Otro proceso se conecta a la JVM (`jcmd`, `jmap`) | `-XX:+DisableAttachMechanism` | 0, 7 |

## 4. Limitaciones conocidas (no se pueden resolver offline)

- Capturas de pantalla, malware o volcados de memoria en un equipo comprometido no se pueden impedir.
- **Pagefile/swap e hibernación:** el sistema operativo puede volcar claves de la RAM al disco anfitrión.
- "Abrir con aplicación externa" deja rastros (caché, recientes, miniaturas); en SSD el borrado no es garantizado.
- Los diálogos nativos de "Abrir archivo" pueden registrar la carpeta en los recientes del sistema operativo.
- Tokens de uso único y rotación de credenciales solo protegen la copia actualizada. **Una copia vieja del `.kipu` se abre con las credenciales y tokens viejos.**
- **Rollback:** quien tenga acceso al archivo puede truncarlo al footer anterior y obtener un estado viejo pero válido.
- `failCounter` y autodestrucción: un atacante con una copia los evita; la defensa real es el costo de Argon2id.
- Una llave de seguridad recomprimida (WhatsApp, Facebook, "fotos" de la nube) deja de funcionar: no hay forma de recuperarla.
- **Copias de claves dentro de JCA que no se pueden borrar.** `SecretKeySpec` copia los bytes de la clave al construirse; `Cipher`, `Mac` y `javax.crypto.KDF` guardan además el material expandido (tabla de rondas de AES, estado HMAC) en su estado interno. `SecureBytes` borra su propio arreglo, pero no esas copias, que quedan en el heap hasta que el recolector de basura reutiliza la memoria. Mitigación: crear esos objetos justo antes de usarlos y no guardarlos en campos (`AesGcmAead`, `HkdfDeriver` y `HeaderCodec.calcularMac` los crean por llamada), mantener abierta la bóveda el menor tiempo posible (auto-bloqueo, Fase 8) y `-XX:+DisableAttachMechanism` / sin volcados de heap. Lo mismo aplica a las copias internas de Bouncy Castle durante Argon2 (se llama a `Argon2Parameters.clear()`, pero el bloque de memoria de trabajo lo libera el recolector).
- **Credenciales como `String`.** `java.text.Normalizer` solo devuelve `String`, así que la frase F3 existe como texto inmutable durante la normalización; en la Fase 6, el `PasswordField` de JavaFX también guarda el texto como `String`. Esas copias no se pueden borrar y quedan en el heap hasta que el recolector reutiliza la memoria. El resto del camino (codificación UTF-8, entrada de Argon2, tokens, huella de la llave) usa `char[]`/`byte[]` que se borran tras usarlos.
- **Uso de un token en medios de solo lectura:** la recuperación se rechaza, porque el slot usado debe borrarse antes de entregar la sesión.
- **Bloqueo de archivo:** en Linux y macOS `tryLock()` es *advisory*: solo protege contra programas que también usen bloqueos (otra instancia de CryptoKipu). En carpetas sincronizadas (Drive, OneDrive, Dropbox) el bloqueo puede no funcionar y el cliente puede subir el archivo a medio escribir.
- **Ventana en la compactación:** entre cerrar el archivo viejo y reabrir el compactado (necesario en Windows para el movimiento atómico) otra instancia podría abrirlo. Mitigación pendiente: archivo de bloqueo auxiliar durante la compactación (Fase 8).
