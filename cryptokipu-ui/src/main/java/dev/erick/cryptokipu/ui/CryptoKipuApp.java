package dev.erick.cryptokipu.ui;

import static dev.erick.cryptokipu.ui.Textos.t;

import dev.erick.cryptokipu.infra.configuracion.Contenedor;
import dev.erick.cryptokipu.infra.configuracion.ModoPortatil;
import dev.erick.cryptokipu.infra.configuracion.PreferenciasStore;
import dev.erick.cryptokipu.infra.storage.ContenedorKipu;
import dev.erick.cryptokipu.ui.comun.DetectorBloqueos;
import dev.erick.cryptokipu.ui.comun.Tema;
import dev.erick.cryptokipu.ui.viewmodel.HiloFx;
import dev.erick.cryptokipu.ui.vista.ShellController;
import java.io.IOException;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

/**
 * Aplicación JavaFX. Arranque rápido: solo se carga la vista de inicio; las demás, al usarlas.
 * Cerrar la ventana con una operación en curso: confirmar → cancelar → esperar → cerrar la sesión (clave maestra
 * en cero) → salir.
 */
public final class CryptoKipuApp extends Application {

    private ContenedorUi uiMija;
    private DetectorBloqueos detectorMija;
    /** No es nulo solo en modo portátil: al salir se vacía su carpeta tmp. */
    private ModoPortatil.Portatil portatilMija;

    /** Arma el contenedor de la interfaz (también lo usa el generador de capturas). */
    public static ContenedorUi componer(PreferenciasStore preferenciasMija) throws IOException {
        ContenedorUi uiMija = new ContenedorUi(new Contenedor(ContenedorKipu.Opciones.porDefecto(), preferenciasMija),
                preferenciasMija);
        uiMija.ajustes().cargar();
        Textos.usarIdioma(uiMija.ajustes().idiomaProperty().get());
        Tema.aplicar(uiMija.ajustes().temaProperty().get());
        return uiMija;
    }

    /** Escena principal con el shell y la hoja de estilos de la aplicación. */
    public static Scene escena(ContenedorUi uiMija) {
        ContenedorUi.Cargado<ShellController> shellMija = uiMija.cargar("shell");
        Scene escenaMija = new Scene(shellMija.raizMija(), 1100, 720);
        escenaMija.getStylesheets().add(Objects.requireNonNull(
                CryptoKipuApp.class.getResource("vista/app.css")).toExternalForm());
        return escenaMija;
    }

    @Override
    public void start(Stage ventanaMija) throws IOException {
        HiloFx.toolkitIniciado();
        ImageIO.setUseCache(false); // la decodificación de imágenes nunca crea archivos de caché
        uiMija = componer(preferenciasDelModo());
        uiMija.usarVentana(ventanaMija);
        uiMija.usarAbridorExterno(archivoMija -> getHostServices().showDocument(archivoMija.toUri().toString()));
        ventanaMija.setTitle(t("app.titulo"));
        ventanaMija.setMinWidth(900);
        ventanaMija.setMinHeight(600);
        ventanaMija.setScene(escena(uiMija));
        ventanaMija.setOnCloseRequest(this::alCerrar);
        ventanaMija.iconifiedProperty().addListener((_, _, minimizadaMija) -> {
            if (minimizadaMija) {
                uiMija.autoBloqueo().alMinimizar(); // si está activado: bloquea (espera si hay algo en curso)
            }
        });
        ventanaMija.show();
        medirArranque();
        if (DetectorBloqueos.activado()) {
            detectorMija = new DetectorBloqueos();
            detectorMija.start();
        }
    }

    /**
     * Medición del arranque en frío (prueba de rendimiento de la Fase 9): con {@code -Dcryptokipu.arranqueArchivo=<ruta>}
     * la aplicación escribe ese archivo cuando la ventana dibuja su primer cuadro y sale. No hace nada sin la propiedad.
     */
    private static void medirArranque() {
        String archivoMija = System.getProperty("cryptokipu.arranqueArchivo");
        if (archivoMija == null) {
            return;
        }
        new AnimationTimer() {
            @Override
            public void handle(long ahoraMija) {
                stop();
                try {
                    Files.writeString(Path.of(archivoMija), "listo");
                } catch (IOException _) {
                    // La prueba detecta la ausencia del archivo.
                }
                Platform.exit();
            }
        }.start();
    }

    /**
     * Modo portátil (archivo {@code portable.properties} junto a la app): preferencias y recientes junto a la app o
     * en ningún lado. {@code -Dcryptokipu.portatil=<carpeta>} fuerza una carpeta (desarrollo y pruebas).
     */
    private PreferenciasStore preferenciasDelModo() throws IOException {
        String forzadaMija = System.getProperty("cryptokipu.portatil");
        if (forzadaMija != null) {
            return PreferenciasStore.portatil(Path.of(forzadaMija));
        }
        portatilMija = ModoPortatil.detectar().orElse(null);
        if (portatilMija == null) {
            return PreferenciasStore.delUsuario();
        }
        portatilMija.prepararCarpetas();
        return portatilMija.preferencias();
    }

    private void alCerrar(WindowEvent eventoMija) {
        if (!uiMija.ejecutor().enCurso()) {
            uiMija.estado().cerrar();
            return;
        }
        eventoMija.consume();
        Alert confirmacionMija = new Alert(Alert.AlertType.CONFIRMATION, t("cerrar.pregunta"), ButtonType.YES,
                ButtonType.NO);
        confirmacionMija.setHeaderText(t("cerrar.titulo"));
        confirmacionMija.initOwner(uiMija.ventana());
        if (confirmacionMija.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) {
            return;
        }
        Thread.ofVirtual().start(() -> {
            try {
                uiMija.ejecutor().cancelarYEsperar(Duration.ofSeconds(60));
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
            }
            Platform.runLater(() -> {
                uiMija.estado().cerrar();
                Platform.exit();
            });
        });
    }

    @Override
    public void stop() {
        if (detectorMija != null) {
            detectorMija.stop();
        }
        if (uiMija != null) {
            uiMija.estado().cerrar(); // también limpia el portapapeles si todavía contiene un token copiado
            uiMija.guardiaPortapapeles().alBloquearOSalir();
        }
        if (portatilMija != null) {
            portatilMija.vaciarTemporales();
        }
    }
}
