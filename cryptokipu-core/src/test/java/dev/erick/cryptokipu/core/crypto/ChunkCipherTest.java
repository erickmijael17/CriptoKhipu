package dev.erick.cryptokipu.core.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erick.cryptokipu.core.error.ErrorBoveda;
import dev.erick.cryptokipu.core.error.ExcepcionBoveda;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class ChunkCipherTest {

    private static final int MIB_MIJA = ChunkCipher.TAMANO_BLOQUE_MIJA;
    private static final int BLOQUE_CIFRADO_MIJA = ChunkCipher.TAMANO_BLOQUE_CIFRADO_MIJA;

    private SecureBytes claveMija;
    private byte[] fileIdMija;

    @BeforeEach
    void preparar() {
        FuenteAleatoria fuenteMija = FuenteAleatoria.delSistema();
        claveMija = SecureBytes.envolver(fuenteMija.bytes(32));
        fileIdMija = fuenteMija.bytes(16);
    }

    @AfterEach
    void limpiar() {
        claveMija.close();
    }

    @ParameterizedTest(name = "{0} bytes")
    @ValueSource(ints = {0, 1, 1024, 1 << 20, (1 << 20) + 1, 3 * (1 << 20) - 7, 50 * 1000 * 1000})
    void idaYVueltaConservaLosDatos(int tamanoMija) throws IOException {
        byte[] originalMija = aleatorios(tamanoMija);

        byte[] cifradoMija = cifrar(originalMija);

        assertEquals(ChunkCipher.tamanoCifrado(tamanoMija), cifradoMija.length);
        assertArrayEquals(originalMija, descifrar(cifradoMija, fileIdMija));
    }

    @Test
    void archivoVacioEsUnSoloBloqueDeSoloTag() throws IOException {
        assertEquals(AesGcmAead.LONGITUD_TAG_MIJA, cifrar(new byte[0]).length);
    }

    @Test
    void detectaIntercambioDeBloques() throws IOException {
        byte[] cifradoMija = cifrar(aleatorios(3 * MIB_MIJA));
        byte[] primeroMija = Arrays.copyOfRange(cifradoMija, 0, BLOQUE_CIFRADO_MIJA);
        System.arraycopy(cifradoMija, BLOQUE_CIFRADO_MIJA, cifradoMija, 0, BLOQUE_CIFRADO_MIJA);
        System.arraycopy(primeroMija, 0, cifradoMija, BLOQUE_CIFRADO_MIJA, BLOQUE_CIFRADO_MIJA);

        esperarIntegridadFallida(cifradoMija, fileIdMija);
    }

    @Test
    void detectaQueSeQuitoElUltimoBloque() throws IOException {
        byte[] cifradoMija = cifrar(aleatorios(2 * MIB_MIJA + 100));

        esperarIntegridadFallida(Arrays.copyOf(cifradoMija, 2 * BLOQUE_CIFRADO_MIJA), fileIdMija);
    }

    @Test
    void detectaTruncadoDentroDeUnBloque() throws IOException {
        byte[] cifradoMija = cifrar(aleatorios(MIB_MIJA + 500));

        esperarIntegridadFallida(Arrays.copyOf(cifradoMija, cifradoMija.length - 1), fileIdMija);
    }

    @Test
    void detectaBytesAgregadosAlFinal() throws IOException {
        byte[] cifradoMija = cifrar(aleatorios(1000));

        esperarIntegridadFallida(Arrays.copyOf(cifradoMija, cifradoMija.length + 10), fileIdMija);
    }

    @Test
    void detectaFlujoCifradoVacio() {
        esperarIntegridadFallida(new byte[0], fileIdMija);
    }

    @Test
    void detectaUnBitCambiado() throws IOException {
        byte[] cifradoMija = cifrar(aleatorios(2 * MIB_MIJA));
        cifradoMija[MIB_MIJA + 1234] ^= 0x10;

        esperarIntegridadFallida(cifradoMija, fileIdMija);
    }

    @Test
    void detectaFileIdDistinto() throws IOException {
        byte[] cifradoMija = cifrar(aleatorios(1000));
        byte[] otroFileIdMija = fileIdMija.clone();
        otroFileIdMija[0] ^= 0x01;

        esperarIntegridadFallida(cifradoMija, otroFileIdMija);
    }

    @Test
    void rechazaFileIdInvalido() {
        assertThrows(IllegalArgumentException.class,
                () -> ChunkCipher.cifrar(claveMija, new byte[8], new ByteArrayInputStream(new byte[1]),
                        new ByteArrayOutputStream()));
    }

    private byte[] cifrar(byte[] originalMija) throws IOException {
        ByteArrayOutputStream salidaMija = new ByteArrayOutputStream();
        ChunkCipher.cifrar(claveMija, fileIdMija, new ByteArrayInputStream(originalMija), salidaMija);
        return salidaMija.toByteArray();
    }

    private byte[] descifrar(byte[] cifradoMija, byte[] idMija) throws IOException {
        ByteArrayOutputStream salidaMija = new ByteArrayOutputStream();
        ChunkCipher.descifrar(claveMija, idMija, new ByteArrayInputStream(cifradoMija), salidaMija);
        return salidaMija.toByteArray();
    }

    private void esperarIntegridadFallida(byte[] cifradoMija, byte[] idMija) {
        ExcepcionBoveda excepcionMija = assertThrows(ExcepcionBoveda.class, () -> descifrar(cifradoMija, idMija));
        ErrorBoveda.IntegridadFallida errorMija =
                assertInstanceOf(ErrorBoveda.IntegridadFallida.class, excepcionMija.error());
        assertEquals(ErrorBoveda.IntegridadFallida.Zona.BLOQUE, errorMija.zonaMija());
    }

    private static byte[] aleatorios(int tamanoMija) {
        byte[] datosMija = new byte[tamanoMija];
        new Random(tamanoMija).nextBytes(datosMija);
        return datosMija;
    }
}
