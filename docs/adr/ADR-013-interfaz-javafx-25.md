# ADR-013: Interfaz sobre JavaFX 25 (LTS), AtlantaFX 2.1.0 y pruebas sin modo headless

**Estado:** aceptada (Fase 6)

## Contexto
1. AtlantaFX 3.0.0 se compiló contra OpenJFX 27 (`openjfx.version=27` en su POM padre). Con JavaFX 25 falla en tiempo de ejecución al abrir el primer modal: `NoSuchMethodError: Interpolator.ofSpline(double, double, double, double)`, un método que JavaFX 25 no tiene. Compila sin errores, así que solo se ve al ejecutar.
2. JavaFX 25 no trae plataforma *headless*, y Monocle (TestFX) solo se publica hasta 21.0.2. No hay forma soportada de ejecutar el toolkit sin pantalla.
3. El plugin `org.openjfx.javafxplugin` resuelve solo la plataforma del equipo que compila; la verificación de dependencias (`verification-metadata.xml`) necesita los jars de todas las plataformas para que la CI de Linux y macOS no falle.

## Decisión
- **JavaFX 25.0.x (LTS)** con los jars con *classifier* declarados a mano por plataforma (`win`, `linux`, `linux-aarch64`, `mac`, `mac-aarch64`), sin el plugin de OpenJFX. Una configuración auxiliar resuelve todas las plataformas para registrar sus hashes.
- **AtlantaFX 2.1.0** (compatible con JavaFX 17 o superior; el mismo `ModalPane` y los temas Primer). Se pasará a 3.x cuando el proyecto adopte JavaFX 27.
- **Pruebas en dos niveles:**
  - La lógica está en ViewModels que solo usan `javafx.base` (propiedades y colecciones) y `TreeItem`; se prueban con JUnit sin toolkit.
  - Lo que necesita el toolkit (`PasswordField`, portapapeles, `Task`, hilo de JavaFX) va en `ToolkitFxTest`, que llama a `Platform.startup` y se omite si no hay pantalla. En CI de Linux corre bajo `xvfb-run`; Windows y macOS tienen pantalla.
  - Lo visual (diálogos nativos, arrastrar y soltar, teclado) se cubre con la lista manual de [PRUEBAS_UI.md](../PRUEBAS_UI.md).
- La guardia `HiloFx.exigirFuera()` compara el nombre del hilo (`JavaFX Application Thread`) en lugar de llamar a `Platform.isFxApplicationThread()`, que carga el toolkit gráfico.

## Consecuencias
- Antes de subir AtlantaFX hay que comprobar contra qué versión de OpenJFX se compiló y ejecutar la interfaz (o `generarCapturas`), porque compilar no detecta el problema.
- En la ruta de módulos los recursos deben vivir dentro del módulo: `cryptokipu-ui` escribe FXML, CSS y textos junto a las clases.
- Si la CI de Linux se ejecuta sin `xvfb-run`, `ToolkitFxTest` aparece como omitida, no como fallida: revisar el número de pruebas omitidas.
- **Alternativa futura:** JavaFX 26 incorpora en `javafx.graphics` una plataforma *headless* (prototipo, `-Dglass.platform=headless`) que no requiere Monocle. Al pasar a JavaFX 26 o una versión posterior (junto con AtlantaFX 3.x), `ToolkitFxTest` podría ejecutarse sin Xvfb. No se adopta ahora porque JavaFX 25 es LTS y esa plataforma aún es un prototipo.
