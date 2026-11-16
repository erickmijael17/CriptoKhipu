package dev.erick.cryptokipu.ui.viewmodel;

import static dev.erick.cryptokipu.ui.Textos.t;

import dev.erick.cryptokipu.app.casos.DesbloquearBoveda;
import dev.erick.cryptokipu.app.casos.InspeccionarBoveda;
import dev.erick.cryptokipu.app.casos.RecuperarConToken;
import dev.erick.cryptokipu.app.casos.RetardoIntentos;
import dev.erick.cryptokipu.app.casos.SesionBoveda;
import dev.erick.cryptokipu.app.mensajes.CatalogoMensajes;
import dev.erick.cryptokipu.core.auth.Credenciales;
import dev.erick.cryptokipu.core.auth.TokenRecuperacion;
import dev.erick.cryptokipu.core.error.ErrorBoveda;
import dev.erick.cryptokipu.core.error.ExcepcionBoveda;
import dev.erick.cryptokipu.infra.autenticacion.LlaveSeguridad;
import dev.erick.cryptokipu.infra.configuracion.Contenedor;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Desbloqueo con F1–F4 o con token + F1. {@link InspeccionarBoveda} decide si se pide la llave (si CUALQUIER copia
 * del header la requiere). Al desbloquear no se valida nada: siempre se deriva y cualquier error de credenciales
 * produce el mismo mensaje del catálogo.
 */
public final class DesbloqueoViewModel {

    private final Contenedor appMija;
    private final ObjectProperty<Path> rutaMija = new SimpleObjectProperty<>();
    private final BooleanProperty requiereLlaveMija = new SimpleBooleanProperty();
    private final BooleanProperty modoTokenMija = new SimpleBooleanProperty();
    private final ObjectProperty<Path> rutaLlaveMija = new SimpleObjectProperty<>();
    private final StringProperty avisoIntentosMija = new SimpleStringProperty("");
    private final StringProperty mensajeErrorMija = new SimpleStringProperty("");
    private final StringProperty avisoMemoriaMija = new SimpleStringProperty("");
    private final StringProperty avisoEsperaMija = new SimpleStringProperty("");
    private final IntegerProperty segundosEsperaMija = new SimpleIntegerProperty();
    private final BooleanProperty destruidaMija = new SimpleBooleanProperty();
    private final LongSupplier memoriaLibreMija;
    private final LongSupplier relojNanosMija;
    /** Un contador por bóveda, solo en memoria (Fase 8): reiniciar la aplicación lo reinicia. */
    private final Map<Path, RetardoIntentos> retardosMija = new HashMap<>();

    public DesbloqueoViewModel(Contenedor appMija) {
        this(appMija, MemoriaDisponible::bytes);
    }

    /** @param memoriaLibreMija bytes que Argon2id podría usar ahora (se sustituye en pruebas) */
    public DesbloqueoViewModel(Contenedor appMija, LongSupplier memoriaLibreMija) {
        this(appMija, memoriaLibreMija, System::nanoTime);
    }

    /** @param relojNanosMija reloj monótono para el retardo entre intentos (se sustituye en pruebas) */
    public DesbloqueoViewModel(Contenedor appMija, LongSupplier memoriaLibreMija, LongSupplier relojNanosMija) {
        this.appMija = Objects.requireNonNull(appMija, "appMija");
        this.memoriaLibreMija = Objects.requireNonNull(memoriaLibreMija, "memoriaLibreMija");
        this.relojNanosMija = Objects.requireNonNull(relojNanosMija, "relojNanosMija");
    }

    // ------------------------------------------------------------------ retardo entre intentos (Fase 8)

    private synchronized RetardoIntentos retardo() {
        Path actualMija = rutaMija.get();
        Path claveMija = (actualMija == null ? Path.of("") : actualMija).toAbsolutePath().normalize();
        return retardosMija.computeIfAbsent(claveMija, _ -> new RetardoIntentos(relojNanosMija));
    }

    /** Hilo de JavaFX, una vez por segundo y tras cada fallo: actualiza la cuenta regresiva visible. */
    public void actualizarEspera() {
        long segundosMija = retardo().segundosRestantes();
        segundosEsperaMija.set((int) segundosMija);
        avisoEsperaMija.set(segundosMija > 0 ? t("desbloqueo.espera", segundosMija) : "");
    }

    public boolean puedeIntentar() {
        return retardo().puedeIntentar();
    }

    private void exigirSinEspera() {
        if (!retardo().puedeIntentar()) {
            throw new IllegalStateException("En espera tras un intento fallido");
        }
    }

    /** Solo una credencial incorrecta cuenta (nunca se sabe cuál factor falló); un error de E/S o de tipeo, no. */
    private SesionBoveda conRetardo(Intento intentoMija) throws IOException {
        RetardoIntentos retardoMija = retardo();
        try {
            SesionBoveda sesionMija = intentoMija.ejecutar();
            retardoMija.registrarExito();
            return sesionMija;
        } catch (ExcepcionBoveda errorMija) {
            if (errorMija.error() instanceof ErrorBoveda.CredencialesIncorrectas) {
                retardoMija.registrarFallo();
            }
            throw errorMija;
        }
    }

    @FunctionalInterface
    private interface Intento {
        SesionBoveda ejecutar() throws IOException;
    }

    /** Fuera del hilo de JavaFX: abre, lee los headers y cierra. */
    public InspeccionarBoveda.Resultado inspeccionar() throws IOException {
        HiloFx.exigirFuera();
        return appMija.inspeccionarBoveda().ejecutar(new InspeccionarBoveda.Comando(rutaMija.get()));
    }

    /** En el hilo de JavaFX: aplica lo que devolvió {@link #inspeccionar()}. */
    public void aplicar(InspeccionarBoveda.Resultado infoMija) {
        requiereLlaveMija.set(infoMija.requiereLlaveMija());
        int restantesMija = infoMija.intentosRestantes();
        avisoIntentosMija.set(restantesMija < 0 ? "" : t("desbloqueo.intentosRestantes", restantesMija));
        mensajeErrorMija.set(infoMija.destruidaMija() ? t("desbloqueo.destruida") : "");
        destruidaMija.set(infoMija.destruidaMija());
        // Antes de derivar: si este equipo no tiene la RAM que pide el perfil de la bóveda, se avisa (en un equipo
        // con poca memoria Argon2id puede fallar o hacer que el sistema use el archivo de paginación).
        long necesariaMija = infoMija.perfilMija().memoriaBytes() + infoMija.perfilMija().memoriaBytes() / 4;
        long libreMija = memoriaLibreMija.getAsLong();
        avisoMemoriaMija.set(libreMija >= necesariaMija ? "" : t("desbloqueo.memoria",
                necesariaMija / (1024 * 1024), libreMija / (1024 * 1024)));
    }

    public StringProperty avisoEsperaProperty() {
        return avisoEsperaMija;
    }

    /** Segundos que faltan para poder intentar otra vez (0 = ya se puede). */
    public IntegerProperty segundosEsperaProperty() {
        return segundosEsperaMija;
    }

    /** La bóveda fue autodestruida: no hay nada que desbloquear. */
    public BooleanProperty destruidaProperty() {
        return destruidaMija;
    }

    public StringProperty avisoMemoriaProperty() {
        return avisoMemoriaMija;
    }

    /** Fuera del hilo de JavaFX. Toma posesión de los arreglos y los borra. */
    public SesionBoveda desbloquear(char[] f1Mija, char[] f2Mija, char[] f3Mija) throws IOException {
        HiloFx.exigirFuera();
        exigirSinEspera();
        byte[] huellaMija = requiereLlaveMija.get() || rutaLlaveMija.get() != null
                ? LlaveSeguridad.huellaBytes(Objects.requireNonNull(rutaLlaveMija.get(), "llave"), ProgresoOperacion.NINGUNO_MIJA)
                : null;
        try (Credenciales credencialesMija = new Credenciales(f1Mija, f2Mija, f3Mija, huellaMija)) {
            return conRetardo(() -> appMija.desbloquearBoveda()
                    .ejecutar(new DesbloquearBoveda.Comando(rutaMija.get(), credencialesMija)));
        }
    }

    /** Fuera del hilo de JavaFX: token + F1. La sesión resultante queda en rotación obligatoria. */
    public SesionBoveda recuperar(char[] tokenEscritoMija, char[] f1Mija) throws IOException {
        HiloFx.exigirFuera();
        try (TokenRecuperacion tokenMija = leerTokenSinEspera(tokenEscritoMija)) {
            return conRetardo(() -> appMija.recuperarConToken()
                    .ejecutar(new RecuperarConToken.Comando(rutaMija.get(), tokenMija, f1Mija)));
        } finally {
            Arrays.fill(tokenEscritoMija, '\0');
            Arrays.fill(f1Mija, '\0');
        }
    }

    /** La espera manda antes que cualquier otra comprobación del token. */
    private TokenRecuperacion leerTokenSinEspera(char[] tokenEscritoMija) {
        exigirSinEspera();
        return TokenRecuperacion.leer(tokenEscritoMija);
    }

    /** Un único mensaje por tipo de error (del catálogo compartido con la CLI); nunca dice qué factor falló. */
    public void mostrarError(Throwable errorMija) {
        mensajeErrorMija.set(mensaje(errorMija));
    }

    public static String mensaje(Throwable errorMija) {
        if (errorMija instanceof ExcepcionBoveda bovedaMija) {
            return CatalogoMensajes.mensaje(bovedaMija.error());
        }
        return t("error.general");
    }

    public ObjectProperty<Path> rutaProperty() {
        return rutaMija;
    }

    public BooleanProperty requiereLlaveProperty() {
        return requiereLlaveMija;
    }

    public BooleanProperty modoTokenProperty() {
        return modoTokenMija;
    }

    public ObjectProperty<Path> rutaLlaveProperty() {
        return rutaLlaveMija;
    }

    public StringProperty avisoIntentosProperty() {
        return avisoIntentosMija;
    }

    public StringProperty mensajeErrorProperty() {
        return mensajeErrorMija;
    }
}
