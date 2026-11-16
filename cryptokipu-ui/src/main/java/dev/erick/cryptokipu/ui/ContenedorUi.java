package dev.erick.cryptokipu.ui;

import dev.erick.cryptokipu.app.puertos.VisorDocumentos;
import dev.erick.cryptokipu.infra.configuracion.Contenedor;
import dev.erick.cryptokipu.infra.configuracion.PreferenciasStore;
import dev.erick.cryptokipu.infra.storage.PoliticaDestino;
import dev.erick.cryptokipu.ui.comun.EjecutorTareas;
import dev.erick.cryptokipu.ui.comun.Navegador;
import dev.erick.cryptokipu.ui.comun.PortapapelesFx;
import dev.erick.cryptokipu.ui.viewmodel.AjustesViewModel;
import dev.erick.cryptokipu.ui.viewmodel.AsistenteCreacionViewModel;
import dev.erick.cryptokipu.ui.viewmodel.AutoBloqueo;
import dev.erick.cryptokipu.ui.viewmodel.DesbloqueoViewModel;
import dev.erick.cryptokipu.ui.viewmodel.EstadoApp;
import dev.erick.cryptokipu.ui.viewmodel.ExploradorViewModel;
import dev.erick.cryptokipu.ui.viewmodel.GuardiaPortapapeles;
import dev.erick.cryptokipu.ui.viewmodel.SeguridadViewModel;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.nio.file.Path;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.function.Consumer;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.stage.Window;

/**
 * Composición manual de la interfaz (sin Spring): un único lugar crea los ViewModels y entrega los controladores a
 * {@link FXMLLoader} con {@code loader.setControllerFactory(contenedorMija::obtener)}, como en la referencia.
 */
public final class ContenedorUi {

    /** Vista FXML cargada con su controlador. */
    public record Cargado<C>(Parent raizMija, C controladorMija) {
    }

    private final Contenedor appMija;
    private final PreferenciasStore preferenciasMija;
    private final EstadoApp estadoMija;
    private final AjustesViewModel ajustesMija;
    private final DesbloqueoViewModel desbloqueoMija;
    private final AsistenteCreacionViewModel asistenteMija;
    private final ExploradorViewModel exploradorMija;
    private final SeguridadViewModel seguridadMija;
    private final PortapapelesFx portapapelesMija = new PortapapelesFx();
    private final GuardiaPortapapeles guardiaMija = new GuardiaPortapapeles(portapapelesMija, portapapelesMija);
    private final AutoBloqueo autoBloqueoMija;
    /** Pantallas de tokens abiertas: mientras haya una, no se bloquea (los tokens aún no se guardaron). */
    private int tokensPendientesMija;
    private EjecutorTareas ejecutorMija;
    private Navegador navegadorMija;
    private Window ventanaMija;
    private Consumer<Path> abridorExternoMija = _ -> {
        throw new IllegalStateException("Sin aplicación externa configurada");
    };

    public ContenedorUi(Contenedor appMija, PreferenciasStore preferenciasMija) {
        this.appMija = Objects.requireNonNull(appMija, "appMija");
        this.preferenciasMija = Objects.requireNonNull(preferenciasMija, "preferenciasMija");
        this.estadoMija = new EstadoApp(appMija);
        this.ajustesMija = new AjustesViewModel(preferenciasMija);
        this.desbloqueoMija = new DesbloqueoViewModel(appMija);
        this.asistenteMija = new AsistenteCreacionViewModel(appMija, PoliticaDestino.delSistema());
        this.exploradorMija = new ExploradorViewModel(appMija,
                ServiceLoader.load(VisorDocumentos.class).stream().map(ServiceLoader.Provider::get).toList());
        this.seguridadMija = new SeguridadViewModel(appMija);
        estadoMija.alCerrar(guardiaMija::alBloquearOSalir);
        this.autoBloqueoMija = new AutoBloqueo(estadoMija,
                () -> (ejecutorMija != null && ejecutorMija.enCurso()) || tokensPendientesMija > 0,
                () -> System.nanoTime() / 1_000_000L);
        autoBloqueoMija.usarMinutos(ajustesMija.autoBloqueoMinutosProperty().get());
        autoBloqueoMija.bloquearAlMinimizar(ajustesMija.bloquearAlMinimizarProperty().get());
        ajustesMija.autoBloqueoMinutosProperty().addListener((_, _, minutosMija) ->
                autoBloqueoMija.usarMinutos(minutosMija.intValue()));
        ajustesMija.bloquearAlMinimizarProperty().addListener((_, _, activoMija) ->
                autoBloqueoMija.bloquearAlMinimizar(activoMija));
    }

    /** {@code controllerFactory}: los controladores reciben este contenedor por constructor. */
    public Object obtener(Class<?> claseMija) {
        try {
            try {
                Constructor<?> constructorMija = claseMija.getDeclaredConstructor(ContenedorUi.class);
                return constructorMija.newInstance(this);
            } catch (NoSuchMethodException _) {
                return claseMija.getDeclaredConstructor().newInstance();
            }
        } catch (ReflectiveOperationException errorMija) {
            throw new IllegalStateException("No se pudo crear " + claseMija.getSimpleName(), errorMija);
        }
    }

    /** Carga {@code vista/<nombre>.fxml} con los textos del idioma activo. */
    public <C> Cargado<C> cargar(String nombreMija) {
        URL fxmlMija = ContenedorUi.class.getResource("vista/" + nombreMija + ".fxml");
        FXMLLoader cargadorMija = new FXMLLoader(Objects.requireNonNull(fxmlMija, nombreMija), Textos.paquete());
        cargadorMija.setControllerFactory(this::obtener);
        try {
            Parent raizMija = cargadorMija.load();
            return new Cargado<>(raizMija, cargadorMija.getController());
        } catch (IOException errorMija) {
            throw new UncheckedIOException(errorMija);
        }
    }

    public Contenedor app() {
        return appMija;
    }

    public PreferenciasStore preferencias() {
        return preferenciasMija;
    }

    public EstadoApp estado() {
        return estadoMija;
    }

    public AjustesViewModel ajustes() {
        return ajustesMija;
    }

    public DesbloqueoViewModel desbloqueo() {
        return desbloqueoMija;
    }

    public AsistenteCreacionViewModel asistente() {
        return asistenteMija;
    }

    public ExploradorViewModel explorador() {
        return exploradorMija;
    }

    public SeguridadViewModel seguridad() {
        return seguridadMija;
    }

    public AutoBloqueo autoBloqueo() {
        return autoBloqueoMija;
    }

    public GuardiaPortapapeles guardiaPortapapeles() {
        return guardiaMija;
    }

    public PortapapelesFx portapapeles() {
        return portapapelesMija;
    }

    /** Una pantalla de tokens se abrió ({@code true}) o se cerró ({@code false}): el auto-bloqueo espera. */
    public void tokensPendientes(boolean abiertaMija) {
        tokensPendientesMija = Math.max(0, tokensPendientesMija + (abiertaMija ? 1 : -1));
    }

    public EjecutorTareas ejecutor() {
        return Objects.requireNonNull(ejecutorMija, "ejecutor");
    }

    public void usarEjecutor(EjecutorTareas nuevoMija) {
        this.ejecutorMija = nuevoMija;
    }

    public Navegador navegador() {
        return Objects.requireNonNull(navegadorMija, "navegador");
    }

    public void usarNavegador(Navegador nuevoMija) {
        this.navegadorMija = nuevoMija;
    }

    /** Abre un archivo con la aplicación del sistema (la interfaz la fija con {@code HostServices}). */
    public void abrirExterno(Path archivoMija) {
        abridorExternoMija.accept(archivoMija);
    }

    public void usarAbridorExterno(Consumer<Path> abridorMija) {
        this.abridorExternoMija = Objects.requireNonNull(abridorMija, "abridorMija");
    }

    public Window ventana() {
        return ventanaMija;
    }

    public void usarVentana(Window nuevaMija) {
        this.ventanaMija = nuevaMija;
    }
}
