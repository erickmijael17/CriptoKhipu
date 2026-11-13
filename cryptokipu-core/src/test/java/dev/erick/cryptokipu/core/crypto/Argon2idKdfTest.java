package dev.erick.cryptokipu.core.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class Argon2idKdfTest {

    private static final ParametrosArgon2 PARAMETROS_RAPIDOS_MIJA = new ParametrosArgon2(32, 3, 4);

    private static byte[] relleno(int longitudMija, int valorMija) {
        byte[] datosMija = new byte[longitudMija];
        Arrays.fill(datosMija, (byte) valorMija);
        return datosMija;
    }

    /** RFC 9106, sección 5.3 (Argon2id, versión 0x13). */
    @Test
    void vectorOficialRfc9106() {
        byte[] etiquetaMija = Argon2idKdf.derivar(relleno(32, 0x01), relleno(16, 0x02), relleno(8, 0x03),
                relleno(12, 0x04), PARAMETROS_RAPIDOS_MIJA, 32);

        assertArrayEquals(
                HexFormat.of().parseHex("0d640df58d78766c08c037a34a8b53c9d01ef0452d75b65eb52520e96b01e659"),
                etiquetaMija);
    }

    @Test
    void esDeterministaYDevuelve32Bytes() {
        try (SecureBytes primeraMija = Argon2idKdf.derivar(relleno(20, 7), relleno(32, 9), null, PARAMETROS_RAPIDOS_MIJA);
                SecureBytes segundaMija = Argon2idKdf.derivar(relleno(20, 7), relleno(32, 9), null, PARAMETROS_RAPIDOS_MIJA)) {
            assertEquals(32, primeraMija.longitud());
            assertArrayEquals(primeraMija.bytes(), segundaMija.bytes());
        }
    }

    @Test
    void elSecretoDeLaLlaveCambiaElResultado() {
        try (SecureBytes sinLlaveMija = Argon2idKdf.derivar(relleno(20, 7), relleno(32, 9), null, PARAMETROS_RAPIDOS_MIJA);
                SecureBytes conLlaveMija = Argon2idKdf.derivar(relleno(20, 7), relleno(32, 9), relleno(32, 1),
                        PARAMETROS_RAPIDOS_MIJA)) {
            assertFalse(Arrays.equals(sinLlaveMija.bytes(), conLlaveMija.bytes()));
        }
    }

    @Test
    void rechazaSalCorta() {
        assertThrows(IllegalArgumentException.class,
                () -> Argon2idKdf.derivar(relleno(20, 7), relleno(4, 9), null, PARAMETROS_RAPIDOS_MIJA));
    }

    @Test
    void perfilesDelPlan() {
        assertEquals(256L * 1024 * 1024, ParametrosArgon2.ESTANDAR_MIJA.memoriaBytes());
        assertEquals(512L * 1024 * 1024, ParametrosArgon2.ALTO_MIJA.memoriaBytes());
        assertEquals(1024L * 1024 * 1024, ParametrosArgon2.PARANOICO_MIJA.memoriaBytes());
        assertEquals(2, ParametrosArgon2.ESTANDAR_MIJA.iteracionesMija());
        assertEquals(4, ParametrosArgon2.PARANOICO_MIJA.paralelismoMija());
        assertThrows(IllegalArgumentException.class, () -> new ParametrosArgon2(16, 3, 4));
        assertThrows(IllegalArgumentException.class, () -> new ParametrosArgon2(1024, 0, 4));
    }
}
