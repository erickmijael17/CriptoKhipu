package dev.erick.cryptokipu.core.auth;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import dev.erick.cryptokipu.core.error.ErrorBoveda;
import dev.erick.cryptokipu.core.error.ExcepcionBoveda;
import java.util.Arrays;

/** Fuzzing del lector de tokens: solo puede aceptar o fallar con {@code TokenMalEscrito}; nunca otra excepción. */
class FuzzTokenTest {

    @FuzzTest(maxDuration = "2m")
    void leerTokenSoloAceptaOFallaConTokenMalEscrito(FuzzedDataProvider datosMija) {
        char[] escritoMija = datosMija.consumeRemainingAsString().toCharArray();
        char[] copiaMija = escritoMija.clone();
        try (TokenRecuperacion tokenMija = TokenRecuperacion.leer(escritoMija)) {
            // Un token aceptado se formatea y vuelve a leerse igual.
            char[] formateadoMija = tokenMija.formatear();
            try (TokenRecuperacion otroMija = TokenRecuperacion.leer(formateadoMija)) {
                assertArrayEquals(tokenMija.simbolosMija(), otroMija.simbolosMija());
            } finally {
                Arrays.fill(formateadoMija, '\0');
            }
        } catch (ExcepcionBoveda errorMija) {
            assertEquals(new ErrorBoveda.TokenMalEscrito(), errorMija.error());
        }
        assertArrayEquals(copiaMija, escritoMija, "leer no debe modificar la entrada");
    }
}
