# Cómo llevar tu bóveda en un USB

CriptoKhipu puede viajar en un USB y abrirse en otro equipo **sin instalar nada y sin dejar archivos en él**: los
archivos descifrados solo existen en la memoria (RAM) mientras la bóveda está abierta.

## 1. Qué va en el USB

```
USB/
├── CriptoKhipu-win/            ← la app portátil (una carpeta por sistema operativo)
│   ├── portable.properties    ← activa el modo portátil (puede estar vacío)
│   ├── CriptoKhipu.exe
│   ├── app/ runtime/          ← la aplicación y su Java incluido
│   ├── datos/                 ← preferencias (solo si no pones guardarPreferencias=false)
│   ├── tmp/                   ← temporales de la JVM; se vacía al cerrar
│   └── logs/                  ← registro de caídas de la JVM (hs_err_*.log)
├── mis-archivos.kipu          ← tu bóveda
└── (la llave de seguridad y la hoja de tokens van en OTRO medio)
```

- **Una carpeta de la app por sistema operativo:** `CriptoKhipu-win` no funciona en Linux ni en macOS.
- **Nunca guardes en el mismo USB** la llave de seguridad (`.kipukey`) ni la hoja de tokens: quien encuentre el USB
  tendría todo lo necesario salvo tus contraseñas.
- Formatea el USB en **exFAT** si vas a guardar archivos de más de 4 GiB (FAT32 limita la bóveda a 4 GiB − 1 B;
  CriptoKhipu te avisa al crearla).

## 2. El archivo `portable.properties`

Es el marcador del modo portátil. CriptoKhipu lo busca junto a la aplicación y en la carpeta superior.

```properties
# Opcional. Con false no se guarda NADA (ni preferencias ni bóvedas recientes) en el USB.
guardarPreferencias=true
```

En modo portátil CriptoKhipu no usa el registro de Windows ni `~/.java`, y la JVM arranca con estas opciones
(`./gradlew :cryptokipu-ui:opcionesJpackage` las imprime tal como se pasan a `jpackage --java-options`):

| Opción | Para qué |
|---|---|
| `-XX:-UsePerfData` | No escribe `hsperfdata` en `%TEMP%` |
| `-XX:ErrorFile=$APPDIR/logs/hs_err_%p.log` | El informe de caídas queda en el USB, no en el equipo |
| `-XX:-HeapDumpOnOutOfMemoryError`, `-XX:-CreateCoredumpOnCrash` | Nunca se vuelca la memoria (contiene claves) |
| `-XX:+DisableAttachMechanism` | `jcmd`/`jmap` no pueden conectarse a la JVM |
| `-XX:MaxRAMPercentage=70` | Deja espacio para Argon2id (hasta 1 GiB) |
| `-Djava.io.tmpdir=$APPDIR/tmp` | Cualquier temporal queda en el USB y se vacía al cerrar |
| `-Djava.awt.headless=true` | Las diapositivas y las páginas PDF se dibujan sin pantalla propia |
| `--enable-native-access=dev.erick.cryptokipu.core` | Permite bloquear en RAM las páginas con las claves (`VirtualLock`/`mlock`) para que no vayan al archivo de paginación |

## 3. Abrir tu bóveda en otro equipo

1. Conecta el USB y ejecuta `CriptoKhipu.exe` desde la carpeta de la app (no la copies al equipo).
2. **Abrir** → elige `mis-archivos.kipu` → escribe tus tres contraseñas (y conecta el medio con la llave, si la usas).
3. Antes de derivar las claves, la app compara la memoria libre con la que pide el perfil de la bóveda y **te advierte**
   si el equipo no alcanza (un perfil «Máximo» pide 1 GiB; en un equipo con poca RAM libre puede fallar).
4. Mira tus archivos en la vista previa. Al terminar pulsa **Cerrar bóveda** y cierra la ventana: se vacía `tmp`.
5. Desconecta el USB **después** de cerrar la aplicación, nunca durante una escritura.

## 4. Qué se puede ver

| Formato | Cómo se muestra | Dónde |
|---|---|---|
| PNG, JPG, GIF, BMP | Imagen (hasta ~100 megapíxeles) | RAM |
| TXT, MD, CSV, JSON, XML, código | Texto (hasta 10 MB) | RAM |
| PDF | Páginas dibujadas al pedirlas | RAM |
| Word (docx), Excel (xlsx) | Texto de párrafos, tablas y hojas | RAM |
| PowerPoint (pptx) | Diapositivas dibujadas | RAM |
| MP4 (H.264 + AAC), M4A, MP3, WAV, AIFF | Reproductor (adelantar y retroceder) | RAM + servidor local `127.0.0.1` |
| MKV, AVI, WebM, HEVC, FLAC, MOV, doc/xls/ppt antiguos | **Abrir con aplicación externa** | copia temporal en `tmp` |

### Abrir con aplicación externa

Crea una copia **sin cifrar** en `tmp`, con aviso previo, y la abre con la aplicación del sistema. Se borra al cerrar la
bóveda; si Windows la tiene bloqueada se reintenta y, si no se puede, se limpia al próximo inicio. **Esa aplicación puede
dejar rastros** (archivos recientes, miniaturas, caché) en el equipo anfitrión. Úsala solo en equipos de confianza.

## 5. Requisitos y límites del equipo anfitrión

- **Mismo sistema operativo** que la carpeta de la app.
- **RAM libre** suficiente para el perfil de la bóveda + ~512 MiB.
- **Linux:** el video necesita las bibliotecas GStreamer/libavcodec del sistema.
- **macOS:** la app sin firmar la bloquea Gatekeeper: *Ajustes del Sistema → Privacidad y seguridad → Abrir de todos modos*.
- **No se puede impedir:** capturas de pantalla, malware o un administrador en ese equipo mientras la bóveda está abierta;
  el *pagefile*/swap y la hibernación pueden llevar claves de la RAM al disco del anfitrión
  (ver [THREAT_MODEL.md](THREAT_MODEL.md), §4). **No abras tu bóveda en un equipo que no sea de confianza.**
- Una copia vieja del `.kipu` se abre con las credenciales y tokens viejos: borra las copias antiguas.
