# Protocolo de pruebas en hardware real (Fase 9)

Lo ejecuta **el usuario** con medios y equipos reales; el agente no puede hacerlo. Cada prueba tiene pasos, resultado
esperado y una tabla para anotar lo ocurrido. Anota siempre: fecha, equipo (modelo, SO, RAM), marca y modelo del USB,
sistema de archivos y versión de CriptoKhipu (diálogo «Acerca de»).

> **Antes de empezar:** usa una bóveda de prueba con archivos que no te importe perder y guarda una copia de seguridad
> (`.kipu` y `.kipukey`) fuera del USB. Compila el modo portátil con `./gradlew :cryptokipu-ui:opcionesJpackage` (las
> opciones de JVM) o usa el `.exe`/carpeta portátil ya empaquetados.

Equipo de referencia para las comparaciones: _______________________________________________

---

## 1. USB en FAT32 y en exFAT: cortar la conexión durante la escritura

**Objetivo.** Comprobar que, si el USB se desconecta a mitad de una escritura, la bóveda abre con **el último estado
válido** (el anterior a la escritura interrumpida), nunca con un estado a medias ni dañada. Es la garantía del commit en
tres pasos (blobs → manifest → footer → `fsync`) y de la escritura de headers (respaldo → primario).

**Material.** Un USB formateado en **FAT32** y otro (o el mismo, reformateado) en **exFAT**. Una carpeta con ~2 GB de
archivos variados (unos de 100 MB y alguno de 1 GB). Una bóveda nueva de perfil «Ligero» creada en cada USB.

**Pasos (repetir 10 veces por sistema de archivos).**
1. Crea la bóveda en el USB (`prueba.kipu`) con un archivo pequeño. Anota su SHA-256 y el número de archivos.
2. Abre la bóveda y empieza a **agregar los ~2 GB** (el progreso muestra el porcentaje).
3. Cuando la barra pase del 30 %–70 %, **arranca el USB físicamente** (sin «Expulsar de forma segura»).
4. Cierra o termina la aplicación (probablemente mostrará un error de E/S; es lo esperado).
5. Vuelve a conectar el USB y abre la bóveda con tus credenciales.
6. Comprueba: ¿abre? ¿lista **solo** los archivos del paso 1 (o todos, si el corte fue tras el commit)? Extrae el archivo del paso 1 y compara el SHA-256. Ejecuta «Verificar integridad».
7. Repite desde el paso 2 sobre la misma bóveda (ya con el estado válido anterior).

**Resultado esperado.** En las 10 repeticiones: la bóveda abre; contiene exactamente el último estado confirmado; «Verificar
integridad» no encuentra archivos dañados; el archivo original sale con el mismo SHA-256. En FAT32, además, una bóveda que
pasaría de 4 GiB se **bloquea antes de escribir** con un mensaje claro y la bóveda queda intacta.

| Repetición | FAT32: abre | FAT32: estado | FAT32: integridad | exFAT: abre | exFAT: estado | exFAT: integridad | Notas |
|---|---|---|---|---|---|---|---|
| 1 | | | | | | | |
| 2 | | | | | | | |
| 3 | | | | | | | |
| 4 | | | | | | | |
| 5 | | | | | | | |
| 6 | | | | | | | |
| 7 | | | | | | | |
| 8 | | | | | | | |
| 9 | | | | | | | |
| 10 | | | | | | | |

«Estado» = «anterior» (correcto) / «nuevo» (el corte llegó después del commit, también correcto) / «a medias» (**fallo**).

**Si falla:** guarda el `.kipu` tal cual (no lo abras más), anota el momento del corte y entrégalo para analizarlo; es un error grave.

---

## 2. Segundo equipo en modo portátil: sin rastros

**Objetivo.** Abrir en un equipo **ajeno** una foto, un PDF, un Word y un MP4 desde el USB y comprobar que no queda nada
en ese equipo (la prueba automática `SinRastrosEnElAnfitrionTest` cubre un equipo; esta cubre uno real).

**Material.** El USB con la carpeta de la app portátil (`CriptoKhipu-win/` con `portable.properties`), la bóveda y los
archivos de prueba (foto, PDF, Word, MP4 H.264 + AAC real). Un equipo donde **no** esté instalado CriptoKhipu.

**Pasos.**
1. **Antes de abrir nada**, en el equipo anfitrión anota el contenido de: `%TEMP%`, `%USERPROFILE%\.java`, `%USERPROFILE%\.openjfx`,
   `%LOCALAPPDATA%` (solo la lista de carpetas nuevas con «Ordenar por fecha»), `%APPDATA%\Microsoft\Windows\Recent` y
   `%LOCALAPPDATA%\Microsoft\Windows\Explorer` (miniaturas). Puedes guardarlo con
   `dir /s /b %TEMP% > antes-temp.txt` y similares, o con una herramienta de comparación de carpetas.
2. Conecta el USB y ejecuta `CriptoKhipu.exe` **desde el USB** (no lo copies al equipo).
3. Abre la bóveda y previsualiza: la foto, el PDF (varias páginas), el Word y reproduce el MP4 (adelanta y retrocede).
4. No uses «Abrir con aplicación externa» en esta prueba (esa función sí deja copias y lo advierte).
5. Cierra la bóveda y la aplicación. Espera 10 s.
6. Repite el inventario del paso 1 en `despues-*.txt` y compara.

**Resultado esperado.** Ningún archivo nuevo ni modificado en `%TEMP%`, `.java`, `.openjfx` ni el perfil; no aparecen
entradas nuevas de la bóveda ni de sus archivos en «Recientes». Se aceptan (documentados en `THREAT_MODEL.md` §4.1) el
registro de sucesos de Windows, `Prefetch`, `UserAssist` y cachés del propio sistema que registran **que se ejecutó** `CriptoKhipu.exe`.
En el USB: `portable.properties` puede crear `preferencias.properties`, `logs/` y `tmp/` junto a la app; `tmp/` queda vacío.

| Comprobación | Resultado (OK / archivos nuevos: cuáles) |
|---|---|
| `%TEMP%` sin archivos nuevos | |
| `.java` y `.openjfx` sin cambios | |
| Recientes sin entradas de la bóveda | |
| Miniaturas sin imágenes de la foto/MP4 | |
| MP4: reproduce, adelanta y retrocede | |
| PDF y Word se ven | |
| `tmp/` del USB vacío al cerrar | |
| Equipo (modelo / SO / RAM) | |

---

## 3. Win+V después de copiar un token

**Objetivo.** Verificar a mano que el historial del portapapeles de Windows **no** conserva los tokens (la prueba
automática solo verifica que los formatos de exclusión estén en el portapapeles; el panel Win+V no se puede automatizar).

**Pasos.**
1. Activa el historial del portapapeles: *Configuración → Sistema → Portapapeles → Historial del portapapeles: Activado*.
   Si puedes, activa también «Sincronizar entre dispositivos».
2. Crea una bóveda de prueba (o regenera los tokens de una existente) para llegar a la pantalla de tokens.
3. Pulsa **Copiar**. Abre **Win+V**.
4. Pega en el Bloc de notas y cierra la pantalla con **Continuar**. Abre Win+V otra vez.
5. Copia otro texto cualquiera antes de que pasen 30 s y espera: ese texto **no** debe borrarse.

**Resultado esperado.** Win+V no muestra los tokens (ni durante ni después); a los 30 s el portapapeles queda vacío si seguía
con los tokens; no se borra lo que copiaste después. Si Win+V **sí** muestra los tokens, anótalo: es el caso «parcial» del
THREAT_MODEL y la pantalla ya recomienda «Guardar TXT».

| Comprobación | Resultado |
|---|---|
| Win+V tras «Copiar»: ¿aparecen los tokens? | |
| Win+V tras «Continuar» | |
| A los 30 s el portapapeles se vacía | |
| Lo copiado después NO se borra | |
| Versión de Windows (`winver`) | |

---

## 4. Auto-bloqueo real

**Pasos.**
1. Ajustes → «Bloqueo automático»: 1 minuto. Abre una bóveda y reproduce un video.
2. No toques teclado ni ratón durante 60 s.
3. Repite con «Bloquear al minimizar» marcado: minimiza la ventana.
4. Repite con una operación larga en curso (agregar 1 GB): el bloqueo debe esperar a que termine.
5. Abre un archivo con «Abrir con aplicación externa», deja la aplicación externa con el archivo abierto y espera el bloqueo.

**Resultado esperado.** A los 60 s vuelve al desbloqueo con la misma ruta, el video se detiene, el explorador queda vacío y
aparece «Bóveda bloqueada». Con la operación en curso espera a que termine. Con la aplicación externa abierta aparece el
aviso «Una aplicación todavía usa archivos de la bóveda».

| Escenario | Resultado |
|---|---|
| Inactividad 60 s | |
| Al minimizar | |
| Con operación en curso | |
| Con aplicación externa abierta (aviso) | |

---

## 5. El `.exe` empaquetado

**Pasos.**
1. Genera la carpeta portátil y el instalador con `jpackage` (opciones: `./gradlew :cryptokipu-ui:opcionesJpackage`).
2. Ejecuta `CriptoKhipu.exe` en un equipo **sin Java instalado**.
3. Crea una bóveda, agrega archivos, ciérrala, ábrela con un token y rota las credenciales.
4. Instala una versión nueva encima: debe **actualizar** (mismo UUID de actualización) y no duplicar la entrada.
5. Mide el arranque en frío (cronómetro hasta ver la ventana): objetivo < 2 s en el equipo de referencia.

| Comprobación | Resultado |
|---|---|
| Arranca sin Java instalado | |
| Nombre visible «CriptoKhipu» (ventana, acceso directo, instalador) | |
| Ciclo completo (crear, agregar, token, rotar) | |
| La versión nueva actualiza, no duplica | |
| Arranque en frío (s) | |
| Aviso de SmartScreen / antivirus | |
