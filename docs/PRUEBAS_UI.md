# Pruebas de la interfaz (Fase 6)

JavaFX 25 no tiene modo *headless* (ver [ADR-013](adr/ADR-013-interfaz-javafx-25.md)). La interfaz se prueba en tres niveles.

## 1. Automáticas, sin pantalla (ViewModels)

`./gradlew :cryptokipu-ui:test`

| Prueba | Qué verifica |
|---|---|
| `FlujoCriterioTest` | Criterio de salida completo: crear una bóveda con un video, una foto, un Word y un Excel; tokens con confirmación; cerrar; reabrir; extraer y comparar byte a byte; recuperar con un token; rotación obligatoria; las credenciales viejas dejan de abrir. |
| `SeguridadInterfazTest` | Derivar en el hilo de JavaFX falla; mismo mensaje para cualquier factor incorrecto (incluido uno que ni cumple la política); tokens sin confirmar no continúan y el TXT nunca se sobrescribe; la rotación obligatoria no se puede saltar ni hacer a medias; aviso de intentos restantes. |
| `ComponentesViewModelTest` | Validación en vivo ✓/✗ y fortaleza; el asistente borra secretos que no coinciden; avisos de destino (carpeta sincronizada, FAT32, espacio, carpeta inexistente, archivo existente); árbol de 100 000 entradas en menos de 5 s con filtro. |
| `FxmlConvencionesTest` | Todo `fx:id` termina en `Mija`; toda clave `%texto` de las FXML existe; ningún texto dice qué factor falló. |

## 2. Automáticas, con pantalla (toolkit)

`ToolkitFxTest` se omite si no hay pantalla. En Linux: `xvfb-run ./gradlew :cryptokipu-ui:test`.

- `leerYLimpiar` copia carácter por carácter y deja el `PasswordField` vacío.
- Copiar y cortar en un `PasswordField` no tocan el portapapeles.
- Sin menú contextual; el texto accesible no contiene el secreto.
- `EjecutorTareas` trabaja en un hilo virtual y entrega el resultado en el hilo de JavaFX.
- La guardia detecta el hilo real de JavaFX.
- Cancelar descarta el resultado y lo cierra (por ejemplo, una sesión abierta durante Argon2id).

### Mutaciones verificadas (Fase 6)

Cada mutación se aplicó a mano y se comprobó que al menos una prueba falla:

| Mutación | Pruebas que la detectan |
|---|---|
| Derivar en el hilo de JavaFX (guardia desactivada) | `lasOperacionesLargasFallanEnElHiloDeJavaFx`, `laGuardiaDetectaElHiloRealDeJavaFx` |
| No limpiar el `PasswordField` | `leerYLimpiarCopiaCaracterPorCaracterYVaciaElCampo` (necesita pantalla) |
| Continuar desde los tokens sin confirmar | `tokensExigenConfirmacionYNuncaSobrescribenElTxt`, `crearCerrarReabrirRecuperarYRotar` |
| Mensaje distinto según el factor (prevalidar la política al desbloquear) | `cualquierFactorIncorrectoProduceElMismoMensaje` |
| Saltarse la rotación obligatoria | `laRotacionObligatoriaNoSePuedeSaltarNiHacerAMedias`, `crearCerrarReabrirRecuperarYRotar` |

### Fase 7: vista previa en RAM y servidor de medios

| Prueba | Qué comprueba |
|---|---|
| `VisoresEnMemoriaTest` | PDF, Word, Excel y PowerPoint se abren desde un canal en memoria; sin temporales; bomba ZIP rechazada; POI no puede crear temporales; ImageIO sin caché; caché de PDFBox solo memoria |
| `ServidorMediosTest` | Solo `127.0.0.1`; sin token o con token incorrecto → 404; solo `GET`; `Range` en posiciones aleatorias idéntico al original; 416 en rangos inválidos; apagado y retiro |
| `MediaPlayerFxTest` (pantalla) | `MediaPlayer` real adelanta y retrocede contra el servidor (WAV generado) |
| `VistaPreviaTest` | Límites: imagen > 100 MP, texto > 10 MB, formatos externos, PDF dañado, sin módulo de visores |
| `SinRastrosEnElAnfitrionTest` | Imagen + PDF + Word + texto + MP4 en una sesión: sin archivos nuevos en `java.io.tmpdir`, en la carpeta temporal real, `~/.openjfx`, `~/.java` ni el perfil; sin el texto claro en ningún archivo; la copia externa se borra al cerrar |
| `ModoPortatilTest` | Detección del marcador, preferencias junto a la app o en memoria, `tmp` se vacía |
| `ReglasArquitecturaTest` | Red solo en `ui.medios`; sin `java.util.prefs`; PDFBox/POI solo en `visores` |
| `verificarImagenJlink` | La imagen jlink con visores abre los cuatro formatos |

### Mutaciones verificadas (Fase 7)

Cada mutación se aplicó a mano, se comprobó que falla al menos una prueba y se revirtió:

| Mutación | Pruebas que la detectan |
|---|---|
| POI con la estrategia de temporales por defecto | `poiNoPuedeCrearTemporales`, `abrirYRenderizarNoCreaArchivosTemporales` |
| PDFBox con caché en disco (`createTempFileOnlyStreamCache`) | `laCacheDeFlujosDePdfBoxEsSoloMemoria` |
| Servidor escuchando en `0.0.0.0` | `enlazaSoloALaInterfazDeBucleLocal`, `desdeOtraInterfazNoConecta` |
| Servidor sin token | `sinTokenOConUnoIncorrectoResponde404` |
| ImageIO con caché | `imageIoNoUsaCacheEnDisco` |

## 3. Manual (antes de cada versión)

`./gradlew :cryptokipu-ui:ejecutarUi` (modo desarrollo: avisa en el registro si el hilo de JavaFX se bloquea más de 100 ms).

### Arranque
- [ ] La ventana aparece en menos de 2 s; el tema sigue al del sistema (cambiarlo con la app abierta).
- [ ] Sin bóveda abierta, Explorador y Seguridad están deshabilitados.

### Crear (con un video, una foto, un Word y un Excel)
- [ ] Arrastrar los cuatro archivos a la lista; también con "Agregar archivos…" y "Agregar carpeta…".
- [ ] F1/F2/F3: cada regla cambia de ✗ a ✓ al escribir; el medidor sube; "Siguiente" con valores distintos muestra "Los valores no coinciden".
- [ ] Clic derecho en un campo de contraseña: no aparece menú. Ctrl+C / Ctrl+X no copian.
- [ ] El botón del ojo muestra el valor y lo oculta solo a los 10 s.
- [ ] Llave: "Generar" pide dónde guardar el `.kipukey`; se ve el aviso de recompresión.
- [ ] Perfil: la RAM indicada cambia con el perfil; "Ligero" muestra su aviso.
- [ ] Destino en una carpeta de OneDrive/Drive/Dropbox: aviso de sincronización. En un USB FAT32 con más de 4 GiB: bloqueado.
- [ ] Durante "Derivando claves" el botón dice "No se puede cancelar en esta etapa".
- [ ] Tokens: "Continuar" deshabilitado hasta marcar la casilla; "Guardar TXT" sobre un archivo existente se niega; "Copiar" y luego "Continuar" vacía el portapapeles.

### Abrir y explorar
- [ ] Cerrar la bóveda y reabrirla desde Recientes y arrastrando el `.kipu` a Inicio.
- [ ] Un factor incorrecto (cualquiera) muestra el mismo mensaje. Con autodestrucción activa, se ve "Te quedan N intentos".
- [ ] Vista previa de la foto y de un TXT; PDF (con «Anterior/Siguiente»), Word, Excel y PowerPoint se ven en el panel.
- [ ] Un MP4 (H.264 + AAC) real se reproduce; arrastrar la barra de posición adelante y atrás no se congela; al elegir otro
      archivo el video se detiene.
- [ ] Un MKV o FLAC muestra el mensaje «Ábrelo con una aplicación externa»; «Abrir con aplicación externa…» pide confirmación
      con la advertencia, abre la copia y, al cerrar la bóveda, la carpeta `cryptokipu-tmp-*` ya no existe.
- [ ] Modo portátil (`portable.properties` junto a la app): con el USB en otro equipo, tomar la lista de `%TEMP%`, `~/.openjfx`,
      `~/.java` y el perfil antes y después de abrir una foto, un PDF, un Word y un MP4: sin archivos nuevos.
- [ ] Extraer con "Si ya existe: Renombrar" dos veces: aparece "nombre (2).ext". Eliminar pide confirmación.
- [ ] Filtro: escribir parte de un nombre reduce el árbol.

### Recuperar y rotar
- [ ] "Usar un token de recuperación" + F1: se abre el modal "Define credenciales nuevas".
- [ ] Escape, clic fuera y el menú lateral no lo cierran; no hay botón cancelar.
- [ ] Al guardar se muestran 6 tokens nuevos; luego el explorador muestra los archivos.
- [ ] El token usado ya no sirve.

### Cierre
- [ ] Cerrar la ventana durante "Cifrando archivos…": pide confirmación, cancela, espera y sale.
- [ ] Navegación solo con teclado: Tab recorre los controles; Alt+letra activa los botones con mnemónico (Alt+C crear, Alt+A abrir).

## Capturas

`./gradlew :cryptokipu-ui:generarCapturas` regenera [docs/capturas](capturas/) con una bóveda temporal y credenciales de demostración (necesita pantalla).
