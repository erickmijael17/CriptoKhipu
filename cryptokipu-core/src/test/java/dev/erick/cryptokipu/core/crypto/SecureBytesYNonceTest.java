package dev.erick.cryptokipu.core.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SecureBytesYNonceTest {

    @Test
    void cerrarBorraLosBytesYBloqueaElAcceso() {
        byte[] datosMija = {1, 2, 3, 4};
        SecureBytes seguroMija = SecureBytes.envolver(datosMija);

        seguroMija.close();

        assertArrayEquals(new byte[4], datosMija);
        assertTrue(seguroMija.estaCerrado());
        assertThrows(IllegalStateException.class, seguroMija::bytes);
        assertThrows(IllegalStateException.class, seguroMija::longitud);
    }

    @Test
    void copiaDeNoComparteElArregloOriginal() {
        byte[] datosMija = {5, 6, 7};
        try (SecureBytes seguroMija = SecureBytes.copiaDe(datosMija)) {
            datosMija[0] = 0;
            assertEquals(5, seguroMija.bytes()[0]);
        }
    }

    @Test
    void toStringNuncaMuestraElContenido() {
        try (SecureBytes seguroMija = SecureBytes.copiaDe(new byte[] {0x41, 0x42})) {
            assertEquals("SecureBytes[***]", seguroMija.toString());
        }
    }

    @Test
    void nonceDeBloqueEsCerosMasIndice() {
        assertArrayEquals(HexFormat.of().parseHex("000000000000000000000000"), ProveedorNonce.deBloque(0));
        assertArrayEquals(HexFormat.of().parseHex("000000000000000000000102"), ProveedorNonce.deBloque(258));
        assertThrows(IllegalArgumentException.class, () -> ProveedorNonce.deBloque(-1));
    }

    @Test
    void noncesAleatoriosMiden12YNoSeRepiten() {
        ProveedorNonce proveedorMija = new ProveedorNonce(FuenteAleatoria.delSistema());
        Set<String> vistosMija = new HashSet<>();
        for (int indiceMija = 0; indiceMija < 10_000; indiceMija++) {
            byte[] nonceMija = proveedorMija.aleatorio();
            assertEquals(12, nonceMija.length);
            assertTrue(vistosMija.add(HexFormat.of().formatHex(nonceMija)));
        }
        assertFalse(vistosMija.isEmpty());
    }
}
