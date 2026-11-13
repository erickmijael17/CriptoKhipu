package dev.erick.cryptokipu.ui.capturas;

import dev.erick.cryptokipu.app.casos.SesionBoveda;
import dev.erick.cryptokipu.core.auth.TokenRecuperacion;
import dev.erick.cryptokipu.core.registro.Registro;
import dev.erick.cryptokipu.infra.configuracion.PreferenciasStore;
import dev.erick.cryptokipu.ui.ContenedorUi;
import dev.erick.cryptokipu.ui.CryptoKipuApp;
import dev.erick.cryptokipu.ui.comun.Navegador;
import dev.erick.cryptokipu.ui.comun.Tema;
import dev.erick.cryptokipu.ui.viewmodel.AjustesViewModel;
import dev.erick.cryptokipu.ui.viewmodel.AsistenteCreacionViewModel;
import dev.erick.cryptokipu.ui.viewmodel.ExploradorViewModel.Nodo;
import dev.erick.cryptokipu.ui.viewmodel.HiloFx;
import dev.erick.cryptokipu.ui.viewmodel.PerfilUi;
import dev.erick.cryptokipu.ui.viewmodel.ProgresoOperacion;
import dev.erick.cryptokipu.ui.viewmodel.ValidacionViva;
import dev.erick.cryptokipu.ui.vista.TokensController;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableView;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Genera las capturas de cada pantalla para el README ({@code ./gradlew :cryptokipu-ui:generarCapturas}). Usa
 * preferencias y bóveda temporales con credenciales de demostración (nunca reales) y el perfil Ligero para que sea
 * rápido. Las acciones se hacen a través de los ViewModels, igual que lo haría la vista.
 */
public final class GeneradorCapturas extends Application {

    private static final char[] F1_DEMO_MIJA = "482915736028".toCharArray();
    private static final char[] F2_DEMO_MIJA = "Kipu2Demo2026".toCharArray();
    private static final char[] F3_DEMO_MIJA = "el condor pasa el rio".toCharArray();

    /** Un paso: hace algo y avisa cuando terminó; luego se espera y, si tiene nombre, se captura. */
    private record Paso(String capturaMija, Consumer<Runnable> accionMija) {
    }

    private static final System.Logger REGISTRO_MIJA = Registro.para(GeneradorCapturas.class);

    private final Deque<Paso> pasosMija = new ArrayDeque<>();
    private Path salidaMija;
    private Path temporalMija;
    private Scene escenaMija;
    private ContenedorUi uiMija;
    private Path bovedaMija;
    private char[] tokenGuardadoMija;

    public static void main(String[] argumentosMija) {
        launch(argumentosMija);
    }

    @Override
    public void start(Stage ventanaMija) throws IOException {
        HiloFx.toolkitIniciado();
        salidaMija = Path.of(getParameters().getRaw().getFirst());
        Files.createDirectories(salidaMija);
        temporalMija = Files.createTempDirectory("cryptokipu-capturas");
        uiMija = CryptoKipuApp.componer(PreferenciasStore.portatil(temporalMija.resolve("preferencias")));
        Tema.aplicar(AjustesViewModel.Tema.CLARO);
        uiMija.usarVentana(ventanaMija);
        escenaMija = CryptoKipuApp.escena(uiMija);
        ventanaMija.setTitle("CryptoKipu");
        ventanaMija.setScene(escenaMija);
        ventanaMija.show();
        List<Path> muestrasMija = crearMuestras(temporalMija.resolve("muestras"));
        Files.createDirectories(temporalMija.resolve("OneDrive"));
        bovedaMija = temporalMija.resolve("OneDrive").resolve("mis-documentos.kipu");
        programar(muestrasMija);
        siguiente();
    }

    private void programar(List<Path> muestrasMija) {
        AsistenteCreacionViewModel asistenteMija = uiMija.asistente();
        sincrono("01-inicio", () -> { });
        sincrono("02-asistente-archivos", () -> {
            uiMija.navegador().mostrar(Navegador.Vista.ASISTENTE);
            asistenteMija.archivos().setAll(muestrasMija);
        });
        sincrono("03-asistente-validacion", () -> {
            asistenteMija.pasoProperty().set(AsistenteCreacionViewModel.Paso.F2);
            campoVisible().setText("kipu2demo");
        });
        sincrono("04-asistente-perfil", () -> {
            campoVisible().clear();
            asistenteMija.fijarSecreto(ValidacionViva.Factor.F1, F1_DEMO_MIJA.clone(), F1_DEMO_MIJA.clone())
                    .ifPresent(errorMija -> {
                        throw new IllegalStateException(errorMija);
                    });
            asistenteMija.fijarSecreto(ValidacionViva.Factor.F2, F2_DEMO_MIJA.clone(), F2_DEMO_MIJA.clone())
                    .ifPresent(errorMija -> {
                        throw new IllegalStateException(errorMija);
                    });
            asistenteMija.fijarSecreto(ValidacionViva.Factor.F3, F3_DEMO_MIJA.clone(), F3_DEMO_MIJA.clone())
                    .ifPresent(errorMija -> {
                        throw new IllegalStateException(errorMija);
                    });
            asistenteMija.perfilProperty().set(PerfilUi.LIGERO);
            asistenteMija.pasoProperty().set(AsistenteCreacionViewModel.Paso.PERFIL);
        });
        sincrono("05-asistente-destino", () -> {
            asistenteMija.destinoProperty().set(bovedaMija);
            asistenteMija.evaluarDestino();
            asistenteMija.pasoProperty().set(AsistenteCreacionViewModel.Paso.DESTINO);
        });
        sincrono("06-asistente-resumen", () -> asistenteMija.pasoProperty().set(AsistenteCreacionViewModel.Paso.RESUMEN));
        pasosMija.add(new Paso("07-tokens", listoMija -> enSegundoPlano(
                () -> asistenteMija.crear(ProgresoOperacion.NINGUNO_MIJA), creadaMija -> {
                    tokenGuardadoMija = creadaMija.tokensMija().getFirst().formatear();
                    uiMija.estado().abrir(creadaMija.rutaMija(), creadaMija.sesionMija());
                    TokensController.mostrar(uiMija, creadaMija.tokensMija(), bovedaMija.getFileName().toString(),
                            () -> { });
                    listoMija.run();
                })));
        pasosMija.add(new Paso("08-explorador", listoMija -> {
            uiMija.navegador().cerrarModal();
            uiMija.navegador().mostrar(Navegador.Vista.EXPLORADOR);
            seleccionarFoto();
            esperar(1500, listoMija);
        }));
        sincrono("09-seguridad", () -> uiMija.navegador().mostrar(Navegador.Vista.SEGURIDAD));
        sincrono("10-ajustes", () -> uiMija.navegador().mostrar(Navegador.Vista.AJUSTES));
        pasosMija.add(new Paso("11-desbloqueo", listoMija -> {
            uiMija.estado().cerrar();
            uiMija.desbloqueo().rutaProperty().set(bovedaMija);
            uiMija.navegador().mostrar(Navegador.Vista.DESBLOQUEO);
            esperar(1500, listoMija);
        }));
        sincrono("12-desbloqueo-token", () -> uiMija.desbloqueo().modoTokenProperty().set(true));
        pasosMija.add(new Paso("13-rotacion-obligatoria", listoMija -> enSegundoPlano(
                () -> uiMija.desbloqueo().recuperar(tokenGuardadoMija, F1_DEMO_MIJA.clone()), (SesionBoveda sesionMija) -> {
                    uiMija.estado().abrir(bovedaMija, sesionMija);
                    listoMija.run();
                })));
        sincrono("14-tema-oscuro", () -> Tema.aplicar(AjustesViewModel.Tema.OSCURO));
        pasosMija.add(new Paso(null, _ -> {
            uiMija.estado().cerrar();
            borrarTemporal();
            Platform.exit();
        }));
    }

    // ------------------------------------------------------------------ ejecución de pasos

    private void sincrono(String capturaMija, Runnable accionMija) {
        pasosMija.add(new Paso(capturaMija, listoMija -> {
            accionMija.run();
            listoMija.run();
        }));
    }

    private void siguiente() {
        Paso pasoMija = pasosMija.poll();
        if (pasoMija == null) {
            return;
        }
        pasoMija.accionMija().accept(() -> esperar(700, () -> {
            if (pasoMija.capturaMija() != null) {
                capturar(pasoMija.capturaMija());
            }
            siguiente();
        }));
    }

    private static void esperar(long milisegundosMija, Runnable despuesMija) {
        PauseTransition pausaMija = new PauseTransition(Duration.millis(milisegundosMija));
        pausaMija.setOnFinished(_ -> despuesMija.run());
        pausaMija.play();
    }

    @FunctionalInterface
    private interface Trabajo<T> {
        T ejecutar() throws Exception;
    }

    private <T> void enSegundoPlano(Trabajo<T> trabajoMija, Consumer<T> alTerminarMija) {
        Thread.ofVirtual().start(() -> {
            try {
                T resultadoMija = trabajoMija.ejecutar();
                Platform.runLater(() -> alTerminarMija.accept(resultadoMija));
            } catch (Exception errorMija) {
                // Solo el tipo de error: el mensaje podría incluir rutas (política de registro).
                REGISTRO_MIJA.log(System.Logger.Level.ERROR, "No se pudieron generar las capturas: {0}",
                        errorMija.getClass().getSimpleName());
                Platform.runLater(() -> {
                    borrarTemporal();
                    Platform.exit();
                });
            }
        });
    }

    private void capturar(String nombreMija) {
        WritableImage imagenMija = escenaMija.snapshot(null);
        try {
            EscritorPng.escribir(imagenMija, salidaMija.resolve(nombreMija + ".png"));
        } catch (IOException errorMija) {
            throw new UncheckedIOException(errorMija);
        }
    }

    // ------------------------------------------------------------------ utilidades

    private PasswordField campoVisible() {
        return escenaMija.getRoot().lookupAll(".password-field").stream()
                .filter(GeneradorCapturas::visibleEnEscena)
                .map(PasswordField.class::cast)
                .findFirst()
                .orElseThrow();
    }

    private static boolean visibleEnEscena(Node nodoMija) {
        for (Node actualMija = nodoMija; actualMija != null; actualMija = actualMija.getParent()) {
            if (!actualMija.isVisible()) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private void seleccionarFoto() {
        TreeTableView<Nodo> tablaMija = (TreeTableView<Nodo>) escenaMija.getRoot().lookup(".tree-table-view");
        for (TreeItem<Nodo> hijoMija : tablaMija.getRoot().getChildren()) {
            if ("paisaje.png".equals(hijoMija.getValue().nombreMija())) {
                tablaMija.getSelectionModel().select(hijoMija);
            }
        }
    }

    private static List<Path> crearMuestras(Path carpetaMija) throws IOException {
        Files.createDirectories(carpetaMija);
        Random aleatorioMija = new Random(7);
        WritableImage fotoMija = new WritableImage(640, 400);
        for (int yMija = 0; yMija < 400; yMija++) {
            for (int xMija = 0; xMija < 640; xMija++) {
                double cieloMija = yMija / 400.0;
                boolean montanaMija = yMija > 240 + 60 * Math.sin(xMija / 70.0);
                fotoMija.getPixelWriter().setColor(xMija, yMija, montanaMija
                        ? Color.color(0.18, 0.35 + 0.2 * cieloMija, 0.22)
                        : Color.color(0.35 + 0.4 * cieloMija, 0.55 + 0.3 * cieloMija, 0.95));
            }
        }
        Path pngMija = carpetaMija.resolve("paisaje.png");
        EscritorPng.escribir(fotoMija, pngMija);
        Path textoMija = carpetaMija.resolve("notas.txt");
        Files.writeString(textoMija, "Notas de la reunión\n- Revisar el presupuesto\n- Enviar el informe\n",
                StandardCharsets.UTF_8);
        return List.of(pngMija, textoMija,
                aleatorio(carpetaMija.resolve("informe.docx"), 48 * 1024, aleatorioMija),
                aleatorio(carpetaMija.resolve("presupuesto.xlsx"), 32 * 1024, aleatorioMija),
                aleatorio(carpetaMija.resolve("viaje.mp4"), 3 * 1024 * 1024, aleatorioMija));
    }

    private static Path aleatorio(Path rutaMija, int tamanoMija, Random aleatorioMija) throws IOException {
        byte[] bytesMija = new byte[tamanoMija];
        aleatorioMija.nextBytes(bytesMija);
        Files.write(rutaMija, bytesMija);
        return rutaMija;
    }

    private void borrarTemporal() {
        if (tokenGuardadoMija != null) {
            Arrays.fill(tokenGuardadoMija, '\0');
        }
        try (Stream<Path> recorridoMija = Files.walk(temporalMija)) {
            recorridoMija.sorted(Comparator.reverseOrder()).forEach(rutaMija -> {
                try {
                    Files.deleteIfExists(rutaMija);
                } catch (IOException _) {
                    // Temporal: el sistema lo limpiará.
                }
            });
        } catch (IOException _) {
            // Temporal: el sistema lo limpiará.
        }
    }
}
