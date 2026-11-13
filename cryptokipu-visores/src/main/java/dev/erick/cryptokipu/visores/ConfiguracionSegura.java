package dev.erick.cryptokipu.visores;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.util.IOUtils;
import org.apache.poi.util.TempFile;
import org.apache.poi.util.TempFileCreationStrategy;

/**
 * Configuración global de las bibliotecas de vista previa para que nada toque el disco ni consuma memoria sin
 * límite. Se aplica una sola vez, al cargar el proveedor (es decir, al iniciar la aplicación).
 *
 * <ul>
 *   <li>{@code ImageIO.setUseCache(false)}: ImageIO no crea archivos de caché al decodificar.</li>
 *   <li>POI: una estrategia de temporales que LANZA excepción; límites de ZIP contra bombas (relación de
 *       compresión, tamaño por entrada, número de entradas, texto extraído) y de arreglos en memoria.</li>
 *   <li>PDFBox: su caché de fuentes ({@code .pdfbox.cache}, normalmente en el perfil del usuario) va a una carpeta
 *       propia dentro de {@code java.io.tmpdir} que se borra al salir. En modo portátil esa carpeta está en el USB.</li>
 * </ul>
 */
public final class ConfiguracionSegura {

    /** Relación mínima comprimido/descomprimido por entrada ZIP (1:100). */
    public static final double RELACION_MINIMA_MIJA = 0.01;
    /** Tamaño máximo descomprimido de una entrada ZIP de un documento de Office. */
    public static final long ENTRADA_MAXIMA_MIJA = 100L * 1024 * 1024;
    public static final long ENTRADAS_MAXIMAS_MIJA = 5_000;
    public static final long TEXTO_MAXIMO_MIJA = 10L * 1024 * 1024;
    public static final String PROPIEDAD_CACHE_FUENTES_MIJA = "pdfbox.fontcache";

    private static boolean aplicadaMija;

    private ConfiguracionSegura() {
    }

    /**
     * PDFBox (commons-logging) y POI (log4j-api) escriben mensajes con rutas y detalles del documento; la aplicación
     * ya tiene su propio registro ({@code Registro}) sin datos sensibles, así que se silencian. Debe llamarse antes de
     * cargar cualquier clase de esas bibliotecas.
     */
    static void silenciarRegistrosDeTerceros() {
        System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog");
        System.setProperty("log4j2.statusLoggerLevel", "OFF");
        System.setProperty("org.apache.logging.log4j.simplelog.StatusLogger.level", "OFF");
    }

    public static synchronized void aplicar() {
        if (aplicadaMija) {
            return;
        }
        ImageIO.setUseCache(false);
        TempFile.setTempFileCreationStrategy(new SinTemporales());
        ZipSecureFile.setMinInflateRatio(RELACION_MINIMA_MIJA);
        ZipSecureFile.setMaxEntrySize(ENTRADA_MAXIMA_MIJA);
        ZipSecureFile.setMaxFileCount(ENTRADAS_MAXIMAS_MIJA);
        ZipSecureFile.setMaxTextSize(TEXTO_MAXIMO_MIJA);
        IOUtils.setByteArrayMaxOverride((int) ENTRADA_MAXIMA_MIJA);
        if (System.getProperty(PROPIEDAD_CACHE_FUENTES_MIJA) == null) {
            System.setProperty(PROPIEDAD_CACHE_FUENTES_MIJA, carpetaCacheFuentes().toString());
        }
        aplicadaMija = true;
    }

    /** Carpeta privada para la caché de fuentes de PDFBox; se borra (con su contenido) al terminar la JVM. */
    private static Path carpetaCacheFuentes() {
        try {
            Path carpetaMija = Files.createTempDirectory("cryptokipu-fuentes-");
            Runtime.getRuntime().addShutdownHook(new Thread(() -> borrar(carpetaMija), "cryptokipu-cache-fuentes"));
            return carpetaMija;
        } catch (IOException errorMija) {
            throw new UncheckedIOException(errorMija);
        }
    }

    private static void borrar(Path carpetaMija) {
        File[] archivosMija = carpetaMija.toFile().listFiles();
        if (archivosMija != null) {
            for (File archivoMija : archivosMija) {
                archivoMija.delete();
            }
        }
        carpetaMija.toFile().delete();
    }

    /** POI no puede crear temporales: cualquier intento falla (la vista previa es solo en RAM). */
    static final class SinTemporales implements TempFileCreationStrategy {

        @Override
        public File createTempFile(String prefijoMija, String sufijoMija) throws IOException {
            throw new IOException("CryptoKipu no permite archivos temporales: la vista previa es solo en memoria");
        }

        @Override
        public File createTempDirectory(String prefijoMija) throws IOException {
            throw new IOException("CryptoKipu no permite carpetas temporales: la vista previa es solo en memoria");
        }
    }
}
