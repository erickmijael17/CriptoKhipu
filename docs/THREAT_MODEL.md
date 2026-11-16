# Modelo de amenazas — CriptoKhipu v1

> Estado: **revisado punto por punto en la Fase 8** (sección 5: cada amenaza queda como *mitigada* —con la prueba que
> lo demuestra—, *mitigada parcialmente* o *aceptada* —con su justificación—). Se vuelve a revisar al cambiar el formato
> o agregar una superficie nueva.

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
| **I**nformation disclosure | Claves en el heap o en logs | `SecureBytes` en `MemorySegment` fuera del heap (`Arena.ofShared`), puesto en cero al cerrar y bloqueado en RAM (`VirtualLock`/`mlock`, best-effort); `char[]`/`byte[]` borrados, `toString()` = `***`, política de registro y auditoría de fugas | 0, 8 |
| **D**enial of service | Header con `m = 64 GiB` o manifest gigante | Límites del parser antes de derivar | 2 |
| **T**ampering | Bóveda creada por otra persona con rutas `../`, absolutas, UNC, letras de unidad, nombres reservados de Windows ("zip-slip") | `RutaInterna.problema` al extraer + comprobación de que el destino queda dentro de la carpeta elegida | 4 |
| **T**ampering | Enlaces simbólicos en carpetas agregadas (bucles, escapes) | No se siguen; se reportan como omitidos | 4 |
| **I**nformation disclosure | Archivo extraído a medias si un bloque está manipulado | Se escribe `.part` y se renombra solo tras verificar todos los bloques | 4 |
| **D**enial of service | Invertir `requiereLlave` o el umbral en una copia del header | Metadatos en la AAD del slot 0 (ADR-012); la UI pide la llave si cualquier copia la requiere | 4 |
| **I**nformation disclosure | Hash rápido de F1 en memoria | Eliminado: la rotación declara explícitamente qué cambia (ADR-012) | 4 |
| **T**ampering | Archivo creado en el destino entre la comprobación de colisión y el renombrado | Enlace duro atómico (`createLink` falla si existe) y luego borrar el `.part` | 5 |
| **I**nformation disclosure | Secretos en el historial de la shell o en la lista de procesos | Ninguna opción de la CLI acepta un secreto (prueba estructural); ArchUnit prohíbe `System.getenv`; se piden por consola o, explícitamente, por stdin | 5 |
| **I**nformation disclosure | Tokens que terminan en logs o archivos de CI | Solo a `--tokens-out` (nunca sobrescribe) o a una TTY; en una tubería es error de uso, comprobado antes de actuar | 5 |
| **D**enial of service | Primario falso con generación mayor y CRC recalculado que "repara" encima del respaldo bueno | Antes de desbloquear no se escribe en las zonas A/B; se repara solo después de un MAC válido | 2 |
| **T**ampering | Pista del footer (Zona C, no autenticada) apuntando a un footer viejo | Tras seguir la pista se busca un footer de generación mayor hasta el final del archivo; AAD del manifest = vaultId ‖ generación | 2 |
| **E**levation of privilege | Otro proceso se conecta a la JVM (`jcmd`, `jmap`) | `-XX:+DisableAttachMechanism` | 0, 7 |

| **I**nformation disclosure | Otro proceso del equipo lee el contenido servido a `MediaPlayer` | Servidor solo en `127.0.0.1`, puerto aleatorio, token de 256 bits por archivo (sin él: 404), solo `GET`, se apaga con la sesión; ArchUnit confina la red a `ui.medios` | 7 |
| **D**enial of service | Bomba de descompresión (imagen, ZIP de Office, PDF) | Dimensiones de la imagen leídas de la cabecera antes de decodificar (≤ 100 MP); `ZipSecureFile` (relación 1:100, entrada ≤ 100 MiB, ≤ 5000 entradas); documentos ≤ 100 MiB y texto ≤ 10 MB | 7 |
| **I**nformation disclosure | Rastros en el anfitrión: temporales de POI, caché de PDFBox, caché de ImageIO, `java.util.prefs` (registro de Windows), hsperfdata, volcados | POI con estrategia de temporales que lanza, PDFBox con caché en memoria, `ImageIO.setUseCache(false)`, regla ArchUnit contra `java.util.prefs`, opciones de JVM del modo portátil; prueba «sin rastros» (`SinRastrosEnElAnfitrionTest`) | 7 |

## 4. Limitaciones conocidas (no se pueden resolver offline)

- Capturas de pantalla, malware o volcados de memoria en un equipo comprometido no se pueden impedir.
- **Pagefile/swap e hibernación:** el sistema operativo puede volcar claves de la RAM al disco anfitrión. Desde la Fase 8
  las claves viven fuera del heap y se **intenta** bloquear sus páginas (`VirtualLock` en Windows, `mlock` en Linux y
  macOS): eso **reduce, pero no elimina**, el riesgo. Falla en silencio (se registra un aviso una sola vez, sin datos) si
  el sistema no concede más páginas (cuota del proceso o `RLIMIT_MEMLOCK`), y no cubre la hibernación, que escribe
  toda la RAM. Quedan fuera del bloqueo las copias temporales que exige JCA (`byte[]` en el heap, ver abajo), el texto
  de las credenciales mientras se escriben y el contenido descifrado de los visores.
- "Abrir con aplicación externa" deja rastros (caché, recientes, miniaturas); en SSD el borrado no es garantizado.
- Los diálogos nativos de "Abrir archivo" pueden registrar la carpeta en los recientes del sistema operativo.
- Tokens de uso único y rotación de credenciales solo protegen la copia actualizada. **Una copia vieja del `.kipu` se abre con las credenciales y tokens viejos.**
- **Rollback:** quien tenga acceso al archivo puede truncarlo al footer anterior y obtener un estado viejo pero válido.
- `failCounter` y autodestrucción: un atacante con una copia los evita; la defensa real es el costo de Argon2id.
- Una llave de seguridad recomprimida (WhatsApp, Facebook, "fotos" de la nube) deja de funcionar: no hay forma de recuperarla.
- **Copias de claves dentro de JCA que no se pueden borrar.** `SecretKeySpec` copia los bytes de la clave al construirse; `Cipher`, `Mac` y `javax.crypto.KDF` guardan además el material expandido (tabla de rondas de AES, estado HMAC) en su estado interno. `SecureBytes` borra su propio arreglo, pero no esas copias, que quedan en el heap hasta que el recolector de basura reutiliza la memoria. Mitigación: crear esos objetos justo antes de usarlos y no guardarlos en campos (`AesGcmAead`, `HkdfDeriver` y `HeaderCodec.calcularMac` los crean por llamada), mantener abierta la bóveda el menor tiempo posible (auto-bloqueo, Fase 8) y `-XX:+DisableAttachMechanism` / sin volcados de heap.
  Verificado en la Fase 8: `SecureBytes.conBytes` entrega la copia del heap solo durante el uso y la borra aunque falle;
  `envolver` borra el arreglo original al instante. Lo mismo aplica a las copias internas de Bouncy Castle durante Argon2 (se llama a `Argon2Parameters.clear()`, pero el bloque de memoria de trabajo lo libera el recolector).
- **Credenciales como `String`.** `java.text.Normalizer` solo devuelve `String`, así que la frase F3 existe como texto inmutable durante la normalización. En la interfaz hay más copias que no se pueden borrar y quedan en el heap hasta que el recolector reutiliza la memoria:
  - `PasswordField` guarda el texto como `String` (cada tecla crea uno nuevo). Mitigación: se lee con `getCharacters()` carácter por carácter a un `char[]` y el campo se limpia antes de derivar; sin menú contextual; copiar y cortar están deshabilitados.
  - El botón "ojo" muestra el valor en un `Label` (un `String` más) durante 10 s como máximo.
  - Los tokens se muestran en una lista (los controles de JavaFX no aceptan `char[]`) y, si el usuario los copia, pasan por el portapapeles del sistema. Desde la Fase 8 se limpian a los 30 s, al bloquear y al salir, **solo si el portapapeles aún contiene ese texto** (se compara una huella SHA-256: no se guarda otra copia). En Windows la copia pide al sistema que no la guarde en el historial (Win+V) ni en la nube (formatos `ExcludeClipboardContentFromMonitorProcessing`, `CanIncludeInClipboardHistory` y `CanUploadToCloudClipboard`; una prueba comprueba, desde otro proceso, que están en el portapapeles del sistema). Es *best-effort*: no se pudo observar el panel Win+V, en Linux y macOS no hay formato equivalente y un programa que vigile el portapapeles puede copiarlo. Por eso la pantalla de tokens recomienda «Guardar TXT».
  - La validación en vivo copia el contenido a un `char[]` que se borra tras evaluar; el medidor de fortaleza no guarda nada.

  El resto del camino (codificación UTF-8, entrada de Argon2, tokens, huella de la llave) usa `char[]`/`byte[]` que se borran tras usarlos.
- **Uso de un token en medios de solo lectura:** la recuperación se rechaza, porque el slot usado debe borrarse antes de entregar la sesión.
- **Bloqueo de archivo:** en Linux y macOS `tryLock()` es *advisory*: solo protege contra programas que también usen bloqueos (otra instancia de CriptoKhipu). En carpetas sincronizadas (Drive, OneDrive, Dropbox) el bloqueo puede no funcionar y el cliente puede subir el archivo a medio escribir.
- **Carrera al extraer en FAT32/exFAT:** esos sistemas no admiten enlaces duros, así que se usa `Files.move` sin reemplazo. En Linux y macOS el JDK comprueba si el destino existe y después llama a `rename()`, que reemplaza: un archivo creado justo en ese instante se pisaría. Solo puede aprovecharlo alguien que ya escribe en la carpeta de destino. En NTFS, ext4 y APFS se usa el enlace duro, que es atómico.
- **CLI y `--secretos-stdin`:** los secretos pasan por la tubería que los entrega (un script, un gestor de contraseñas). Proteger esa tubería es responsabilidad de quien la arma; por eso exige el flag explícito.
- **Ventana en la compactación:** entre cerrar el archivo viejo y reabrir el compactado (necesario en Windows para el movimiento atómico) el archivo principal no está bloqueado. **Mitigada en la Fase 8** con `<ruta>.lock`, que se mantiene durante todo el proceso (incluido el reemplazo) y se borra al terminar; si quedó huérfano se limpia al abrir, comprobando que ninguna instancia viva lo tenga bloqueado. Limitación residual: el `.lock` es un bloqueo *advisory* en Linux y macOS (como el del archivo principal) y no protege contra programas que ignoren los bloqueos.

### 4.1 Lo que el modo portátil NO controla (Fase 7)

- **Pagefile/swap e hibernación del anfitrión:** si el sistema paginó la memoria de la JVM, claves y texto claro pueden
  quedar en el disco del equipo. No hay forma de impedirlo desde Java (sin privilegios ni bloqueo de memoria del SO).
- **Cachés de fuentes del sistema operativo** y de la pila gráfica: dibujar texto (PDF, diapositivas, la propia interfaz)
  puede hacer que el sistema registre fuentes o glifos en sus cachés. PDFBox escribe además su caché de fuentes en una
  carpeta propia dentro de `java.io.tmpdir` (en modo portátil: `$APPDIR/tmp`; fuera de él, `%TEMP%`), que se borra al salir
  y no contiene el documento.
- **Aplicaciones externas** («Abrir con aplicación externa»): reciben una copia sin cifrar y pueden dejar recientes,
  miniaturas, cachés y copias de seguridad; en SSD el borrado no es garantizado. Si Windows tiene el archivo bloqueado se
  reintenta y se limpia al siguiente inicio.
- **Registro de sucesos, `Prefetch`, `UserAssist`, lista de «recientes» de Windows** y cualquier telemetría del sistema: el
  hecho de haber ejecutado `CriptoKhipu.exe` puede quedar registrado aunque ningún archivo del usuario cambie.
- **Auto-bloqueo y rotación obligatoria:** tras usar un token la sesión queda en rotación obligatoria y el auto-bloqueo **espera** (cerrarla gastaría el token sin dejar credenciales nuevas); lo mismo con una operación en curso o unos tokens recién generados sin guardar. Mientras tanto la clave sigue en memoria. Se acepta: es el menor de los males y la ventana dura lo que tarde el usuario en terminar.
- **Servidor de medios:** el token viaja en la URL local; un proceso con permiso para leer la memoria de la JVM o para
  espiar el tráfico de bucle local (administrador) podría obtenerlo. Es el mismo atacante A5 (fuera de alcance).
- **Decodificadores de terceros** (PDFBox, POI, JavaFX Media/GStreamer/Media Foundation) procesan archivos del usuario: un
  fallo en ellos es una vulnerabilidad del componente. Mitigaciones: límites de tamaño, dependencias bloqueadas y verificadas
  por checksum, y vista previa limitada a lo que la plataforma ya sabe decodificar.
- **Biblioteca firmada:** `jlink` no admite jars modulares firmados; la imagen se construye con
  `--ignore-signing-information` (Bouncy Castle viene firmado) y la integridad de los jars la garantizan
  `gradle/verification-metadata.xml` y los `gradle.lockfile`, no la firma dentro de la imagen.

## 5. Revisión punto por punto (Fase 8)

Estados: **Mitigada** (hay una prueba que lo demuestra), **Parcial** (se reduce, con un resto explicado) y **Aceptada**
(no se puede o no conviene resolver; con su justificación). Las pruebas se ejecutan con `./gradlew clean check`.

### 5.1 Atacantes

| Atacante | Estado | Prueba o justificación |
|---|---|---|
| A1. USB o `.kipu` robado, offline | **Mitigada** | `Argon2idKdfTest.vectorOficialRfc9106`, `HeaderCodecTest.memoriaDe64GiBSeRechazaAntesDeDerivarEnMenosDe10Ms`; el costo de Argon2id con los cuatro factores es la defensa (ver «Rollback» y «Copia vieja» en 5.3: lo que ya se copió no se puede revocar) |
| A2. Hoja de tokens | **Mitigada** | `GestorSlotsTest.creaUnSlotPrincipalYSeisTokensQueAbrenLaMismaClave` (los slots de token se derivan de token **y** F1), `AutenticacionYSesionTest.elSlotDelTokenUsadoSeBorraEnAmbosHeadersYNoSirveDosVeces` |
| A3. Archivo manipulado | **Mitigada** | `HeaderCodecTest.manipulacionConCrcRecalculadoLaDetectaElMac`, `ContenedorKipuTest.unBitEnUnBlobEsIntegridadFallidaDelBloque`, `…unBitEnElManifestEsIntegridadFallidaDelManifest`, `HeaderCodecTest.bloquesBasuraNuncaLanzanExcepcionesNoControladas` |
| A4. Usuario del equipo anfitrión, después de la sesión | **Parcial** | `SinRastrosEnElAnfitrionTest` (comparación de `%TEMP%` y del perfil antes y después); el resto —Prefetch, recientes del sistema, caché de fuentes, SSD tras borrar— queda en 4.1 |
| A5. Malware o administrador durante la sesión | **Aceptada** | Fuera del alcance de cualquier software: lee la memoria, el teclado y la pantalla. La captura de pantalla de Windows (`SetWindowDisplayAffinity`) queda en «Futuro» (5.4) |

### 5.2 STRIDE

| Amenaza | Estado | Prueba o justificación |
|---|---|---|
| Probar credenciales hasta acertar | **Mitigada** | Costo de Argon2id; un solo mensaje: `SeguridadInterfazTest.cualquierFactorIncorrectoProduceElMismoMensaje`, `FxmlConvencionesTest.ningunTextoDeLaInterfazMencionaQueFactorFallo`; retardo creciente 0, 0, 2, 4, 8, 16, 30 s en UI y CLI: `RetardoIntentosTest`, `RetardoDesbloqueoTest`, `CliRetardoIntentosTest`. El retardo es solo en memoria: **no** frena a quien copie el archivo o reinicie la aplicación (la defensa real sigue siendo Argon2id) |
| Alterar header, slots, manifest o bloques | **Mitigada** | `AesGcmAeadTest.detectaUnBitCambiadoEnElCifrado`, `HeaderCodecTest.elSlotUsaLaZonaAElIndiceYElTipoComoAad`, `ContenedorKipuTest.unBitEnAmbasCopiasDelHeaderEsIntegridadFallida` |
| Reordenar, truncar o quitar bloques | **Mitigada** | `ChunkCipherTest.detectaIntercambioDeBloques`, `…detectaQueSeQuitoElUltimoBloque`, `…detectaTruncadoDentroDeUnBloque` |
| Nombres de archivos visibles | **Mitigada** | Manifest cifrado: `ManifestYFooterTest.manifestIdaYVueltaConTildes`; `ContenedorKipuTest.eliminarYCompactarQuitaLosBloquesDelArchivo` |
| Fugas a disco en el anfitrión | **Mitigada** (con 4.1) | `SinRastrosEnElAnfitrionTest.unaSesionConImagenPdfWordTextoYMp4NoDejaRastros`, `VisoresEnMemoriaTest.abrirYRenderizarNoCreaArchivosTemporales`, `OpcionesJvmPruebasTest` |
| Claves en el heap o en logs | **Mitigada** / **Parcial** | Fuera del heap: `SecureBytesYNonceTest.cerrarPoneEnCeroElMemorySegmentAntesDeLiberarlo`, `trasCerrarLaSesionLaClaveMaestraQuedaEnCero…`; logs: `AuditoriaDeFugasTest` (registro en DEBUG, stdout, stderr y mensajes de error de todo el flujo: ninguna contraseña, token, nombre interno ni volcado). Parcial por las copias de JCA y el texto de las credenciales (4) |
| DoS con `m = 64 GiB` o manifest gigante | **Mitigada** | `HeaderCodecTest.memoriaDe64GiBSeRechazaAntesDeDerivarEnMenosDe10Ms`, `ManifestYFooterTest.demasiadasEntradasSeRechazanSinReservarMemoria` |
| Rutas `../`, absolutas, UNC o nombres reservados («zip-slip») | **Mitigada** | `RutaInternaTest.rutasPeligrosasONoPortables`, `ArchivosTest.manifestFabricadoConRutasMaliciosasNoEscribeFueraDelDestino` |
| Enlaces simbólicos en carpetas agregadas | **Mitigada** | `ArchivosTest.losEnlacesSimbolicosNoSeSiguen` |
| Archivo extraído a medias | **Mitigada** | `ArchivosTest.unBloqueManipuladoNoDejaNiArchivoNiPart` |
| Invertir `requiereLlave` o el umbral en una copia | **Mitigada** | `AutenticacionYSesionTest.invertirRequiereLlaveEnElPrimarioAbreConElRespaldoYLoRepara`, `…cambiarElUmbralEnUnaCopiaHaceFallarSuSlotPrincipal` |
| Hash rápido de F1 en memoria | **Mitigada** | Eliminado (ADR-012); `AutenticacionYSesionTest.rotarExigeCredencialesVigentesCorrectas` |
| Archivo creado entre la comprobación y el renombrado | **Mitigada** (NTFS, ext4, APFS) / **Aceptada** (FAT32) | `ColocadorSeguroTest.destinoCreadoJustoAntesDelEnlaceNoSePisa`; en FAT32/exFAT no hay enlaces duros y la carrera queda (4) |
| Secretos en historial de la shell o lista de procesos | **Mitigada** | `CliEnProcesoTest.ningunaOpcionAceptaUnSecretoComoArgumento`, `ReglasArquitecturaTest.nadaLeeVariablesDeEntorno` |
| Tokens en logs o archivos de CI | **Mitigada** | `CliEnProcesoTest.sinTerminalNiTokensOutLosTokensNoSeImprimen`, `…tokensOutNuncaSobrescribe` |
| «Reparar» con un primario falso más nuevo | **Mitigada** | `ContenedorKipuTest.primarioFalsoMasNuevoNoDestruyeElRespaldo` |
| Pista del footer apuntando a uno viejo | **Mitigada** | `ContenedorKipuTest.pistaQueApuntaAUnFooterViejoCargaElEstadoMasNuevo` |
| Otro proceso se conecta a la JVM | **Mitigada** | `-XX:+DisableAttachMechanism`: `OpcionesJvmPruebasTest.laJvmDePruebasRecibeLasOpcionesDeSeguridad` |
| Otro proceso lee lo servido a `MediaPlayer` | **Mitigada** | `ServidorMediosTest.enlazaSoloALaInterfazDeBucleLocal`, `…sinTokenOConUnoIncorrectoResponde404`, `…elTokenTiene256BitsYCambiaEnCadaPublicacion`; ArchUnit `laRedYElServidorHttpSoloVivenEnUiMedios`. Un administrador local (A5) puede espiar el bucle local |
| Bomba de descompresión | **Mitigada** | `VisoresEnMemoriaTest.unaBombaZipSeRechazaAntesDeDescomprimirla`, `VistaPreviaTest.dimensionesDeLaCabeceraSinDecodificar` |
| Rastros de POI, PDFBox, ImageIO, `java.util.prefs` | **Mitigada** | `VisoresEnMemoriaTest.poiNoPuedeCrearTemporales`, `…imageIoNoUsaCacheEnDisco`; ArchUnit `sinJavaUtilPrefs` |

### 5.3 Limitaciones conocidas

| Limitación | Estado | Justificación |
|---|---|---|
| Capturas de pantalla, malware, volcados en equipo comprometido | **Aceptada** | No se puede impedir desde una aplicación de usuario |
| Pagefile/swap e hibernación | **Parcial** | `SecureBytes` fuera del heap y bloqueado en RAM (`SecureBytesYNonceTest.elBloqueoEnRamEsBestEffortYNuncaLanza`); en Windows con la cuota por defecto solo caben unas decenas de páginas bloqueadas a la vez (el resto de claves sigue fuera del heap pero paginable) y la hibernación no se puede evitar |
| Abrir con aplicación externa deja rastros | **Aceptada** | Es lo que pide el usuario. La carpeta es privada y se borra al cerrar o bloquear; si la aplicación aún tiene archivos abiertos se avisa (`AutoBloqueoTest.siUnaAplicacionExternaTieneUnArchivoAbiertoAvisaYLaCarpetaQuedaParaDespues`) y se limpia al próximo inicio (`TempWorkspaceYPreferenciasTest.seLimpianRestosAbandonadosPeroNoLosDeOtraInstanciaViva`); en SSD el borrado no es físico |
| Diálogos nativos de «Abrir archivo» registran la carpeta | **Aceptada** | Es del sistema operativo |
| Copia vieja del `.kipu` abre con credenciales y tokens viejos | **Aceptada** | Inherente a un archivo copiable sin servidor; la rotación protege solo la copia actualizada |
| Rollback al footer anterior | **Aceptada** | Estado viejo pero válido; sin contador externo no se puede detectar |
| `failCounter` y autodestrucción evitables con una copia | **Aceptada** | La defensa real es Argon2id. La UI exige **doble confirmación** al activarla y avisa de que también destruye la bóveda para su dueño (`RetardoDesbloqueoTest.activarLaAutodestruccionExigeDobleConfirmacionYDesactivarla_no`); `AutenticacionYSesionTest.alcanzarElUmbralDestruyeAmbosHeaders` |
| Llave de seguridad recomprimida | **Aceptada** | Cambia sus bytes; no hay forma de recuperarla |
| Copias de claves dentro de JCA | **Parcial** | Se crean justo antes de usarse y se borran justo después (`SecureBytesYNonceTest.conBytesBorraLaCopiaTemporalAunqueElUsoFalle`); `Cipher`, `Mac` y `KDF` conservan estado interno que el recolector libera |
| Credenciales como `String` | **Parcial** | `Normalizer` y `PasswordField` obligan a `String`; el resto usa `char[]` borrado (`CredencialesSeBorranAlCerrar…`, `ToolkitFxTest.leerYLimpiarCopiaCaracterPorCaracterYVaciaElCampo`) |
| Token en medios de solo lectura | **Aceptada** | Se rechaza: el slot debe borrarse antes de entregar la sesión |
| Bloqueo de archivo *advisory* y carpetas sincronizadas | **Aceptada** | Limitación del sistema operativo y de los clientes de nube; `DetectorCarpetaSincronizadaTest` avisa al crear |
| Carrera al extraer en FAT32/exFAT | **Aceptada** | Solo la aprovecha quien ya escribe en la carpeta de destino |
| `--secretos-stdin` | **Aceptada** | La tubería es responsabilidad de quien la arma; exige el flag explícito |
| Ventana en la compactación | **Mitigada** | `ContenedorKipuTest.duranteElReemplazoDeLaCompactacionOtraInstanciaNoPuedeAbrir`, `…elBloqueoSeBorraAunqueLaCompactacionFalle`, `…unBloqueoHuerfanoSeLimpiaAlAbrir`, `…unBloqueoQueUnaInstanciaVivaTieneNoSeBorraYImpideAbrir` |
| Portapapeles e historial de Windows | **Parcial** | `GuardiaPortapapelesTest` (30 s, al bloquear y al salir, solo si sigue igual), `ToolkitFxTest.windowsRecibeLosFormatosQueExcluyenElContenidoDelHistorial`; ver 4 |
| Sesión abierta y desatendida | **Mitigada** | `AutoBloqueoTest.trasCincoMinutosSinActividadCierraLaSesionYLaClaveQuedaInutilizable` (la sesión se cierra de verdad), `…conUnaOperacionEnCursoEsperaYBloqueaCuandoTermina`, `…alMinimizarBloqueaSoloSiEstaActivado`; por defecto 5 min, configurable, 0 = desactivado |

### 5.4 Futuro

- **Excluir la ventana de las capturas de pantalla (Windows, `SetWindowDisplayAffinity(WDA_EXCLUDEFROMCAPTURE)`):** no se
  implementó. JavaFX no ofrece una API pública para obtener el `HWND` de una `Stage`; habría que reflejar clases internas de
  `com.sun.glass.ui` (exigiría `--add-opens` y se rompe entre versiones de JavaFX) o buscar la ventana por su título con
  `FindWindow`, que es frágil (el título cambia con el idioma y puede haber otra ventana con el mismo nombre). Además solo
  protege de capturas hechas por la propia sesión de Windows, no de malware con privilegios (A5). Se retomará si JavaFX
  publica una forma estable de obtener el identificador de la ventana.
