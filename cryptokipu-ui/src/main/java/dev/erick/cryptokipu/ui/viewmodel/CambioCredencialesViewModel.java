package dev.erick.cryptokipu.ui.viewmodel;

import static dev.erick.cryptokipu.ui.Textos.t;

import dev.erick.cryptokipu.app.casos.RotarCredenciales;
import dev.erick.cryptokipu.app.casos.SesionBoveda;
import dev.erick.cryptokipu.core.auth.CambioCredenciales;
import dev.erick.cryptokipu.core.auth.Credenciales;
import dev.erick.cryptokipu.core.auth.TokenRecuperacion;
import dev.erick.cryptokipu.infra.autenticacion.LlaveSeguridad;
import dev.erick.cryptokipu.infra.configuracion.Contenedor;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;

/**
 * Cambio de credenciales, por factor ({@link CambioCredenciales}). Sirve para dos pantallas:
 *
 * <ul>
 *   <li><b>Rotación obligatoria</b> (tras usar un token): pide F1, F2, F3 y la decisión sobre la llave; no se puede
 *       cerrar hasta terminar ({@link #puedeCerrarProperty()}).</li>
 *   <li><b>Seguridad</b>: el usuario marca qué factores cambian y confirma con sus credenciales vigentes.</li>
 * </ul>
 */
public final class CambioCredencialesViewModel {

    public enum DecisionLlave { SIN_CAMBIO, NUEVA, GENERAR, QUITAR }

    private final Contenedor appMija;
    private final boolean obligatoriaMija;
    private final BooleanProperty cambiaF1Mija = new SimpleBooleanProperty();
    private final BooleanProperty cambiaF2Mija = new SimpleBooleanProperty();
    private final BooleanProperty cambiaF3Mija = new SimpleBooleanProperty();
    private final ObjectProperty<DecisionLlave> llaveMija = new SimpleObjectProperty<>(DecisionLlave.SIN_CAMBIO);
    private final ObjectProperty<Path> rutaLlaveMija = new SimpleObjectProperty<>();
    private final ReadOnlyBooleanWrapper puedeCerrarMija;
    private final ValidacionViva validacionF1Mija = new ValidacionViva(ValidacionViva.Factor.F1);
    private final ValidacionViva validacionF2Mija = new ValidacionViva(ValidacionViva.Factor.F2);
    private final ValidacionViva validacionF3Mija = new ValidacionViva(ValidacionViva.Factor.F3);

    /** @param obligatoriaMija {@code true} en la rotación tras un token: todos los factores cambian y no se puede saltar */
    public CambioCredencialesViewModel(Contenedor appMija, boolean obligatoriaMija) {
        this.appMija = Objects.requireNonNull(appMija, "appMija");
        this.obligatoriaMija = obligatoriaMija;
        this.puedeCerrarMija = new ReadOnlyBooleanWrapper(!obligatoriaMija);
        if (obligatoriaMija) {
            cambiaF1Mija.set(true);
            cambiaF2Mija.set(true);
            cambiaF3Mija.set(true);
            llaveMija.set(DecisionLlave.QUITAR);
        }
    }

    /**
     * Fuera del hilo de JavaFX. {@code nuevosMija}: valores nuevos de F1, F2, F3 (null si ese factor no cambia);
     * {@code vigentesMija}: credenciales actuales (vacío en la rotación obligatoria). Devuelve los tokens nuevos
     * (vacío si F1 no cambió).
     */
    public List<TokenRecuperacion> rotar(SesionBoveda sesionMija, Optional<Credenciales> vigentesMija, char[] f1Mija,
            char[] f2Mija, char[] f3Mija) throws IOException {
        HiloFx.exigirFuera();
        if (obligatoriaMija && (f1Mija == null || f2Mija == null || f3Mija == null
                || llaveMija.get() == DecisionLlave.SIN_CAMBIO)) {
            throw new IllegalArgumentException(t("error.factoresIncompletos"));
        }
        CambioCredenciales cambioMija = new CambioCredenciales(Optional.ofNullable(f1Mija), Optional.ofNullable(f2Mija),
                Optional.ofNullable(f3Mija), cambioLlave(), OptionalInt.empty());
        try (cambioMija) {
            return appMija.rotarCredenciales().ejecutar(new RotarCredenciales.Comando(sesionMija, vigentesMija,
                    cambioMija)).tokensNuevosMija();
        }
    }

    /** En el hilo de JavaFX, tras rotar con éxito. */
    public void rotacionTerminada() {
        puedeCerrarMija.set(true);
    }

    /** Pedido de cerrar la pantalla: en la rotación obligatoria se rechaza hasta terminar. */
    public boolean solicitarCierre() {
        return puedeCerrarMija.get();
    }

    private CambioCredenciales.CambioLlave cambioLlave() throws IOException {
        return switch (llaveMija.get()) {
            case SIN_CAMBIO -> new CambioCredenciales.CambioLlave.SinCambio();
            case QUITAR -> new CambioCredenciales.CambioLlave.Quitar();
            case NUEVA -> new CambioCredenciales.CambioLlave.Nueva(
                    LlaveSeguridad.huellaBytes(rutaLlaveMija.get(), ProgresoOperacion.NINGUNO_MIJA));
            case GENERAR -> {
                LlaveSeguridad.generar(rutaLlaveMija.get());
                yield new CambioCredenciales.CambioLlave.Nueva(
                        LlaveSeguridad.huellaBytes(rutaLlaveMija.get(), ProgresoOperacion.NINGUNO_MIJA));
            }
        };
    }

    /** Borra arreglos que no llegaron a usarse. */
    public static void borrar(char[]... arreglosMija) {
        for (char[] arregloMija : arreglosMija) {
            if (arregloMija != null) {
                Arrays.fill(arregloMija, '\0');
            }
        }
    }

    public boolean esObligatoria() {
        return obligatoriaMija;
    }

    public ValidacionViva validacion(ValidacionViva.Factor factorMija) {
        return switch (factorMija) {
            case F1 -> validacionF1Mija;
            case F2 -> validacionF2Mija;
            case F3 -> validacionF3Mija;
        };
    }

    public BooleanProperty cambiaF1Property() {
        return cambiaF1Mija;
    }

    public BooleanProperty cambiaF2Property() {
        return cambiaF2Mija;
    }

    public BooleanProperty cambiaF3Property() {
        return cambiaF3Mija;
    }

    public ObjectProperty<DecisionLlave> llaveProperty() {
        return llaveMija;
    }

    public ObjectProperty<Path> rutaLlaveProperty() {
        return rutaLlaveMija;
    }

    public ReadOnlyBooleanProperty puedeCerrarProperty() {
        return puedeCerrarMija.getReadOnlyProperty();
    }
}
