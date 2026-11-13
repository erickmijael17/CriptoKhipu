package dev.erick.cryptokipu.core.vault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class RutaInternaTest {

    @ParameterizedTest(name = "rechaza \"{0}\"")
    @ValueSource(strings = {"", "/etc/passwd", "//servidor/compartido/a.txt", "../a.txt", "a/../../b.txt", "a/./b",
            "a//b", "C:/Windows/a.txt", "c:archivo.txt", "a\\..\\b.txt", "CON", "con.txt", "fotos/PRN.jpg",
            "aux", "NUL.tar.gz", "COM1", "lpt9.log", "a<b", "a>b", "a\"b", "a|b", "a?b", "a*b", "nombre.",
            "nombre ", "tab\tdentro", "nulo\u0000", "COM0", "lpt0.txt", "COM¹", "lpt³.log", "CONIN$",
            "conout$.txt"})
    void rutasPeligrosasONoPortables(String rutaMija) {
        assertFalse(RutaInterna.esSegura(rutaMija), rutaMija);
    }

    @ParameterizedTest(name = "acepta \"{0}\"")
    @ValueSource(strings = {"a.txt", "fotos/año 2026/ñandú.jpg", "docs/informe final.docx", ".oculto", "consola.txt",
            "COM10", "LPT", "a..b", "carpeta/sub/archivo.tar.gz"})
    void rutasValidas(String rutaMija) {
        assertTrue(RutaInterna.esSegura(rutaMija), rutaMija);
    }

    /** Cada defensa se prueba sola: ".." también termina en punto, así que se exige el motivo exacto. */
    @Test
    void elMotivoDeRechazoEsElEsperado() {
        assertEquals(java.util.Optional.of("segmento '.' o '..'"), RutaInterna.problema("a/../b"));
        assertEquals(java.util.Optional.of("segmento '.' o '..'"), RutaInterna.problema("./b"));
        assertEquals(java.util.Optional.of("ruta absoluta o UNC"), RutaInterna.problema("//srv/x"));
        assertEquals(java.util.Optional.of("carácter inválido o letra de unidad"), RutaInterna.problema("C:/x"));
        assertEquals(java.util.Optional.of("nombre reservado de Windows"), RutaInterna.problema("nul.txt"));
        assertEquals(java.util.Optional.of("termina en punto o espacio"), RutaInterna.problema("a."));
    }

    @Test
    void unirNormalizaANfc() {
        String descompuestaMija = "n\u0303andu\u0301.jpg";
        assertEquals("fotos/ñandú.jpg", RutaInterna.unir(List.of("fotos", descompuestaMija)));
    }

    @Test
    void laClaveDeColisionIgnoraMayusculasYFormaUnicode() {
        assertEquals(RutaInterna.claveColision("Fotos/A.TXT"), RutaInterna.claveColision("fotos/a.txt"));
        assertEquals(RutaInterna.claveColision("ñ.txt"), RutaInterna.claveColision("n\u0303.txt"));
    }
}
