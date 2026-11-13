package dev.erick.cryptokipu.infra.configuracion;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

/**
 * Preferencias en un {@code .properties}: en el directorio del usuario o, en modo portátil, junto a la app
 * (nunca {@code java.util.prefs}, que escribe en el registro de Windows).
 *
 * <p>Solo admite las claves de {@link Clave}: nunca guarda credenciales, tokens ni nombres de archivos internos
 * de una bóveda. "Recientes" son rutas de archivos {@code .kipu}.
 */
public final class PreferenciasStore {

    public enum Clave { TEMA, IDIOMA, AUTO_BLOQUEO_MINUTOS, PERFIL_ARGON2, RECIENTES, RECIENTES_ACTIVOS }

    private static final String ARCHIVO_MIJA = "preferencias.properties";
    private static final int MAXIMO_RECIENTES_MIJA = 10;

    /** {@code null} en modo portátil sin guardar: las preferencias viven solo en memoria. */
    private final Path archivoMija;
    private final Properties memoriaMija = new Properties();

    private PreferenciasStore(Path archivoMija) {
        this.archivoMija = archivoMija;
    }

    /** {@code ~/.cryptokipu/preferencias.properties}. */
    public static PreferenciasStore delUsuario() {
        return new PreferenciasStore(Path.of(System.getProperty("user.home"), ".cryptokipu", ARCHIVO_MIJA));
    }

    /** Modo portátil: junto a la app, en el USB; no deja nada en el equipo anfitrión. */
    public static PreferenciasStore portatil(Path carpetaAppMija) {
        return new PreferenciasStore(carpetaAppMija.resolve(ARCHIVO_MIJA));
    }

    /** Modo portátil con {@code guardarPreferencias=false}: nada se escribe en ningún disco. */
    public static PreferenciasStore enMemoria() {
        return new PreferenciasStore(null);
    }

    public boolean persistente() {
        return archivoMija != null;
    }

    public Optional<String> leer(Clave claveMija) throws IOException {
        return Optional.ofNullable(cargar().getProperty(nombre(claveMija)));
    }

    public void guardar(Clave claveMija, String valorMija) throws IOException {
        Objects.requireNonNull(valorMija, "valorMija");
        if (valorMija.indexOf('\n') >= 0 || valorMija.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Valor con saltos de línea");
        }
        if (claveMija == Clave.RECIENTES) {
            throw new IllegalArgumentException("Usar agregarReciente");
        }
        Properties propiedadesMija = cargar();
        propiedadesMija.setProperty(nombre(claveMija), valorMija);
        escribir(propiedadesMija);
    }

    /** Recordar bóvedas recientes (por defecto sí). Al desactivarlo se borra la lista. */
    public boolean recientesActivos() throws IOException {
        return !leer(Clave.RECIENTES_ACTIVOS).map("false"::equals).orElse(false);
    }

    public void activarRecientes(boolean activoMija) throws IOException {
        Properties propiedadesMija = cargar();
        propiedadesMija.setProperty(nombre(Clave.RECIENTES_ACTIVOS), Boolean.toString(activoMija));
        if (!activoMija) {
            propiedadesMija.remove(nombre(Clave.RECIENTES));
        }
        escribir(propiedadesMija);
    }

    public List<Path> recientes() throws IOException {
        return leer(Clave.RECIENTES).map(valorMija -> Arrays.stream(valorMija.split("\\|"))
                .filter(textoMija -> !textoMija.isBlank()).map(Path::of).toList()).orElse(List.of());
    }

    /** Agrega la ruta de una bóveda {@code .kipu} al principio de la lista de recientes (máximo 10). */
    public void agregarReciente(Path bovedaMija) throws IOException {
        if (!recientesActivos()) {
            return;
        }
        String rutaMija = bovedaMija.toAbsolutePath().normalize().toString();
        if (!rutaMija.toLowerCase(Locale.ROOT).endsWith(".kipu") || rutaMija.contains("|")) {
            throw new IllegalArgumentException("Solo se recuerdan rutas de archivos .kipu");
        }
        List<String> listaMija = new ArrayList<>();
        listaMija.add(rutaMija);
        recientes().stream().map(Path::toString).filter(otraMija -> !otraMija.equals(rutaMija))
                .limit(MAXIMO_RECIENTES_MIJA - 1L).forEach(listaMija::add);
        Properties propiedadesMija = cargar();
        propiedadesMija.setProperty(nombre(Clave.RECIENTES), String.join("|", listaMija));
        escribir(propiedadesMija);
    }

    private Properties cargar() throws IOException {
        Properties propiedadesMija = new Properties();
        if (archivoMija == null) {
            propiedadesMija.putAll(memoriaMija);
        } else if (Files.exists(archivoMija)) {
            try (InputStream entradaMija = Files.newInputStream(archivoMija)) {
                propiedadesMija.load(entradaMija);
            }
        }
        propiedadesMija.stringPropertyNames().stream()
                .filter(nombreMija -> Arrays.stream(Clave.values()).noneMatch(claveMija -> nombre(claveMija).equals(nombreMija)))
                .toList().forEach(propiedadesMija::remove);
        return propiedadesMija;
    }

    private void escribir(Properties propiedadesMija) throws IOException {
        if (archivoMija == null) {
            memoriaMija.clear();
            memoriaMija.putAll(propiedadesMija);
            return;
        }
        Files.createDirectories(archivoMija.getParent());
        Path temporalMija = archivoMija.resolveSibling(ARCHIVO_MIJA + ".tmp");
        try (OutputStream salidaMija = Files.newOutputStream(temporalMija)) {
            propiedadesMija.store(salidaMija, "CryptoKipu: preferencias (sin credenciales ni nombres de archivos internos)");
        }
        Files.move(temporalMija, archivoMija, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private static String nombre(Clave claveMija) {
        return claveMija.name().toLowerCase(Locale.ROOT);
    }
}
