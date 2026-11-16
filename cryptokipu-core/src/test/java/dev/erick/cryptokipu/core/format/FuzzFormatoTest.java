package dev.erick.cryptokipu.core.format;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import dev.erick.cryptokipu.core.crypto.FuenteAleatoria;
import dev.erick.cryptokipu.core.error.ExcepcionBoveda;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.zip.CRC32C;

/**
 * Fuzzing (Jazzer) de los parsers del formato: header, manifest y footer. Los parsers solo pueden fallar con
 * {@link ExcepcionBoveda} (error controlado); cualquier otra excepción, un cuelgue o un agotamiento de memoria es un
 * hallazgo. Como el CRC rechazaría casi todo, el objetivo lo recalcula para que el fuzzer llegue a la lógica profunda.
 *
 * <p>Sin {@code JAZZER_FUZZ=1} cada método solo repite el corpus de {@code src/test/resources} (regresión, en
 * {@code check}); con la variable (tarea {@code fuzzLargo}) fuzzea de verdad. Cada caída encontrada se corrige y su
 * entrada se guarda en el corpus como prueba de regresión.
 */
class FuzzFormatoTest {

    private static final FuenteAleatoria FUENTE_MIJA = FuenteAleatoria.delSistema();

    private static void recalcularCrcHeader(byte[] bloqueMija) {
        CRC32C crcMija = new CRC32C();
        crcMija.update(bloqueMija, 0, HeaderCodec.OFFSET_CRC_MIJA);
        ByteBuffer.wrap(bloqueMija).putInt(HeaderCodec.OFFSET_CRC_MIJA, (int) crcMija.getValue());
    }

    @FuzzTest(maxDuration = "2m")
    void headerNuncaFallaConUnErrorNoControlado(FuzzedDataProvider datosMija) {
        boolean arreglarCrcMija = datosMija.consumeBoolean();
        byte[] bloqueMija = new byte[LimitesFormato.TAMANO_HEADER_MIJA];
        byte[] entradaMija = datosMija.consumeRemainingAsBytes();
        System.arraycopy(entradaMija, 0, bloqueMija, 0, Math.min(entradaMija.length, bloqueMija.length));
        if (arreglarCrcMija) {
            recalcularCrcHeader(bloqueMija);
        }
        try {
            Header headerMija = HeaderCodec.decodificar(bloqueMija);
            // Invariante: lo que se decodifica se vuelve a codificar y decodificar igual.
            Header otroMija = HeaderCodec.decodificar(HeaderCodec.codificar(headerMija, FUENTE_MIJA));
            assertEquals(headerMija.zonaAMija(), otroMija.zonaAMija());
            assertEquals(headerMija.slotsMija(), otroMija.slotsMija());
            assertEquals(headerMija.generacionMija(), otroMija.generacionMija());
        } catch (ExcepcionBoveda _) {
            // Error controlado: el archivo se rechaza.
        }
    }

    @FuzzTest(maxDuration = "2m")
    void manifestNuncaFallaConUnErrorNoControlado(byte[] textoMija) {
        try {
            Manifest manifestMija = ManifestCodec.deserializar(textoMija);
            Manifest otroMija = ManifestCodec.deserializar(ManifestCodec.serializar(manifestMija));
            assertEquals(manifestMija, otroMija);
        } catch (ExcepcionBoveda _) {
            // Error controlado.
        }
    }

    @FuzzTest(maxDuration = "2m")
    void footerNuncaFallaConUnErrorNoControlado(FuzzedDataProvider datosMija) {
        long posicionMija = datosMija.consumeLong();
        boolean arreglarMija = datosMija.consumeBoolean();
        byte[] bytesMija = datosMija.consumeRemainingAsBytes();
        if (arreglarMija && bytesMija.length == Footer.TAMANO_MIJA) {
            System.arraycopy(LimitesFormato.MAGIC_FOOTER_MIJA, 0, bytesMija, Footer.OFFSET_MAGIC_MIJA,
                    LimitesFormato.MAGIC_FOOTER_MIJA.length);
            CRC32C crcMija = new CRC32C();
            crcMija.update(bytesMija, 0, Footer.TAMANO_MIJA - 4);
            ByteBuffer.wrap(bytesMija).putInt(Footer.TAMANO_MIJA - 4, (int) crcMija.getValue());
        }
        Optional<Footer> footerMija = Footer.leer(bytesMija, posicionMija);
        footerMija.ifPresent(encontradoMija -> assertArrayEquals(bytesMija, encontradoMija.codificar()));
    }
}
