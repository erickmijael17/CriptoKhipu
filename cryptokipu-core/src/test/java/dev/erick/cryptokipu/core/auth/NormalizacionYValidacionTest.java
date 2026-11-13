package dev.erick.cryptokipu.core.auth;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import org.junit.jupiter.api.Test;

class NormalizacionYValidacionTest {

    private static char[] c(String textoMija) {
        return textoMija.toCharArray();
    }

    // ------------------------------------------------------------------ normalización F3

    @Test
    void tildesEnieMayusculasYEspaciosSeIgnoran() {
        assertArrayEquals(c("mi perro nato"), CredentialNormalizer.normalizarFrase(c("Mi Pérro  Ñato")));
        assertArrayEquals(c("mi perro nato"), CredentialNormalizer.normalizarFrase(c("  mi\tperro  nato \n")));
        assertArrayEquals(c("cancion de cuna"), CredentialNormalizer.normalizarFrase(c("CANCIÓN DE CUÑA".replace('Ñ', 'N'))));
    }

    @Test
    void noModificaLaEntrada() {
        char[] entradaMija = c("Ñandú Azul");
        CredentialNormalizer.normalizarFrase(entradaMija);
        assertArrayEquals(c("Ñandú Azul"), entradaMija);
    }

    @Property(tries = 2_000)
    void normalizarEsIdempotente(@ForAll String textoMija) {
        char[] unaVezMija = CredentialNormalizer.normalizarFrase(textoMija.toCharArray());
        char[] dosVecesMija = CredentialNormalizer.normalizarFrase(unaVezMija);
        assertArrayEquals(unaVezMija, dosVecesMija);
    }

    // ------------------------------------------------------------------ validación

    @Test
    void credencialesValidasNoTienenReglasIncumplidas() {
        assertTrue(ValidadorFactores.validar(c("123456789012"), c("Clave Segura 2026"),
                c("Mi perro se llama Toby")).isEmpty());
    }

    @Test
    void devuelveTodasLasReglasIncumplidasALaVez() {
        List<ReglaIncumplida> reglasMija = ValidadorFactores.validar(c("12345a"), c("ñandú"), c("Mi Pérro  Ñato"));

        assertEquals(List.of(ReglaIncumplida.F1_LONGITUD, ReglaIncumplida.F1_SOLO_DIGITOS,
                ReglaIncumplida.F2_LONGITUD, ReglaIncumplida.F2_SOLO_ASCII, ReglaIncumplida.F2_FALTA_MAYUSCULA,
                ReglaIncumplida.F2_FALTA_DIGITO, ReglaIncumplida.F3_LONGITUD), reglasMija);
    }

    @Test
    void limitesDeLongitud() {
        assertTrue(ValidadorFactores.validar(c("1".repeat(25)), c("Aa1" + "x".repeat(22)), c("a".repeat(25))).isEmpty());
        assertEquals(List.of(ReglaIncumplida.F1_LONGITUD, ReglaIncumplida.F2_LONGITUD, ReglaIncumplida.F3_LONGITUD),
                ValidadorFactores.validar(c("1".repeat(26)), c("Aa1" + "x".repeat(23)), c("a".repeat(26))));
        assertEquals(List.of(ReglaIncumplida.F1_LONGITUD), ValidadorFactores.validar(c("1".repeat(11)),
                c("Aa1" + "x".repeat(9)), c("a".repeat(15))));
    }

    @Test
    void laLongitudDeF3SeCuentaDespuesDeNormalizar() {
        // 20 caracteres escritos, 14 tras recortar los espacios de los extremos → no alcanza el mínimo de 15.
        assertEquals(List.of(ReglaIncumplida.F3_LONGITUD),
                ValidadorFactores.validar(c("123456789012"), c("Clave Segura 2026"), c("   abcdefghijklmn   ")));
        assertTrue(ValidadorFactores.validar(c("123456789012"), c("Clave Segura 2026"),
                c("   abcdefghijklmno   ")).isEmpty());
    }

    @Test
    void f2AceptaEspacioYSimbolosAscii() {
        assertTrue(ValidadorFactores.validar(c("123456789012"), c("Hola Mundo #1 ~"), c("frase larga de prueba"))
                .isEmpty());
    }

    // ------------------------------------------------------------------ codificación

    @Test
    void elPrefijoDeLongitudEvitaAmbiguedades() {
        assertFalse(Arrays.equals(CodificadorEntrada.codificar(c("ab"), c("c")),
                CodificadorEntrada.codificar(c("a"), c("bc"))));
    }

    @Test
    void formatoLongitudBigEndianMasUtf8() {
        byte[] codificadoMija = CodificadorEntrada.codificar(c("ñ"), c(""));
        ByteBuffer bufferMija = ByteBuffer.wrap(codificadoMija);

        assertEquals(2, bufferMija.getInt());
        assertEquals((byte) 0xC3, bufferMija.get());
        assertEquals((byte) 0xB1, bufferMija.get());
        assertEquals(0, bufferMija.getInt());
        assertFalse(bufferMija.hasRemaining());
    }

    // ------------------------------------------------------------------ credenciales

    @Test
    void credencialesSeBorranAlCerrarYNoSeMuestran() {
        char[] pinMija = c("123456789012");
        byte[] huellaMija = new byte[32];
        Arrays.fill(huellaMija, (byte) 7);
        Credenciales credencialesMija = new Credenciales(pinMija, c("Clave Segura 2026"), c("frase"), huellaMija);

        assertEquals("***", credencialesMija.toString());
        credencialesMija.close();

        assertArrayEquals(new char[12], pinMija);
        assertArrayEquals(new byte[32], huellaMija);
    }
}
