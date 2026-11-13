package dev.erick.cryptokipu.infra.configuracion;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

/**
 * Modo portátil (plan, Fase 7.1): se activa si junto a la app hay un archivo {@code portable.properties}.
 *
 * <p>El lanzador de {@code jpackage} pasa {@code -Dcryptokipu.appdir=$APPDIR}; el marcador se busca en esa carpeta
 * y en la raíz de la imagen (la carpeta que contiene el ejecutable). En modo portátil las preferencias se guardan
 * en {@code <raíz>/datos} o, con {@code guardarPreferencias=false}, en ningún lado.
 */
public final class ModoPortatil {

    public static final String MARCADOR_MIJA = "portable.properties";
    public static final String PROPIEDAD_APPDIR_MIJA = "cryptokipu.appdir";
    private static final String CARPETA_TEMPORALES_MIJA = "tmp";
    private static final String CARPETA_REGISTROS_MIJA = "logs";

    /** Configuración detectada. */
    public record Portatil(Path raizMija, boolean guardarPreferenciasMija) {

        public PreferenciasStore preferencias() {
            return guardarPreferenciasMija ? PreferenciasStore.portatil(raizMija.resolve("datos"))
                    : PreferenciasStore.enMemoria();
        }

        /** {@code <raíz>/tmp}: es el {@code java.io.tmpdir} de la JVM en modo portátil. */
        public Path temporales() {
            return raizMija.resolve(CARPETA_TEMPORALES_MIJA);
        }

        /** Crea {@code tmp} y {@code logs} junto a la app (la JVM los usa para temporales y para hs_err). */
        public void prepararCarpetas() throws IOException {
            Files.createDirectories(temporales());
            Files.createDirectories(raizMija.resolve(CARPETA_REGISTROS_MIJA));
        }

        /**
         * Vacía {@code tmp} al cerrar: la carpeta queda, su contenido no. Es "al mejor esfuerzo": lo que otra
         * aplicación mantenga abierto (Windows) se borra al próximo inicio.
         *
         * @return cuántos archivos o carpetas de primer nivel quedaron sin borrar
         */
        public int vaciarTemporales() {
            return vaciar(temporales());
        }
    }

    private ModoPortatil() {
    }

    /** Lee {@code cryptokipu.appdir}; sin esa propiedad (ejecución desde Gradle) no hay modo portátil. */
    public static Optional<Portatil> detectar() throws IOException {
        String appDirMija = System.getProperty(PROPIEDAD_APPDIR_MIJA);
        return appDirMija == null || appDirMija.isBlank() ? Optional.empty() : detectar(Path.of(appDirMija));
    }

    public static Optional<Portatil> detectar(Path appDirMija) throws IOException {
        Path absolutaMija = appDirMija.toAbsolutePath().normalize();
        for (Path candidataMija : new Path[] {absolutaMija, absolutaMija.getParent()}) {
            if (candidataMija != null && Files.isRegularFile(candidataMija.resolve(MARCADOR_MIJA))) {
                Properties propiedadesMija = new Properties();
                try (InputStream entradaMija = Files.newInputStream(candidataMija.resolve(MARCADOR_MIJA))) {
                    propiedadesMija.load(entradaMija);
                }
                boolean guardarMija = !"false".equalsIgnoreCase(
                        propiedadesMija.getProperty("guardarPreferencias", "true").trim());
                return Optional.of(new Portatil(candidataMija, guardarMija));
            }
        }
        return Optional.empty();
    }

    private static int vaciar(Path carpetaMija) {
        if (!Files.isDirectory(carpetaMija)) {
            return 0;
        }
        int pendientesMija = 0;
        try (var contenidoMija = Files.newDirectoryStream(carpetaMija)) {
            for (Path hijoMija : contenidoMija) {
                if (!borrarRecursivo(hijoMija)) {
                    pendientesMija++;
                }
            }
        } catch (IOException _) {
            return Integer.MAX_VALUE;
        }
        return pendientesMija;
    }

    private static boolean borrarRecursivo(Path rutaMija) {
        try {
            if (Files.isDirectory(rutaMija, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                try (var contenidoMija = Files.newDirectoryStream(rutaMija)) {
                    for (Path hijoMija : contenidoMija) {
                        borrarRecursivo(hijoMija);
                    }
                }
            }
            Files.deleteIfExists(rutaMija);
            return true;
        } catch (IOException _) {
            return false;
        }
    }
}
