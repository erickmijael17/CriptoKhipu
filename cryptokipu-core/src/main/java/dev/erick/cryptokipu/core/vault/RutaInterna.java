package dev.erick.cryptokipu.core.vault;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Rutas dentro de la bóveda: relativas, con {@code /} como separador y normalizadas a NFC.
 *
 * <p>{@link #problema(String)} se aplica <b>al extraer</b> aunque el manifest esté autenticado, porque la
 * bóveda pudo crearla otra persona ("zip-slip"). Rechaza: rutas vacías o absolutas, {@code \}, segmentos
 * vacíos, {@code .} y {@code ..}, letras de unidad y rutas UNC, nombres reservados de Windows
 * (CON, PRN, AUX, NUL, COM1-9, LPT1-9, también con extensión), caracteres inválidos {@code <>:"|?*},
 * caracteres de control y segmentos que terminan en punto o espacio (Windows los recorta).
 */
public final class RutaInterna {

    public static final int LONGITUD_MAXIMA_MIJA = 4096;
    private static final String INVALIDOS_MIJA = "<>:\"|?*\\";
    /**
     * Lista de "Naming Files, Paths, and Namespaces" (Microsoft Learn): incluye COM0/LPT0 y las variantes con
     * superíndice ¹²³. Se agregan CONIN$ y CONOUT$, que Windows también trata como dispositivos.
     */
    private static final Set<String> RESERVADOS_MIJA = Set.of("CON", "PRN", "AUX", "NUL", "CONIN$", "CONOUT$",
            "COM0", "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "COM¹", "COM²", "COM³",
            "LPT0", "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9",
            "LPT¹", "LPT²", "LPT³");

    private RutaInterna() {
    }

    /** Motivo por el que la ruta no es segura, o vacío si se puede usar. */
    public static Optional<String> problema(String rutaMija) {
        if (rutaMija == null || rutaMija.isEmpty()) {
            return Optional.of("ruta vacía");
        }
        if (rutaMija.length() > LONGITUD_MAXIMA_MIJA) {
            return Optional.of("ruta demasiado larga");
        }
        if (rutaMija.startsWith("/")) {
            return Optional.of("ruta absoluta o UNC");
        }
        for (String segmentoMija : rutaMija.split("/", -1)) {
            Optional<String> errorMija = problemaSegmento(segmentoMija);
            if (errorMija.isPresent()) {
                return errorMija;
            }
        }
        return Optional.empty();
    }

    public static boolean esSegura(String rutaMija) {
        return problema(rutaMija).isEmpty();
    }

    /** Une segmentos con {@code /} y normaliza a NFC. */
    public static String unir(Iterable<String> segmentosMija) {
        return Normalizer.normalize(String.join("/", segmentosMija), Normalizer.Form.NFC);
    }

    public static String normalizar(String rutaMija) {
        return Normalizer.normalize(rutaMija, Normalizer.Form.NFC);
    }

    /** Clave para detectar colisiones que solo difieren en mayúsculas (Windows y macOS no las distinguen). */
    public static String claveColision(String rutaMija) {
        return normalizar(rutaMija).toLowerCase(Locale.ROOT);
    }

    private static Optional<String> problemaSegmento(String segmentoMija) {
        if (segmentoMija.isEmpty()) {
            return Optional.of("segmento vacío");
        }
        if (segmentoMija.equals(".") || segmentoMija.equals("..")) {
            return Optional.of("segmento '.' o '..'");
        }
        for (int indiceMija = 0; indiceMija < segmentoMija.length(); indiceMija++) {
            char caracterMija = segmentoMija.charAt(indiceMija);
            if (caracterMija < 0x20 || caracterMija == 0x7F) {
                return Optional.of("carácter de control");
            }
            if (INVALIDOS_MIJA.indexOf(caracterMija) >= 0) {
                return Optional.of("carácter inválido o letra de unidad");
            }
        }
        char ultimoMija = segmentoMija.charAt(segmentoMija.length() - 1);
        if (ultimoMija == '.' || ultimoMija == ' ') {
            return Optional.of("termina en punto o espacio");
        }
        int puntoMija = segmentoMija.indexOf('.');
        String baseMija = (puntoMija < 0 ? segmentoMija : segmentoMija.substring(0, puntoMija)).trim();
        if (RESERVADOS_MIJA.contains(baseMija.toUpperCase(Locale.ROOT))) {
            return Optional.of("nombre reservado de Windows");
        }
        return Optional.empty();
    }
}
