package dev.erick.cryptokipu.core.format;

import dev.erick.cryptokipu.core.auth.TokenRecuperacion;
import dev.erick.cryptokipu.core.crypto.FuenteAleatoria;
import dev.erick.cryptokipu.core.crypto.ParametrosArgon2;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Genera el corpus semilla del fuzzing del núcleo (Fase 9): entradas válidas y casos límite que el fuzzer muta.
 * {@code ./gradlew :cryptokipu-core:generarSemillasFuzz}. Los archivos resultantes SE VERSIONAN en
 * {@code src/test/resources}; este programa solo se vuelve a ejecutar si cambia el formato.
 */
public final class GeneradorSemillasFuzz {

    private static final FuenteAleatoria FUENTE_MIJA = FuenteAleatoria.delSistema();

    private GeneradorSemillasFuzz() {
    }

    public static void main(String[] argumentosMija) throws IOException {
        Path raizMija = Path.of(argumentosMija[0]).resolve("dev/erick/cryptokipu/core");
        header(raizMija.resolve("format/FuzzFormatoTestInputs/headerNuncaFallaConUnErrorNoControlado"));
        manifest(raizMija.resolve("format/FuzzFormatoTestInputs/manifestNuncaFallaConUnErrorNoControlado"));
        footer(raizMija.resolve("format/FuzzFormatoTestInputs/footerNuncaFallaConUnErrorNoControlado"));
        tokens(raizMija.resolve("auth/FuzzTokenTestInputs/leerTokenSoloAceptaOFallaConTokenMalEscrito"));
        rutas(raizMija.resolve("vault/FuzzRutaInternaTestInputs/rutaInternaNuncaLanzaYLasSegurasNoEscapan"));
    }

    private static void escribir(Path carpetaMija, String nombreMija, byte[] bytesMija) throws IOException {
        Files.createDirectories(carpetaMija);
        Files.write(carpetaMija.resolve(nombreMija), bytesMija);
    }

    private static byte[] conFinal(byte[] bytesMija, int... finalMija) {
        byte[] resultadoMija = java.util.Arrays.copyOf(bytesMija, bytesMija.length + finalMija.length);
        for (int indiceMija = 0; indiceMija < finalMija.length; indiceMija++) {
            resultadoMija[bytesMija.length + indiceMija] = (byte) finalMija[indiceMija];
        }
        return resultadoMija;
    }

    // ------------------------------------------------------------------ header (el booleano va al FINAL del dato)

    private static void header(Path carpetaMija) throws IOException {
        ZonaA zonaMija = ZonaA.nueva(new ParametrosArgon2(LimitesFormato.ARGON2_MEMORIA_MIN_KIB_MIJA, 2, 1), FUENTE_MIJA);
        List<Slot> unoMija = List.of(Slot.vacio(FUENTE_MIJA));
        List<Slot> sieteMija = new ArrayList<>();
        for (int indiceMija = 0; indiceMija < 7; indiceMija++) {
            sieteMija.add(Slot.vacio(FUENTE_MIJA));
        }
        escribir(carpetaMija, "valido-un-slot", conFinal(HeaderCodec.codificar(Header.nuevo(zonaMija, unoMija, 0), FUENTE_MIJA), 1));
        escribir(carpetaMija, "valido-siete-slots-umbral",
                conFinal(HeaderCodec.codificar(Header.nuevo(zonaMija, sieteMija, 5, true), FUENTE_MIJA), 1));
        byte[] sinCrcMija = HeaderCodec.codificar(Header.nuevo(zonaMija, unoMija, 0), FUENTE_MIJA);
        sinCrcMija[HeaderCodec.OFFSET_CRC_MIJA] ^= 0x55;
        escribir(carpetaMija, "crc-roto", conFinal(sinCrcMija, 0));
        escribir(carpetaMija, "ceros", conFinal(new byte[LimitesFormato.TAMANO_HEADER_MIJA], 1));
        escribir(carpetaMija, "vacio", new byte[0]);
        escribir(carpetaMija, "solo-magic", conFinal("CKIPU".getBytes(StandardCharsets.US_ASCII), 1));
    }

    // ------------------------------------------------------------------ manifest

    private static EntradaManifest entrada(int numeroMija, String rutaMija) {
        byte[] idMija = new byte[16];
        idMija[0] = (byte) numeroMija;
        return new EntradaManifest(idMija, rutaMija, 1000L * numeroMija, 1_700_000_000_000L, "text/plain",
                LimitesFormato.OFFSET_DATOS_MIJA + numeroMija * 100L, 1016);
    }

    private static void manifest(Path carpetaMija) throws IOException {
        escribir(carpetaMija, "vacio", ManifestCodec.serializar(Manifest.vacio()));
        escribir(carpetaMija, "una-entrada", ManifestCodec.serializar(new Manifest(2, List.of(entrada(1, "nota.txt")))));
        escribir(carpetaMija, "tres-entradas-con-tildes", ManifestCodec.serializar(new Manifest(7,
                List.of(entrada(1, "fotos/año 2026/ñandú.jpg"), entrada(2, "docs/informe.docx"), entrada(3, "a/b/c/d.md")))));
        escribir(carpetaMija, "version-desconocida", new byte[] {9, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0});
        escribir(carpetaMija, "cantidad-gigante", new byte[] {1, 0, 0, 0, 0, 0, 0, 0, 1, 0x7f, (byte) 0xff, (byte) 0xff, (byte) 0xff});
        escribir(carpetaMija, "truncado", new byte[] {1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 1, 7});
    }

    // ------------------------------------------------------------------ footer: [bytes][booleano][long de 8 bytes]

    private static void footer(Path carpetaMija) throws IOException {
        Footer footerMija = new Footer(LimitesFormato.OFFSET_DATOS_MIJA, 100, 4);
        long posicionMija = footerMija.offsetManifestMija() + footerMija.longitudManifestMija();
        byte[] bytesMija = footerMija.codificar();
        // Jazzer lee el long desde el final del dato; se guardan las dos ordenaciones de bytes posibles.
        escribir(carpetaMija, "valido-orden-a", concat(bytesMija, new byte[] {1}, ordenar(posicionMija, true)));
        escribir(carpetaMija, "valido-orden-b", concat(bytesMija, new byte[] {1}, ordenar(posicionMija, false)));
        byte[] danadoMija = bytesMija.clone();
        danadoMija[3] ^= 0x7f;
        escribir(carpetaMija, "con-crc-roto", concat(danadoMija, new byte[] {0}, ordenar(posicionMija, true)));
        escribir(carpetaMija, "corto", concat(new byte[] {1, 2, 3}, new byte[] {0}, new byte[8]));
    }

    private static byte[] ordenar(long valorMija, boolean grandeMija) {
        ByteBuffer bufferMija = ByteBuffer.allocate(8).order(grandeMija ? java.nio.ByteOrder.BIG_ENDIAN
                : java.nio.ByteOrder.LITTLE_ENDIAN).putLong(valorMija);
        return bufferMija.array();
    }

    private static byte[] concat(byte[]... partesMija) {
        int totalMija = 0;
        for (byte[] parteMija : partesMija) {
            totalMija += parteMija.length;
        }
        ByteBuffer bufferMija = ByteBuffer.allocate(totalMija);
        for (byte[] parteMija : partesMija) {
            bufferMija.put(parteMija);
        }
        return bufferMija.array();
    }

    // ------------------------------------------------------------------ tokens y rutas

    private static void tokens(Path carpetaMija) throws IOException {
        for (int indiceMija = 0; indiceMija < 3; indiceMija++) {
            try (TokenRecuperacion tokenMija = TokenRecuperacion.generar(FUENTE_MIJA)) {
                String textoMija = new String(tokenMija.formatear());
                escribir(carpetaMija, "valido-" + indiceMija, textoMija.getBytes(StandardCharsets.UTF_8));
                escribir(carpetaMija, "sin-guiones-minusculas-" + indiceMija,
                        textoMija.replace("-", "").toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.UTF_8));
                escribir(carpetaMija, "con-espacios-" + indiceMija, textoMija.replace("-", " ").getBytes(StandardCharsets.UTF_8));
            }
        }
        escribir(carpetaMija, "vacio", new byte[0]);
        escribir(carpetaMija, "solo-prefijo", "CKP-".getBytes(StandardCharsets.US_ASCII));
        escribir(carpetaMija, "unicode", "CKP-ÁÉÍÓÚ-ñññññ-😀😀😀😀😀".getBytes(StandardCharsets.UTF_8));
    }

    private static void rutas(Path carpetaMija) throws IOException {
        String[] casosMija = {"documentos/informe.pdf", "fotos/año 2026/ñandú.jpg", "../escape.txt", "a/../../b", "/absoluta",
            "C:\\Windows\\system32", "C:/x", "\\\\servidor\\recurso", "con/nul/x", "NUL", "aux.txt", "COM1", "lpt9.log", "a//b",
            "termina.", "termina ", " empieza", "a\0b", "a\\b", "e\u0301/e\u0301.txt", ".", "..", "...", "a/./b",
            "x".repeat(4097), "ñ".repeat(2100)};
        for (int indiceMija = 0; indiceMija < casosMija.length; indiceMija++) {
            escribir(carpetaMija, "caso-" + indiceMija, casosMija[indiceMija].getBytes(StandardCharsets.UTF_8));
        }
    }
}
