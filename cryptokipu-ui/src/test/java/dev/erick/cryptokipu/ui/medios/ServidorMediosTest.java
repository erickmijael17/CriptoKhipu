package dev.erick.cryptokipu.ui.medios;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** El servidor de medios es el único código con red: solo 127.0.0.1, token de 256 bits, solo GET, con Range. */
class ServidorMediosTest {

    private static final int TAMANO_MIJA = 300_000;

    @TempDir
    Path carpetaMija;

    private final HttpClient clienteMija = HttpClient.newHttpClient();
    private byte[] contenidoMija;
    private ServidorMedios servidorMija;
    private ServidorMedios.Publicacion publicadaMija;

    @BeforeEach
    void iniciar() throws IOException {
        contenidoMija = new byte[TAMANO_MIJA];
        new Random(7).nextBytes(contenidoMija);
        Path archivoMija = Files.write(carpetaMija.resolve("medio.bin"), contenidoMija);
        servidorMija = ServidorMedios.iniciar();
        publicadaMija = servidorMija.publicar(Files.newByteChannel(archivoMija), TAMANO_MIJA, "video/mp4",
                "archivo.mp4");
    }

    @AfterEach
    void cerrar() {
        servidorMija.close();
        clienteMija.close();
    }

    private HttpResponse<byte[]> pedir(String metodoMija, URI uriMija, String rangoMija) throws Exception {
        HttpRequest.Builder constructorMija = HttpRequest.newBuilder(uriMija)
                .method(metodoMija, HttpRequest.BodyPublishers.noBody());
        if (rangoMija != null) {
            constructorMija.header("Range", rangoMija);
        }
        return clienteMija.send(constructorMija.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpResponse<byte[]> get(String rangoMija) throws Exception {
        return pedir("GET", URI.create(publicadaMija.urlMija()), rangoMija);
    }

    @Test
    void enlazaSoloALaInterfazDeBucleLocal() {
        InetSocketAddress direccionMija = servidorMija.direccion();
        assertTrue(direccionMija.getAddress().isLoopbackAddress(), "nunca 0.0.0.0: " + direccionMija);
        assertFalse(direccionMija.getAddress().isAnyLocalAddress());
        assertTrue(direccionMija.getPort() > 0);
        assertEquals(direccionMija.getAddress().getHostAddress(), URI.create(publicadaMija.urlMija()).getHost());
    }

    @Test
    void desdeOtraInterfazNoConecta() throws Exception {
        List<InetAddress> otrasMija = new ArrayList<>();
        for (NetworkInterface interfazMija : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (interfazMija.isUp() && !interfazMija.isLoopback()) {
                Collections.list(interfazMija.getInetAddresses()).stream().filter(Inet4Address.class::isInstance)
                        .forEach(otrasMija::add);
            }
        }
        assumeFalse(otrasMija.isEmpty(), "El equipo no tiene otra interfaz IPv4 para probar");
        for (InetAddress otraMija : otrasMija) {
            try (Socket socketMija = new Socket()) {
                assertThrows(IOException.class, () -> socketMija.connect(
                        new InetSocketAddress(otraMija, servidorMija.direccion().getPort()), 2000),
                        "el servidor no debe escuchar en " + otraMija);
            }
        }
    }

    @Test
    void conTokenSirveElContenidoCompleto() throws Exception {
        HttpResponse<byte[]> respuestaMija = get(null);
        assertEquals(200, respuestaMija.statusCode());
        assertArrayEquals(contenidoMija, respuestaMija.body());
        assertEquals("video/mp4", respuestaMija.headers().firstValue("Content-Type").orElseThrow());
        assertEquals("bytes", respuestaMija.headers().firstValue("Accept-Ranges").orElseThrow());
        assertEquals("no-store", respuestaMija.headers().firstValue("Cache-Control").orElseThrow());
    }

    @Test
    void sinTokenOConUnoIncorrectoResponde404() throws Exception {
        URI baseMija = URI.create(publicadaMija.urlMija());
        String origenMija = "http://" + baseMija.getHost() + ":" + baseMija.getPort();
        String tokenFalsoMija = "0".repeat(64);
        for (String rutaMija : List.of("/", "/medio/", "/medio/archivo.mp4", "/medio/" + tokenFalsoMija + "/archivo.mp4",
                "/medio/" + publicadaMija.tokenMija().substring(1) + "/x", "/medio/" + publicadaMija.tokenMija()
                        .toUpperCase(java.util.Locale.ROOT) + "/x", "/otro/" + publicadaMija.tokenMija() + "/x")) {
            assertEquals(404, pedir("GET", URI.create(origenMija + rutaMija), null).statusCode(), rutaMija);
        }
    }

    @Test
    void elTokenTiene256BitsYCambiaEnCadaPublicacion() throws Exception {
        assertEquals(64, publicadaMija.tokenMija().length());
        assertTrue(publicadaMija.tokenMija().matches("[0-9a-f]{64}"));
        ServidorMedios.Publicacion otraMija = servidorMija.publicar(
                Files.newByteChannel(Files.write(carpetaMija.resolve("otro.bin"), new byte[10])), 10, "audio/mpeg",
                "otro.mp3");
        assertNotEquals(publicadaMija.tokenMija(), otraMija.tokenMija());
        assertEquals("Publicacion[***]", publicadaMija.toString(), "el token no se imprime");
    }

    @Test
    void soloAceptaGet() throws Exception {
        for (String metodoMija : List.of("POST", "PUT", "DELETE")) {
            HttpResponse<byte[]> respuestaMija = pedir(metodoMija, URI.create(publicadaMija.urlMija()), null);
            assertEquals(405, respuestaMija.statusCode(), metodoMija);
            assertEquals("GET", respuestaMija.headers().firstValue("Allow").orElseThrow());
        }
    }

    @Test
    void rangoEnPosicionesAleatoriasDevuelveLosMismosBytesQueElOriginal() throws Exception {
        Random aleatorioMija = new Random(99);
        for (int repeticionMija = 0; repeticionMija < 40; repeticionMija++) {
            int inicioMija = aleatorioMija.nextInt(TAMANO_MIJA);
            int finMija = inicioMija + aleatorioMija.nextInt(Math.min(100_000, TAMANO_MIJA - inicioMija));
            HttpResponse<byte[]> respuestaMija = get("bytes=" + inicioMija + "-" + finMija);
            assertEquals(206, respuestaMija.statusCode());
            assertEquals("bytes " + inicioMija + "-" + finMija + "/" + TAMANO_MIJA,
                    respuestaMija.headers().firstValue("Content-Range").orElseThrow());
            assertArrayEquals(Arrays.copyOfRange(contenidoMija, inicioMija, finMija + 1), respuestaMija.body(),
                    "rango " + inicioMija + "-" + finMija);
        }
    }

    @Test
    void rangoAbiertoYDeSufijoComoLosPideMediaPlayer() throws Exception {
        HttpResponse<byte[]> abiertoMija = get("bytes=250000-");
        assertEquals(206, abiertoMija.statusCode());
        assertArrayEquals(Arrays.copyOfRange(contenidoMija, 250_000, TAMANO_MIJA), abiertoMija.body());

        HttpResponse<byte[]> sufijoMija = get("bytes=-1000");
        assertEquals(206, sufijoMija.statusCode());
        assertArrayEquals(Arrays.copyOfRange(contenidoMija, TAMANO_MIJA - 1000, TAMANO_MIJA), sufijoMija.body());

        HttpResponse<byte[]> pasadoMija = get("bytes=0-" + (TAMANO_MIJA * 2L));
        assertEquals("bytes 0-" + (TAMANO_MIJA - 1) + "/" + TAMANO_MIJA,
                pasadoMija.headers().firstValue("Content-Range").orElseThrow(), "el fin se recorta al tamaño");
    }

    @Test
    void adelantarYRetrocederEnLaMismaConexionLogicaEsConsistente() throws Exception {
        // Orden como el de un reproductor: final (moov), inicio, salto adelante, salto atrás.
        for (int inicioMija : new int[] {TAMANO_MIJA - 1000, 0, 200_000, 50_000, 123_456, 0}) {
            HttpResponse<byte[]> respuestaMija = get("bytes=" + inicioMija + "-" + (inicioMija + 999));
            assertArrayEquals(Arrays.copyOfRange(contenidoMija, inicioMija, Math.min(TAMANO_MIJA, inicioMija + 1000)),
                    respuestaMija.body(), "desde " + inicioMija);
        }
    }

    @Test
    void rangosInvalidosResponden416() throws Exception {
        for (String rangoMija : List.of("bytes=" + TAMANO_MIJA + "-", "bytes=500-100", "bytes=-0", "bytes=-",
                "items=0-5", "bytes=a-b")) {
            HttpResponse<byte[]> respuestaMija = get(rangoMija);
            assertEquals(416, respuestaMija.statusCode(), rangoMija);
            assertEquals("bytes */" + TAMANO_MIJA, respuestaMija.headers().firstValue("Content-Range").orElseThrow());
        }
    }

    @Test
    void retirarCierraElArchivoYApagarElServidorLoDejaSinRespuesta() throws Exception {
        assertEquals(1, servidorMija.publicados());
        servidorMija.retirar(publicadaMija.tokenMija());
        assertEquals(0, servidorMija.publicados());
        assertEquals(404, get(null).statusCode(), "retirado: ya no se sirve");

        int puertoMija = servidorMija.direccion().getPort();
        servidorMija.close();
        assertThrows(IllegalStateException.class, () -> servidorMija.publicar(
                Files.newByteChannel(carpetaMija.resolve("medio.bin")), 1, "audio/wav", "x.wav"));
        try (Socket socketMija = new Socket()) {
            assertThrows(IOException.class, () -> socketMija.connect(
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), puertoMija), 2000));
        }
    }
}
