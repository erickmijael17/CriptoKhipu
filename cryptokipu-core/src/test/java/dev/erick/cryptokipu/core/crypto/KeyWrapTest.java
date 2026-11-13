package dev.erick.cryptokipu.core.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class KeyWrapTest {

    private final FuenteAleatoria fuenteMija = FuenteAleatoria.delSistema();
    private final KeyWrap envolturaMija = new KeyWrap(new ProveedorNonce(fuenteMija));
    private final byte[] zonaAMija = "zona-A-del-header".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    @Test
    void idaYVueltaDevuelveLaMismaClaveMaestra() {
        try (SecureBytes kekMija = SecureBytes.envolver(fuenteMija.bytes(32));
                SecureBytes maestraMija = SecureBytes.envolver(fuenteMija.bytes(32))) {
            KeyWrap.MkEnvuelta envueltaMija = envolturaMija.envolver(kekMija, maestraMija, zonaAMija);

            assertEquals(12, envueltaMija.nonceMija().length);
            assertEquals(48, envueltaMija.cifradoMija().length);
            Optional<SecureBytes> resultadoMija = KeyWrap.desenvolver(kekMija, envueltaMija, zonaAMija);
            assertTrue(resultadoMija.isPresent());
            try (SecureBytes recuperadaMija = resultadoMija.get()) {
                assertArrayEquals(maestraMija.bytes(), recuperadaMija.bytes());
            }
        }
    }

    @Test
    void dosEnvolturasSeguidasUsanNoncesDistintos() {
        try (SecureBytes kekMija = SecureBytes.envolver(fuenteMija.bytes(32));
                SecureBytes maestraMija = SecureBytes.envolver(fuenteMija.bytes(32))) {
            KeyWrap.MkEnvuelta primeraMija = envolturaMija.envolver(kekMija, maestraMija, zonaAMija);
            KeyWrap.MkEnvuelta segundaMija = envolturaMija.envolver(kekMija, maestraMija, zonaAMija);

            assertFalse(Arrays.equals(primeraMija.nonceMija(), segundaMija.nonceMija()));
            assertFalse(Arrays.equals(primeraMija.cifradoMija(), segundaMija.cifradoMija()));
        }
    }

    @Test
    void kekEquivocadaNoAbre() {
        try (SecureBytes kekMija = SecureBytes.envolver(fuenteMija.bytes(32));
                SecureBytes otraKekMija = SecureBytes.envolver(fuenteMija.bytes(32));
                SecureBytes maestraMija = SecureBytes.envolver(fuenteMija.bytes(32))) {
            KeyWrap.MkEnvuelta envueltaMija = envolturaMija.envolver(kekMija, maestraMija, zonaAMija);

            assertTrue(KeyWrap.desenvolver(otraKekMija, envueltaMija, zonaAMija).isEmpty());
        }
    }

    @Test
    void zonaAModificadaNoAbre() {
        try (SecureBytes kekMija = SecureBytes.envolver(fuenteMija.bytes(32));
                SecureBytes maestraMija = SecureBytes.envolver(fuenteMija.bytes(32))) {
            KeyWrap.MkEnvuelta envueltaMija = envolturaMija.envolver(kekMija, maestraMija, zonaAMija);
            byte[] zonaAlteradaMija = zonaAMija.clone();
            zonaAlteradaMija[0] ^= 0x01;

            assertTrue(KeyWrap.desenvolver(kekMija, envueltaMija, zonaAlteradaMija).isEmpty());
        }
    }

    @Test
    void elRecordEsInmutableYNoMuestraBytes() {
        byte[] nonceMija = new byte[12];
        KeyWrap.MkEnvuelta envueltaMija = new KeyWrap.MkEnvuelta(nonceMija, new byte[48]);
        nonceMija[0] = 9;
        envueltaMija.nonceMija()[1] = 9;

        assertArrayEquals(new byte[12], envueltaMija.nonceMija());
        assertEquals("MkEnvuelta[***]", envueltaMija.toString());
    }
}
