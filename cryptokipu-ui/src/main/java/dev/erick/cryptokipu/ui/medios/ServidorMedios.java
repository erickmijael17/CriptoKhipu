package dev.erick.cryptokipu.ui.medios;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.erick.cryptokipu.core.registro.Registro;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Servidor HTTP local para que {@code MediaPlayer} reproduzca audio y video descifrados sin escribirlos en disco
 * (JavaFX solo reproduce desde archivo o URL).
 *
 * <ul>
 *   <li>Enlazado SOLO a la interfaz de bucle local (127.0.0.1), en un puerto aleatorio.</li>
 *   <li>Cada archivo publicado tiene una ruta con un token aleatorio de 256 bits: sin él, 404. Otro programa del
 *       mismo equipo no puede adivinarla (THREAT_MODEL: un proceso con acceso a la memoria sí podría leerla).</li>
 *   <li>Solo {@code GET}; soporta {@code Range} (206 y {@code Content-Range}) para adelantar y retroceder.</li>
 *   <li>Al retirar un archivo se cierra su canal; al cerrar el servidor (al cerrar la sesión) se retiran todos.</li>
 * </ul>
 */
public final class ServidorMedios implements AutoCloseable {

    /** Archivo publicado: la URL completa (con token) y el token para retirarlo. */
    public record Publicacion(String urlMija, String tokenMija) {
        @Override
        public String toString() {
            return "Publicacion[***]";
        }
    }

    private record Fuente(SeekableByteChannel canalMija, long tamanoMija, String tipoMimeMija) {
    }

    private static final System.Logger REGISTRO_MIJA = Registro.para(ServidorMedios.class);
    private static final String PREFIJO_MIJA = "/medio/";
    private static final int BYTES_TOKEN_MIJA = 32;
    private static final int BLOQUE_MIJA = 64 * 1024;
    private static final Pattern RANGO_MIJA = Pattern.compile("bytes=(\\d*)-(\\d*)");
    private static final SecureRandom ALEATORIO_MIJA = new SecureRandom();

    private final HttpServer servidorMija;
    private final ExecutorService hilosMija;
    private final Map<String, Fuente> publicadosMija = new ConcurrentHashMap<>();
    private volatile boolean cerradoMija;

    private ServidorMedios(HttpServer servidorMija, ExecutorService hilosMija) {
        this.servidorMija = servidorMija;
        this.hilosMija = hilosMija;
    }

    /** Arranca en 127.0.0.1 y un puerto libre elegido por el sistema. */
    public static ServidorMedios iniciar() throws IOException {
        HttpServer servidorMija = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        ExecutorService hilosMija = Executors.newVirtualThreadPerTaskExecutor();
        servidorMija.setExecutor(hilosMija);
        ServidorMedios mediosMija = new ServidorMedios(servidorMija, hilosMija);
        servidorMija.createContext("/", mediosMija::atender);
        servidorMija.start();
        return mediosMija;
    }

    public InetSocketAddress direccion() {
        return servidorMija.getAddress();
    }

    /**
     * Publica un canal descifrado. El servidor toma posesión del canal (lo cierra al retirar). {@code nombreMija}
     * solo da la extensión al reproductor; no se usa para buscar el archivo.
     */
    public Publicacion publicar(SeekableByteChannel canalMija, long tamanoMija, String tipoMimeMija,
            String nombreMija) {
        if (cerradoMija) {
            throw new IllegalStateException("El servidor de medios está cerrado");
        }
        byte[] aleatoriosMija = new byte[BYTES_TOKEN_MIJA];
        ALEATORIO_MIJA.nextBytes(aleatoriosMija);
        String tokenMija = HexFormat.of().formatHex(aleatoriosMija);
        publicadosMija.put(tokenMija, new Fuente(Objects.requireNonNull(canalMija), tamanoMija,
                Objects.requireNonNull(tipoMimeMija)));
        InetSocketAddress direccionMija = direccion();
        String urlMija = "http://" + direccionMija.getAddress().getHostAddress() + ":" + direccionMija.getPort()
                + PREFIJO_MIJA + tokenMija + "/" + URLEncoder.encode(nombreMija, StandardCharsets.UTF_8)
                        .replace("+", "%20");
        return new Publicacion(urlMija, tokenMija);
    }

    /** Deja de servir el archivo y cierra su canal. */
    public void retirar(String tokenMija) {
        Fuente fuenteMija = publicadosMija.remove(tokenMija);
        if (fuenteMija != null) {
            cerrarCanal(fuenteMija);
        }
    }

    public int publicados() {
        return publicadosMija.size();
    }

    @Override
    public void close() {
        if (cerradoMija) {
            return;
        }
        cerradoMija = true;
        servidorMija.stop(0);
        hilosMija.shutdownNow();
        publicadosMija.values().forEach(ServidorMedios::cerrarCanal);
        publicadosMija.clear();
    }

    // ------------------------------------------------------------------ HTTP

    private void atender(HttpExchange intercambioMija) throws IOException {
        try (intercambioMija) {
            intercambioMija.getResponseHeaders().set("Cache-Control", "no-store");
            if (!"GET".equals(intercambioMija.getRequestMethod())) {
                intercambioMija.getResponseHeaders().set("Allow", "GET");
                intercambioMija.sendResponseHeaders(405, -1);
                return;
            }
            Fuente fuenteMija = buscar(intercambioMija.getRequestURI().getRawPath());
            if (fuenteMija == null) {
                intercambioMija.sendResponseHeaders(404, -1);
                return;
            }
            servir(intercambioMija, fuenteMija);
        } catch (IOException errorMija) {
            // El reproductor corta conexiones al adelantar: es normal y no se registra el detalle (rutas, tokens).
            REGISTRO_MIJA.log(System.Logger.Level.DEBUG, "Conexión de medios interrumpida: {0}",
                    errorMija.getClass().getSimpleName());
        }
    }

    private Fuente buscar(String rutaMija) {
        if (rutaMija == null || !rutaMija.startsWith(PREFIJO_MIJA)) {
            return null;
        }
        String restoMija = rutaMija.substring(PREFIJO_MIJA.length());
        int barraMija = restoMija.indexOf('/');
        String tokenMija = barraMija < 0 ? restoMija : restoMija.substring(0, barraMija);
        return tokenMija.length() == BYTES_TOKEN_MIJA * 2 ? publicadosMija.get(tokenMija) : null;
    }

    private static void servir(HttpExchange intercambioMija, Fuente fuenteMija) throws IOException {
        long tamanoMija = fuenteMija.tamanoMija();
        long inicioMija = 0;
        long finMija = tamanoMija - 1;
        boolean parcialMija = false;
        String rangoMija = intercambioMija.getRequestHeaders().getFirst("Range");
        var cabecerasMija = intercambioMija.getResponseHeaders();
        cabecerasMija.set("Accept-Ranges", "bytes");
        cabecerasMija.set("Content-Type", fuenteMija.tipoMimeMija());
        if (rangoMija != null) {
            Matcher coincidenciaMija = RANGO_MIJA.matcher(rangoMija.trim());
            long[] limitesMija = coincidenciaMija.matches() ? limites(coincidenciaMija, tamanoMija) : null;
            if (limitesMija == null) {
                cabecerasMija.set("Content-Range", "bytes */" + tamanoMija);
                intercambioMija.sendResponseHeaders(416, -1);
                return;
            }
            inicioMija = limitesMija[0];
            finMija = limitesMija[1];
            parcialMija = true;
            cabecerasMija.set("Content-Range", "bytes " + inicioMija + "-" + finMija + "/" + tamanoMija);
        }
        long longitudMija = tamanoMija == 0 ? 0 : finMija - inicioMija + 1;
        intercambioMija.sendResponseHeaders(parcialMija ? 206 : 200, longitudMija == 0 ? -1 : longitudMija);
        if (longitudMija == 0) {
            return;
        }
        try (OutputStream salidaMija = intercambioMija.getResponseBody()) {
            ByteBuffer bufferMija = ByteBuffer.allocate(BLOQUE_MIJA);
            long posicionMija = inicioMija;
            while (posicionMija <= finMija) {
                bufferMija.clear();
                bufferMija.limit((int) Math.min(BLOQUE_MIJA, finMija - posicionMija + 1));
                int leidosMija;
                // El canal es compartido por todas las peticiones de ese archivo: posición + lectura, atómicas.
                synchronized (fuenteMija) {
                    fuenteMija.canalMija().position(posicionMija);
                    leidosMija = fuenteMija.canalMija().read(bufferMija);
                }
                if (leidosMija <= 0) {
                    break;
                }
                salidaMija.write(bufferMija.array(), 0, leidosMija);
                posicionMija += leidosMija;
            }
        }
    }

    /** {@code [inicio, fin]} del rango pedido, o null si no se puede satisfacer. */
    static long[] limites(Matcher coincidenciaMija, long tamanoMija) {
        String desdeMija = coincidenciaMija.group(1);
        String hastaMija = coincidenciaMija.group(2);
        try {
            if (desdeMija.isEmpty()) {
                if (hastaMija.isEmpty()) {
                    return null;
                }
                long sufijoMija = Long.parseLong(hastaMija);
                if (sufijoMija == 0 || tamanoMija == 0) {
                    return null;
                }
                return new long[] {Math.max(0, tamanoMija - sufijoMija), tamanoMija - 1};
            }
            long inicioMija = Long.parseLong(desdeMija);
            long finMija = hastaMija.isEmpty() ? tamanoMija - 1 : Math.min(Long.parseLong(hastaMija), tamanoMija - 1);
            if (inicioMija >= tamanoMija || finMija < inicioMija) {
                return null;
            }
            return new long[] {inicioMija, finMija};
        } catch (NumberFormatException _) {
            return null;
        }
    }

    private static void cerrarCanal(Fuente fuenteMija) {
        try {
            synchronized (fuenteMija) {
                fuenteMija.canalMija().close();
            }
        } catch (IOException _) {
            // Canal en memoria: no hay nada que limpiar en disco.
        }
    }
}
