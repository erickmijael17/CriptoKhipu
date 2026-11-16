package dev.erick.cryptokipu.core.vault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.code_intelligence.jazzer.junit.FuzzTest;

/**
 * Fuzzing de las rutas internas (zip-slip): para CUALQUIER texto, {@code problema} responde sin lanzar y, si la ruta
 * se declara segura, no contiene nada que escape de la carpeta de destino.
 */
class FuzzRutaInternaTest {

    @FuzzTest(maxDuration = "2m")
    void rutaInternaNuncaLanzaYLasSegurasNoEscapan(String rutaMija) {
        boolean seguraMija = RutaInterna.esSegura(rutaMija);
        assertEquals(RutaInterna.problema(rutaMija).isEmpty(), seguraMija);
        RutaInterna.claveColision(rutaMija == null ? "" : rutaMija);
        if (seguraMija) {
            assertFalse(rutaMija.startsWith("/"), rutaMija);
            assertFalse(rutaMija.contains("\\"), rutaMija);
            assertFalse(rutaMija.contains("\0"), rutaMija);
            assertFalse(rutaMija.matches(".*(^|/)\\.\\.(/|$).*"), "segmento .. en una ruta segura: " + rutaMija);
            assertFalse(rutaMija.matches("^[A-Za-z]:.*"), "letra de unidad en una ruta segura: " + rutaMija);
            assertTrue(rutaMija.length() <= RutaInterna.LONGITUD_MAXIMA_MIJA);
        }
    }
}
