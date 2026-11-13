package dev.erick.cryptokipu.core.auth;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erick.cryptokipu.core.crypto.FuenteAleatoria;
import dev.erick.cryptokipu.core.crypto.SecureBytes;
import dev.erick.cryptokipu.core.error.ErrorBoveda;
import dev.erick.cryptokipu.core.error.ExcepcionBoveda;
import dev.erick.cryptokipu.core.progreso.ReporteProgreso;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.regex.Pattern;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

class TokenYLlaveTest {

    private static final FuenteAleatoria FUENTE_MIJA = FuenteAleatoria.delSistema();
    private static final Pattern FORMATO_MIJA =
            Pattern.compile("CKP(-[0-9A-HJKMNP-TV-Z]{5}){5}-[0-9A-HJKMNP-TV-Z*~$=U]");

    // ------------------------------------------------------------------ tokens

    @Test
    void formatoYLecturaDeIdaYVuelta() {
        try (TokenRecuperacion tokenMija = TokenRecuperacion.generar(FUENTE_MIJA)) {
            String formateadoMija = new String(tokenMija.formatear());

            assertTrue(FORMATO_MIJA.matcher(formateadoMija).matches(), formateadoMija);
            assertEquals(tokenMija, TokenRecuperacion.leer(formateadoMija.toCharArray()));
        }
    }

    @Test
    void lecturaTolerante() {
        try (TokenRecuperacion tokenMija = TokenRecuperacion.generar(FUENTE_MIJA)) {
            String formateadoMija = new String(tokenMija.formatear());
            String sinPrefijoMija = formateadoMija.substring(4).replace("-", " ").toLowerCase();
            String confusionesMija = formateadoMija.replace('0', 'O').replace('1', 'l');

            assertEquals(tokenMija, TokenRecuperacion.leer(sinPrefijoMija.toCharArray()));
            assertEquals(tokenMija, TokenRecuperacion.leer(confusionesMija.toCharArray()));
            assertEquals(tokenMija, TokenRecuperacion.leer(("  " + formateadoMija + "\n").toCharArray()));
        }
    }

    @Property(tries = 300)
    void cualquierSimboloCambiadoSeDetectaAntesDeDerivar(@ForAll @IntRange(min = 0, max = 24) int posicionMija,
            @ForAll @IntRange(min = 1, max = 31) int desplazamientoMija) {
        try (TokenRecuperacion tokenMija = TokenRecuperacion.generar(FUENTE_MIJA)) {
            char[] simbolosMija = tokenMija.simbolosMija().clone();
            String alfabetoMija = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
            int valorMija = alfabetoMija.indexOf(simbolosMija[posicionMija]);
            char[] formateadoMija = tokenMija.formatear();
            char verificacionMija = formateadoMija[formateadoMija.length - 1];
            simbolosMija[posicionMija] = alfabetoMija.charAt((valorMija + desplazamientoMija) % 32);
            char[] alteradoMija = (new String(simbolosMija) + verificacionMija).toCharArray();

            esperarMalEscrito(alteradoMija);
        }
    }

    @Test
    void longitudOSimbolosInvalidosSonTokenMalEscrito() {
        esperarMalEscrito("CKP-ABCDE".toCharArray());
        esperarMalEscrito("".toCharArray());
        esperarMalEscrito("CKP-UUUUU-00000-00000-00000-00000-0".toCharArray()); // U solo vale como verificación
    }

    @Test
    void tokensDistintosYSinMostrarse() {
        Set<TokenRecuperacion> vistosMija = new HashSet<>();
        for (int indiceMija = 0; indiceMija < 1_000; indiceMija++) {
            assertTrue(vistosMija.add(TokenRecuperacion.generar(FUENTE_MIJA)));
        }
        TokenRecuperacion tokenMija = TokenRecuperacion.generar(FUENTE_MIJA);
        assertEquals("***", tokenMija.toString());
        tokenMija.close();
        assertArrayEquals(new char[25], tokenMija.simbolosMija());
    }

    // ------------------------------------------------------------------ llave de seguridad

    @Test
    void huellaEsSha256DeLosBytesExactos() throws IOException {
        try (SecureBytes huellaMija = KeyfileDigest.huella(
                new ByteArrayInputStream("abc".getBytes(StandardCharsets.US_ASCII)), 3, ReporteProgreso.NINGUNO_MIJA)) {
            assertArrayEquals(HexFormat.of().parseHex(
                    "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"), huellaMija.bytes());
        }
    }

    @Test
    void huellaReportaProgresoYSePuedeCancelar() throws IOException {
        long[] ultimoMija = {0};
        KeyfileDigest.huella(new ByteArrayInputStream(new byte[3 * (1 << 20) + 5]), 3 * (1 << 20) + 5,
                (hechoMija, _) -> ultimoMija[0] = hechoMija).close();
        assertEquals(3 * (1 << 20) + 5, ultimoMija[0]);

        ReporteProgreso cancelarMija = new ReporteProgreso() {
            @Override
            public void avanzar(long hechoMija, long totalMija) {
            }

            @Override
            public boolean cancelado() {
                return true;
            }
        };
        ExcepcionBoveda excepcionMija = assertThrows(ExcepcionBoveda.class,
                () -> KeyfileDigest.huella(new ByteArrayInputStream(new byte[10]), 10, cancelarMija));
        assertInstanceOf(ErrorBoveda.OperacionCancelada.class, excepcionMija.error());
    }

    @Test
    void llaveGeneradaMide64Bytes() {
        assertEquals(64, KeyfileDigest.generarLlave(FUENTE_MIJA).length);
    }

    private static void esperarMalEscrito(char[] entradaMija) {
        ExcepcionBoveda excepcionMija = assertThrows(ExcepcionBoveda.class, () -> TokenRecuperacion.leer(entradaMija));
        assertInstanceOf(ErrorBoveda.TokenMalEscrito.class, excepcionMija.error());
    }
}
