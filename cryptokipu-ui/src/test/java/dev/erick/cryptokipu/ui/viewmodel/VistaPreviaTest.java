package dev.erick.cryptokipu.ui.viewmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erick.cryptokipu.core.format.EntradaManifest;
import dev.erick.cryptokipu.infra.configuracion.Contenedor;
import dev.erick.cryptokipu.ui.viewmodel.ExploradorViewModel.VistaPrevia;
import dev.erick.cryptokipu.visores.VisoresEnMemoria;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Límites de la vista previa en RAM: qué se muestra, qué se deriva a "Abrir con aplicación externa". */
class VistaPreviaTest {

    @TempDir
    Path carpetaMija;

    private EstadoApp estadoMija;
    private ExploradorViewModel visoresMija;
    private ExploradorViewModel sinVisoresMija;

    /** Bóveda con un archivo por caso; devuelve el estado abierto. */
    private void abrir(List<Path> archivosMija) throws Exception {
        Contenedor appMija = BancoUi.contenedor(carpetaMija);
        AsistenteCreacionViewModel.Creada creadaMija = BancoUi.asistenteListo(appMija,
                carpetaMija.resolve("previa.kipu"), archivosMija).crear(ProgresoOperacion.NINGUNO_MIJA);
        creadaMija.tokensMija().forEach(tokenMija -> tokenMija.close());
        estadoMija = new EstadoApp(appMija);
        estadoMija.abrir(creadaMija.rutaMija(), creadaMija.sesionMija());
        visoresMija = new ExploradorViewModel(appMija, List.of(new VisoresEnMemoria()));
        sinVisoresMija = new ExploradorViewModel(appMija);
    }

    private VistaPrevia ver(ExploradorViewModel vmMija, String nombreMija) throws IOException {
        EntradaManifest entradaMija = estadoMija.sesion().manifest().entradasMija().stream()
                .filter(candidataMija -> candidataMija.rutaMija().equals(nombreMija)).findFirst().orElseThrow();
        return vmMija.vistaPrevia(estadoMija.sesion(), entradaMija, estadoMija::servidorMedios);
    }

    private Path archivo(String nombreMija, byte[] contenidoMija) throws IOException {
        Path carpetaOrigenMija = Files.createDirectories(carpetaMija.resolve("o"));
        return Files.write(carpetaOrigenMija.resolve(nombreMija), contenidoMija);
    }

    /** PNG mínimo y válido de cabecera que DICE medir ancho × alto (una bomba de descompresión). */
    static byte[] pngQueDiceMedir(int anchoMija, int altoMija) {
        ByteBuffer bufferMija = ByteBuffer.allocate(33);
        bufferMija.put(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        bufferMija.putInt(13).put("IHDR".getBytes(StandardCharsets.US_ASCII)).putInt(anchoMija).putInt(altoMija)
                .put(new byte[] {8, 2, 0, 0, 0}).putInt(0);
        return bufferMija.array();
    }

    @Test
    void imagenesTextosMediosYFormatosExternos() throws Exception {
        byte[] textoMija = "hola".getBytes(StandardCharsets.UTF_8);
        abrir(List.of(
                archivo("notas.txt", textoMija),
                archivo("bomba.png", pngQueDiceMedir(50_000, 50_000)),
                archivo("rota.png", new byte[] {1, 2, 3, 4}),
                archivo("peli.mkv", new byte[100]),
                archivo("sonido.flac", new byte[100]),
                archivo("clip.avi", new byte[100]),
                archivo("dato.bin", new byte[100]),
                archivo("tema.mp3", new byte[1000]),
                archivo("informe.pdf", new byte[50]),
                archivo("antiguo.doc", new byte[50])));

        assertEquals("hola", assertInstanceOf(VistaPrevia.Texto.class, ver(visoresMija, "notas.txt")).contenidoMija());
        assertEquals("previa.imagenEnorme", assertInstanceOf(VistaPrevia.Externa.class, ver(visoresMija, "bomba.png"))
                .claveMotivoMija(), "las dimensiones se leen de la cabecera ANTES de decodificar (máx. 100 MP)");
        assertEquals("previa.imagenIlegible", assertInstanceOf(VistaPrevia.Externa.class, ver(visoresMija, "rota.png"))
                .claveMotivoMija());
        for (String formatoMija : List.of("peli.mkv", "sonido.flac", "clip.avi")) {
            assertEquals("previa.formatoExterno", assertInstanceOf(VistaPrevia.Externa.class,
                    ver(visoresMija, formatoMija)).claveMotivoMija(), formatoMija);
        }
        assertEquals("previa.noSoportada", assertInstanceOf(VistaPrevia.Externa.class, ver(visoresMija, "dato.bin"))
                .claveMotivoMija());
        assertEquals("previa.formatoExterno", assertInstanceOf(VistaPrevia.Externa.class, ver(visoresMija, "antiguo.doc"))
                .claveMotivoMija(), "los formatos binarios de Office antiguos van a la aplicación externa");

        VistaPrevia.Medio audioMija = assertInstanceOf(VistaPrevia.Medio.class, ver(visoresMija, "tema.mp3"));
        assertFalse(audioMija.videoMija());
        assertTrue(audioMija.urlMija().startsWith("http://127.0.0.1:"), audioMija.urlMija());
        assertEquals("Medio[***]", audioMija.toString(), "la URL con el token no se imprime");

        // Un PDF dañado no rompe la interfaz: se ofrece la aplicación externa. Sin el módulo de visores, lo mismo.
        assertEquals("previa.documentoIlegible", assertInstanceOf(VistaPrevia.Externa.class,
                ver(visoresMija, "informe.pdf")).claveMotivoMija());
        assertEquals("previa.sinVisores", assertInstanceOf(VistaPrevia.Externa.class,
                ver(sinVisoresMija, "informe.pdf")).claveMotivoMija());
        assertFalse(sinVisoresMija.hayVisoresDeDocumentos());
        assertTrue(visoresMija.hayVisoresDeDocumentos());
    }

    @Test
    void textoDeMasDeDiezMegabytesNoSeCargaEnMemoria() throws Exception {
        byte[] grandeMija = new byte[(int) ExploradorViewModel.MAXIMO_TEXTO_MIJA + 1];
        new Random(1).nextBytes(grandeMija);
        abrir(List.of(archivo("enorme.txt", grandeMija), archivo("justo.csv",
                new byte[(int) ExploradorViewModel.MAXIMO_TEXTO_MIJA])));
        assertEquals("previa.demasiadoGrande", assertInstanceOf(VistaPrevia.Externa.class,
                ver(visoresMija, "enorme.txt")).claveMotivoMija());
        assertInstanceOf(VistaPrevia.Texto.class, ver(visoresMija, "justo.csv"));
    }

    @Test
    void dimensionesDeLaCabeceraSinDecodificar() throws Exception {
        assertEquals(new DimensionesImagen.Dimensiones(50_000, 50_000),
                DimensionesImagen.leer(pngQueDiceMedir(50_000, 50_000)).orElseThrow());
        assertTrue(DimensionesImagen.leer(pngQueDiceMedir(50_000, 50_000)).orElseThrow().pixeles()
                > DimensionesImagen.PIXELES_MAXIMOS_MIJA);
        assertEquals(Optional.empty(), DimensionesImagen.leer(new byte[] {0, 1, 2}));
        assertEquals(Optional.empty(), DimensionesImagen.leer(pngQueDiceMedir(0, 10)));

        ByteArrayOutputStream gifMija = new ByteArrayOutputStream();
        gifMija.write("GIF89a".getBytes(StandardCharsets.US_ASCII));
        gifMija.write(new byte[] {(byte) 0x2C, 0x01, (byte) 0x90, 0x01, 0, 0, 0});
        assertEquals(new DimensionesImagen.Dimensiones(300, 400),
                DimensionesImagen.leer(gifMija.toByteArray()).orElseThrow());

        List<Byte> jpegMija = new ArrayList<>();
        for (int valorMija : new int[] {0xFF, 0xD8, 0xFF, 0xC0, 0x00, 0x11, 0x08, 0x02, 0x00, 0x03, 0x00, 0x03, 1, 1, 0x11,
                0, 2, 0x11, 1}) {
            jpegMija.add((byte) valorMija);
        }
        byte[] jpegBytesMija = new byte[jpegMija.size()];
        for (int indiceMija = 0; indiceMija < jpegBytesMija.length; indiceMija++) {
            jpegBytesMija[indiceMija] = jpegMija.get(indiceMija);
        }
        assertEquals(new DimensionesImagen.Dimensiones(768, 512), DimensionesImagen.leer(jpegBytesMija).orElseThrow());
    }
}
