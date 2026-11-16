package dev.erick.cryptokipu.core.auth;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/** Credenciales, cambios de credenciales y codificación de la entrada de Argon2 (mutantes de PIT, Fase 9). */
class CredencialesYCambiosTest {

    private static byte[] huella(int valorMija) {
        byte[] huellaMija = new byte[32];
        java.util.Arrays.fill(huellaMija, (byte) valorMija);
        return huellaMija;
    }

    private static Credenciales vigentes() {
        return new Credenciales("111111111111".toCharArray(), "Clave Vigente 1".toCharArray(),
                "frase vigente uno ya".toCharArray(), huella(7));
    }

    private static CambioCredenciales cambio(String pinMija, String claveMija, String fraseMija,
            CambioCredenciales.CambioLlave llaveMija) {
        return new CambioCredenciales(Optional.ofNullable(pinMija).map(String::toCharArray),
                Optional.ofNullable(claveMija).map(String::toCharArray),
                Optional.ofNullable(fraseMija).map(String::toCharArray), llaveMija, OptionalInt.empty());
    }

    // ------------------------------------------------------------------ Credenciales

    @Test
    void credencialesValidanLaHuellaYSeBorranAlCerrar() {
        char[] pinMija = "123".toCharArray();
        char[] claveMija = "abc".toCharArray();
        char[] fraseMija = "xyz".toCharArray();
        byte[] llaveMija = huella(9);
        Credenciales credencialesMija = new Credenciales(pinMija, claveMija, fraseMija, llaveMija);
        assertTrue(credencialesMija.tieneLlave());
        assertFalse(new Credenciales(pinMija, claveMija, fraseMija, null).tieneLlave());
        assertThrows(IllegalArgumentException.class, () -> new Credenciales(pinMija, claveMija, fraseMija, new byte[31]));
        assertThrows(IllegalArgumentException.class, () -> new Credenciales(pinMija, claveMija, fraseMija, new byte[33]));
        assertThrows(NullPointerException.class, () -> new Credenciales(null, claveMija, fraseMija, null));
        assertThrows(NullPointerException.class, () -> new Credenciales(pinMija, null, fraseMija, null));
        assertThrows(NullPointerException.class, () -> new Credenciales(pinMija, claveMija, null, null));

        credencialesMija.close();

        assertArrayEquals(new char[3], pinMija);
        assertArrayEquals(new char[3], claveMija);
        assertArrayEquals(new char[3], fraseMija);
        assertArrayEquals(new byte[32], llaveMija);
    }

    @Test
    void credencialesNoSeMuestranNiSeCompararPorContenido() {
        Credenciales unaMija = vigentes();
        Credenciales otraMija = vigentes();
        assertEquals("***", unaMija.toString());
        assertEquals(unaMija, unaMija);
        assertNotEquals(unaMija, otraMija, "la igualdad es por identidad: nunca se comparan secretos");
        assertEquals(unaMija.hashCode(), unaMija.hashCode());
        assertNotEquals(unaMija, null);
        // Cerrar sin llave tampoco falla.
        new Credenciales(new char[1], new char[1], new char[1], null).close();
    }

    // ------------------------------------------------------------------ CambioCredenciales

    @Test
    void combinarUsaLoNuevoYConservaLoVigente() {
        Credenciales vigentesMija = vigentes();
        try (CambioCredenciales cambioMija = cambio(null, "Clave Nueva 22", null, new CambioCredenciales.CambioLlave.SinCambio());
                Credenciales resultadoMija = cambioMija.combinar(vigentesMija)) {
            assertArrayEquals("111111111111".toCharArray(), resultadoMija.pinMija());
            assertArrayEquals("Clave Nueva 22".toCharArray(), resultadoMija.contrasenaMija());
            assertArrayEquals("frase vigente uno ya".toCharArray(), resultadoMija.fraseMija());
            assertArrayEquals(huella(7), resultadoMija.huellaLlaveMija());
            assertNotEquals(System.identityHashCode(vigentesMija.huellaLlaveMija()),
                    System.identityHashCode(resultadoMija.huellaLlaveMija()), "la huella se copia");
        }
        try (CambioCredenciales cambioMija = cambio("999999999999", null, "frase nueva dos ya", new CambioCredenciales.CambioLlave.Quitar());
                Credenciales resultadoMija = cambioMija.combinar(vigentesMija)) {
            assertArrayEquals("999999999999".toCharArray(), resultadoMija.pinMija());
            assertArrayEquals("Clave Vigente 1".toCharArray(), resultadoMija.contrasenaMija());
            assertArrayEquals("frase nueva dos ya".toCharArray(), resultadoMija.fraseMija());
            assertNull(resultadoMija.huellaLlaveMija());
        }
        try (CambioCredenciales cambioMija = cambio(null, null, null, new CambioCredenciales.CambioLlave.Nueva(huella(3)));
                Credenciales resultadoMija = cambioMija.combinar(vigentesMija)) {
            assertArrayEquals(huella(3), resultadoMija.huellaLlaveMija());
        }
        // Sin llave vigente y sin cambio de llave, sigue sin llave.
        Credenciales sinLlaveMija = new Credenciales("1".toCharArray(), "a".toCharArray(), "b".toCharArray(), null);
        try (CambioCredenciales cambioMija = cambio(null, null, null, new CambioCredenciales.CambioLlave.SinCambio());
                Credenciales resultadoMija = cambioMija.combinar(sinLlaveMija)) {
            assertNull(resultadoMija.huellaLlaveMija());
        }
        // Combinar no borra lo vigente ni lo nuevo.
        assertArrayEquals("111111111111".toCharArray(), vigentesMija.pinMija());
    }

    @Test
    void completasExigeLosTresFactoresYUnCambioDeLlaveExplicito() {
        List<ReglaIncumplida> esperadoMija = List.of(ReglaIncumplida.FACTORES_INCOMPLETOS);
        for (CambioCredenciales incompletoMija : List.of(
                cambio(null, "Clave Nueva 22", "frase nueva dos ya", new CambioCredenciales.CambioLlave.Quitar()),
                cambio("999999999999", null, "frase nueva dos ya", new CambioCredenciales.CambioLlave.Quitar()),
                cambio("999999999999", "Clave Nueva 22", null, new CambioCredenciales.CambioLlave.Quitar()),
                cambio("999999999999", "Clave Nueva 22", "frase nueva dos ya", new CambioCredenciales.CambioLlave.SinCambio()))) {
            assertEquals(esperadoMija, assertThrows(ExcepcionValidacion.class, incompletoMija::completas).reglas());
        }
        try (Credenciales quitarMija = cambio("999999999999", "Clave Nueva 22", "frase nueva dos ya",
                new CambioCredenciales.CambioLlave.Quitar()).completas()) {
            assertNull(quitarMija.huellaLlaveMija());
            assertArrayEquals("999999999999".toCharArray(), quitarMija.pinMija());
            assertArrayEquals("Clave Nueva 22".toCharArray(), quitarMija.contrasenaMija());
            assertArrayEquals("frase nueva dos ya".toCharArray(), quitarMija.fraseMija());
        }
        try (Credenciales nuevaMija = cambio("999999999999", "Clave Nueva 22", "frase nueva dos ya",
                new CambioCredenciales.CambioLlave.Nueva(huella(5))).completas()) {
            assertArrayEquals(huella(5), nuevaMija.huellaLlaveMija());
        }
    }

    @Test
    void cambiaPinSoloSiHayPinNuevo() {
        assertTrue(cambio("999999999999", null, null, new CambioCredenciales.CambioLlave.SinCambio()).cambiaPin());
        assertFalse(cambio(null, "x", "y", new CambioCredenciales.CambioLlave.SinCambio()).cambiaPin());
    }

    @Test
    void cerrarBorraSoloLoQueTieneYLaHuellaNueva() {
        char[] pinMija = "123".toCharArray();
        char[] claveMija = "abc".toCharArray();
        char[] fraseMija = "xyz".toCharArray();
        byte[] huellaMija = huella(4);
        CambioCredenciales todoMija = new CambioCredenciales(Optional.of(pinMija), Optional.of(claveMija),
                Optional.of(fraseMija), new CambioCredenciales.CambioLlave.Nueva(huellaMija), OptionalInt.of(3));
        todoMija.close();
        assertArrayEquals(new char[3], pinMija);
        assertArrayEquals(new char[3], claveMija);
        assertArrayEquals(new char[3], fraseMija);
        assertArrayEquals(new byte[32], huellaMija);
        // Cada factor se borra por separado.
        char[] soloPinMija = "123".toCharArray();
        new CambioCredenciales(Optional.of(soloPinMija), Optional.empty(), Optional.empty(),
                new CambioCredenciales.CambioLlave.SinCambio(), OptionalInt.empty()).close();
        assertArrayEquals(new char[3], soloPinMija);
        char[] soloClaveMija = "abc".toCharArray();
        new CambioCredenciales(Optional.empty(), Optional.of(soloClaveMija), Optional.empty(),
                new CambioCredenciales.CambioLlave.SinCambio(), OptionalInt.empty()).close();
        assertArrayEquals(new char[3], soloClaveMija);
        char[] soloFraseMija = "xyz".toCharArray();
        new CambioCredenciales(Optional.empty(), Optional.empty(), Optional.of(soloFraseMija),
                new CambioCredenciales.CambioLlave.SinCambio(), OptionalInt.empty()).close();
        assertArrayEquals(new char[3], soloFraseMija);
    }

    @Test
    void cambioNoSeMuestraNiSeCompara() {
        CambioCredenciales unoMija = cambio("1", "a", "b", new CambioCredenciales.CambioLlave.Quitar());
        assertEquals("***", unoMija.toString());
        assertEquals(unoMija, unoMija);
        assertNotEquals(unoMija, cambio("1", "a", "b", new CambioCredenciales.CambioLlave.Quitar()));
        assertEquals("Nueva[***]", new CambioCredenciales.CambioLlave.Nueva(huella(1)).toString());
        assertThrows(IllegalArgumentException.class, () -> new CambioCredenciales.CambioLlave.Nueva(new byte[31]));
        assertThrows(IllegalArgumentException.class, () -> new CambioCredenciales.CambioLlave.Nueva(new byte[33]));
        assertThrows(NullPointerException.class, () -> new CambioCredenciales(null, Optional.empty(), Optional.empty(),
                new CambioCredenciales.CambioLlave.Quitar(), OptionalInt.empty()));
        assertThrows(NullPointerException.class, () -> new CambioCredenciales(Optional.empty(), Optional.empty(),
                Optional.empty(), null, OptionalInt.empty()));
    }

    @Test
    void laExcepcionDeValidacionGuardaSusReglas() {
        ExcepcionValidacion errorMija = new ExcepcionValidacion(List.of(ReglaIncumplida.F1_LONGITUD, ReglaIncumplida.F3_LONGITUD));
        assertEquals(List.of(ReglaIncumplida.F1_LONGITUD, ReglaIncumplida.F3_LONGITUD), errorMija.reglas());
        assertTrue(errorMija.getMessage().contains("F1_LONGITUD"));
    }

    // ------------------------------------------------------------------ CodificadorEntrada

    @Test
    void laEntradaEsLongitudMasUtf8DeCadaFactor() {
        byte[] bytesMija = CodificadorEntrada.codificar("ab".toCharArray(), "ñ".toCharArray(), new char[0]);
        ByteBuffer esperadoMija = ByteBuffer.allocate(4 + 2 + 4 + 2 + 4)
                .putInt(2).put("ab".getBytes(StandardCharsets.UTF_8))
                .putInt(2).put("ñ".getBytes(StandardCharsets.UTF_8))
                .putInt(0);
        assertArrayEquals(esperadoMija.array(), bytesMija);
        assertArrayEquals(new byte[0], CodificadorEntrada.codificar());
        assertThrows(NullPointerException.class, () -> CodificadorEntrada.codificar((char[][]) null));
        assertThrows(NullPointerException.class, () -> CodificadorEntrada.codificar("a".toCharArray(), null));
        // Un sustituto suelto no se puede codificar: se reemplaza por «?» y no falla.
        assertArrayEquals(new byte[] {0, 0, 0, 1, '?'}, CodificadorEntrada.codificar(new char[] {'\ud800'}));
        assertArrayEquals(new byte[] {'?'}, CodificadorEntrada.utf8(new char[] {'\ud800'}));
        assertArrayEquals("áé€".getBytes(StandardCharsets.UTF_8), CodificadorEntrada.utf8("áé€".toCharArray()));
    }
}
