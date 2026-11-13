package dev.erick.cryptokipu.ui.viewmodel;

import static dev.erick.cryptokipu.ui.Textos.t;

import dev.erick.cryptokipu.app.casos.AgregarArchivos;
import dev.erick.cryptokipu.app.casos.CrearBoveda;
import dev.erick.cryptokipu.app.casos.PoliticaColision;
import dev.erick.cryptokipu.app.casos.SesionBoveda;
import dev.erick.cryptokipu.core.auth.Credenciales;
import dev.erick.cryptokipu.core.auth.ReglaIncumplida;
import dev.erick.cryptokipu.core.auth.TokenRecuperacion;
import dev.erick.cryptokipu.core.auth.ValidadorFactores;
import dev.erick.cryptokipu.infra.autenticacion.LlaveSeguridad;
import dev.erick.cryptokipu.infra.configuracion.Contenedor;
import dev.erick.cryptokipu.infra.storage.PoliticaDestino;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Asistente de creación: Archivos → F1 → F2 → F3 → Llave → Perfil → Destino → Resumen; los tokens se muestran
 * después, en su propia pantalla. Los secretos confirmados se guardan como {@code char[]} hasta crear o cancelar.
 */
public final class AsistenteCreacionViewModel {

    public enum Paso { ARCHIVOS, F1, F2, F3, LLAVE, PERFIL, DESTINO, RESUMEN }

    public enum ModoLlave { ARCHIVO, GENERAR, SIN_LLAVE }

    /** Resultado de crear: la sesión abierta y los tokens (se muestran una sola vez). */
    public record Creada(Path rutaMija, SesionBoveda sesionMija, List<TokenRecuperacion> tokensMija,
            AgregarArchivos.Resultado archivosMija) {
    }

    private final Contenedor appMija;
    private final PoliticaDestino politicaMija;
    private final ObjectProperty<Paso> pasoMija = new SimpleObjectProperty<>(Paso.ARCHIVOS);
    private final ObservableList<Path> archivosMija = FXCollections.observableArrayList();
    private final ObjectProperty<ModoLlave> modoLlaveMija = new SimpleObjectProperty<>(ModoLlave.SIN_LLAVE);
    private final ObjectProperty<Path> rutaLlaveMija = new SimpleObjectProperty<>();
    private final ObjectProperty<PerfilUi> perfilMija = new SimpleObjectProperty<>(PerfilUi.ESTANDAR);
    private final ObjectProperty<Path> destinoMija = new SimpleObjectProperty<>();
    private final ObservableList<Aviso> avisosDestinoMija = FXCollections.observableArrayList();
    private final ValidacionViva validacionF1Mija = new ValidacionViva(ValidacionViva.Factor.F1);
    private final ValidacionViva validacionF2Mija = new ValidacionViva(ValidacionViva.Factor.F2);
    private final ValidacionViva validacionF3Mija = new ValidacionViva(ValidacionViva.Factor.F3);
    private char[] f1Mija;
    private char[] f2Mija;
    private char[] f3Mija;

    public AsistenteCreacionViewModel(Contenedor appMija, PoliticaDestino politicaMija) {
        this.appMija = Objects.requireNonNull(appMija, "appMija");
        this.politicaMija = Objects.requireNonNull(politicaMija, "politicaMija");
    }

    // ------------------------------------------------------------------ secretos

    /**
     * Guarda el factor si cumple la política y coincide con su repetición. Toma posesión de {@code valorMija};
     * borra siempre {@code repetidoMija}. Devuelve el motivo del rechazo, si lo hay.
     */
    public Optional<String> fijarSecreto(ValidacionViva.Factor factorMija, char[] valorMija, char[] repetidoMija) {
        try {
            if (!Arrays.equals(valorMija, repetidoMija)) {
                Arrays.fill(valorMija, '\0');
                return Optional.of(t("error.noCoinciden"));
            }
            ValidacionViva validacionMija = validacion(factorMija);
            validacionMija.evaluar(valorMija);
            if (!validacionMija.esValido()) {
                Arrays.fill(valorMija, '\0');
                return Optional.of(t("error.politica"));
            }
            switch (factorMija) {
                case F1 -> f1Mija = reemplazar(f1Mija, valorMija);
                case F2 -> f2Mija = reemplazar(f2Mija, valorMija);
                case F3 -> f3Mija = reemplazar(f3Mija, valorMija);
            }
            return Optional.empty();
        } finally {
            Arrays.fill(repetidoMija, '\0');
        }
    }

    public boolean tieneSecreto(ValidacionViva.Factor factorMija) {
        return switch (factorMija) {
            case F1 -> f1Mija != null;
            case F2 -> f2Mija != null;
            case F3 -> f3Mija != null;
        };
    }

    public ValidacionViva validacion(ValidacionViva.Factor factorMija) {
        return switch (factorMija) {
            case F1 -> validacionF1Mija;
            case F2 -> validacionF2Mija;
            case F3 -> validacionF3Mija;
        };
    }

    // ------------------------------------------------------------------ navegación

    public boolean puedeAvanzar() {
        return switch (pasoMija.get()) {
            case ARCHIVOS, PERFIL, RESUMEN -> true;
            case F1 -> f1Mija != null;
            case F2 -> f2Mija != null;
            case F3 -> f3Mija != null;
            case LLAVE -> modoLlaveMija.get() == ModoLlave.SIN_LLAVE || rutaLlaveMija.get() != null;
            case DESTINO -> destinoMija.get() != null
                    && avisosDestinoMija.stream().noneMatch(Aviso::bloqueanteMija);
        };
    }

    public boolean siguiente() {
        if (!puedeAvanzar() || pasoMija.get() == Paso.RESUMEN) {
            return false;
        }
        pasoMija.set(Paso.values()[pasoMija.get().ordinal() + 1]);
        return true;
    }

    public void atras() {
        if (pasoMija.get().ordinal() > 0) {
            pasoMija.set(Paso.values()[pasoMija.get().ordinal() - 1]);
        }
    }

    /** Recalcula los avisos del destino (FAT32, espacio, carpeta sincronizada, archivo existente). */
    public void evaluarDestino() {
        avisosDestinoMija.clear();
        Path rutaMija = destinoMija.get();
        if (rutaMija == null) {
            return;
        }
        if (Files.exists(rutaMija)) {
            avisosDestinoMija.add(new Aviso(t("aviso.yaExiste"), true));
        }
        avisosDestinoMija.addAll(AvisosDestino.evaluar(rutaMija, tamanoEstimado(), politicaMija));
    }

    /** Suma de los archivos (y carpetas) elegidos más un margen por bloque y metadatos. */
    public long tamanoEstimado() {
        long totalMija = 64 * 1024;
        for (Path archivoMija : archivosMija) {
            try (var recorridoMija = Files.walk(archivoMija)) {
                totalMija += recorridoMija.filter(Files::isRegularFile).mapToLong(rutaMija -> {
                    try {
                        return Files.size(rutaMija);
                    } catch (IOException errorMija) {
                        throw new UncheckedIOException(errorMija);
                    }
                }).sum();
            } catch (IOException | UncheckedIOException _) {
                // Archivo ilegible: lo reportará AgregarArchivos.
            }
        }
        return totalMija + totalMija / (1 << 20) * 16;
    }

    // ------------------------------------------------------------------ creación

    /** Crea la bóveda y agrega los archivos. Fuera del hilo de JavaFX. */
    public Creada crear(ProgresoOperacion progresoMija) throws IOException {
        HiloFx.exigirFuera();
        List<ReglaIncumplida> reglasMija = ValidadorFactores.validar(requerir(f1Mija), requerir(f2Mija), requerir(f3Mija));
        if (!reglasMija.isEmpty()) {
            throw new IllegalStateException("Credenciales sin validar");
        }
        Path rutaMija = Objects.requireNonNull(destinoMija.get(), "destino");
        byte[] huellaMija = null;
        progresoMija.etapa("etapa.llave", true);
        switch (modoLlaveMija.get()) {
            case GENERAR -> {
                LlaveSeguridad.generar(rutaLlaveMija.get());
                huellaMija = LlaveSeguridad.huellaBytes(rutaLlaveMija.get(), progresoMija);
            }
            case ARCHIVO -> huellaMija = LlaveSeguridad.huellaBytes(rutaLlaveMija.get(), progresoMija);
            case SIN_LLAVE -> huellaMija = null;
        }
        progresoMija.etapa("etapa.derivando", false);
        Credenciales credencialesMija = new Credenciales(f1Mija, f2Mija, f3Mija, huellaMija);
        f1Mija = null;
        f2Mija = null;
        f3Mija = null;
        CrearBoveda.Resultado creadaMija;
        try (credencialesMija) {
            creadaMija = appMija.crearBoveda().ejecutar(new CrearBoveda.Comando(rutaMija, credencialesMija,
                    perfilMija.get().parametros(), 0));
        }
        try {
            progresoMija.etapa("etapa.cifrando", true);
            AgregarArchivos.Resultado archivosMija = this.archivosMija.isEmpty()
                    ? new AgregarArchivos.Resultado(List.of(), List.of())
                    : appMija.agregarArchivos().ejecutar(new AgregarArchivos.Comando(creadaMija.sesionMija(),
                            List.copyOf(this.archivosMija), "", PoliticaColision.OMITIR, progresoMija));
            return new Creada(rutaMija, creadaMija.sesionMija(), creadaMija.tokensMija(), archivosMija);
        } catch (IOException | RuntimeException errorMija) {
            creadaMija.close();
            throw errorMija;
        }
    }

    /** Borra los secretos guardados (al cancelar el asistente). */
    public void limpiar() {
        for (char[] secretoMija : new char[][] {f1Mija, f2Mija, f3Mija}) {
            if (secretoMija != null) {
                Arrays.fill(secretoMija, '\0');
            }
        }
        f1Mija = null;
        f2Mija = null;
        f3Mija = null;
        pasoMija.set(Paso.ARCHIVOS);
    }

    private static char[] requerir(char[] secretoMija) {
        if (secretoMija == null) {
            throw new IllegalStateException("Falta un factor");
        }
        return secretoMija;
    }

    private static char[] reemplazar(char[] anteriorMija, char[] nuevoMija) {
        if (anteriorMija != null) {
            Arrays.fill(anteriorMija, '\0');
        }
        return nuevoMija;
    }

    public ObjectProperty<Paso> pasoProperty() {
        return pasoMija;
    }

    public ObservableList<Path> archivos() {
        return archivosMija;
    }

    public ObjectProperty<ModoLlave> modoLlaveProperty() {
        return modoLlaveMija;
    }

    public ObjectProperty<Path> rutaLlaveProperty() {
        return rutaLlaveMija;
    }

    public ObjectProperty<PerfilUi> perfilProperty() {
        return perfilMija;
    }

    public ObjectProperty<Path> destinoProperty() {
        return destinoMija;
    }

    public ObservableList<Aviso> avisosDestino() {
        return avisosDestinoMija;
    }
}
