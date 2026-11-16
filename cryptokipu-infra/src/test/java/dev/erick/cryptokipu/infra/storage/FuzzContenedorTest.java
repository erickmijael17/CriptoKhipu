package dev.erick.cryptokipu.infra.storage;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import dev.erick.cryptokipu.core.error.ExcepcionBoveda;
import dev.erick.cryptokipu.core.format.HeaderCodec;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Fuzzing de la apertura de un {@code .kipu} sin claves: lectura de los dos headers y búsqueda del footer (la pista de
 * la Zona C, el barrido hacia atrás y la comparación de generaciones). El archivo es una plantilla válida con dos
 * headers (los 32 KiB iniciales) seguida de bytes arbitrarios; además el fuzzer puede escribir la pista de footer de la
 * Zona C (que no está autenticada). Solo puede ocurrir {@link ExcepcionBoveda}: nada de otras excepciones ni
 * cuelgues, y el archivo no se modifica.
 *
 * <p>Corpus semilla: {@code src/test/resources/.../FuzzContenedorTestInputs}, generado por
 * {@code ./gradlew :cryptokipu-infra:generarSemillasFuzz}.
 */
class FuzzContenedorTest {

    private static final int OFFSET_DATOS_MIJA = 2 * 16 * 1024;
    private static final byte[] PLANTILLA_MIJA = plantilla();

    private static byte[] plantilla() {
        try (InputStream entradaMija = FuzzContenedorTest.class.getResourceAsStream("plantilla-headers.bin")) {
            if (entradaMija == null) {
                throw new IllegalStateException("Falta plantilla-headers.bin: ejecutar generarSemillasFuzz");
            }
            return entradaMija.readAllBytes();
        } catch (IOException errorMija) {
            throw new UncheckedIOException(errorMija);
        }
    }

    @FuzzTest(maxDuration = "2m")
    void abrirNuncaFallaConUnErrorNoControlado(FuzzedDataProvider datosMija) throws IOException {
        long pistaMija = datosMija.consumeLong();
        byte[] colaMija = datosMija.consumeRemainingAsBytes();
        byte[] archivoMija = new byte[OFFSET_DATOS_MIJA + colaMija.length];
        System.arraycopy(PLANTILLA_MIJA, 0, archivoMija, 0, OFFSET_DATOS_MIJA);
        System.arraycopy(colaMija, 0, archivoMija, OFFSET_DATOS_MIJA, colaMija.length);
        // La pista de footer (Zona C) no está autenticada: se escribe en ambas copias del header.
        for (int copiaMija = 0; copiaMija < 2; copiaMija++) {
            ByteBuffer.wrap(archivoMija).putLong(copiaMija * 16 * 1024 + HeaderCodec.OFFSET_ZONA_C_MIJA + 4,
                    pistaMija & Long.MAX_VALUE);
        }
        Path rutaMija = Files.createTempFile("fuzz-contenedor", ".kipu");
        try {
            Files.write(rutaMija, archivoMija);
            try (ContenedorKipu contenedorMija = ContenedorKipu.abrir(rutaMija, ContenedorKipu.Opciones.porDefecto())) {
                contenedorMija.header();
            } catch (ExcepcionBoveda _) {
                // Error controlado: archivo dañado o sin footer.
            }
        } finally {
            Files.deleteIfExists(rutaMija);
        }
    }
}
