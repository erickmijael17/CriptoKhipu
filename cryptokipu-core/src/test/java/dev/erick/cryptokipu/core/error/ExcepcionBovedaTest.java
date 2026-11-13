package dev.erick.cryptokipu.core.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class ExcepcionBovedaTest {

    @Test
    void elMensajeEsSoloElTipoDeErrorSinDatos() {
        var errorMija = new ErrorBoveda.EspacioInsuficiente(987_654_321L, 123_456_789L);
        var excepcionMija = new ExcepcionBoveda(errorMija);

        assertEquals("EspacioInsuficiente", excepcionMija.getMessage());
        assertFalse(excepcionMija.toString().contains("987654321"));
        assertSame(errorMija, excepcionMija.error());
    }

    @Test
    void noGuardaTrazaParaNoFiltrarContexto() {
        var excepcionMija = new ExcepcionBoveda(new ErrorBoveda.CredencialesIncorrectas());

        assertEquals(0, excepcionMija.getStackTrace().length);
    }

    @Test
    void credencialesIncorrectasNoTieneComponentesQueRevelenElFactor() {
        assertEquals(0, ErrorBoveda.CredencialesIncorrectas.class.getRecordComponents().length);
    }

    @Test
    void rechazaErrorNulo() {
        assertThrows(NullPointerException.class, () -> new ExcepcionBoveda(null));
    }

    @Test
    void elSwitchSobreErroresEsExhaustivo() {
        List<ErrorBoveda> erroresMija = List.of(
                new ErrorBoveda.CredencialesIncorrectas(),
                new ErrorBoveda.IntegridadFallida(ErrorBoveda.IntegridadFallida.Zona.MANIFEST),
                new ErrorBoveda.FormatoNoSoportado(2),
                new ErrorBoveda.ParametrosFueraDeRango("argon2.m"),
                new ErrorBoveda.EspacioInsuficiente(10, 5),
                new ErrorBoveda.LimiteSistemaArchivos("FAT32", 4_294_967_295L),
                new ErrorBoveda.BovedaEnUso(),
                new ErrorBoveda.BovedaDestruida(),
                new ErrorBoveda.MemoriaInsuficiente(1L << 30, 1L << 29),
                new ErrorBoveda.OperacionCancelada(),
                new ErrorBoveda.TokenMalEscrito(),
                new ErrorBoveda.RotacionPendiente());

        for (ErrorBoveda errorMija : erroresMija) {
            // Sin rama default: si se agrega un error y no se maneja aquí, no compila.
            String claveMija = switch (errorMija) {
                case ErrorBoveda.CredencialesIncorrectas _ -> "credenciales";
                case ErrorBoveda.IntegridadFallida _ -> "integridad";
                case ErrorBoveda.FormatoNoSoportado _ -> "formato";
                case ErrorBoveda.ParametrosFueraDeRango _ -> "parametros";
                case ErrorBoveda.EspacioInsuficiente _ -> "espacio";
                case ErrorBoveda.LimiteSistemaArchivos _ -> "limite";
                case ErrorBoveda.BovedaEnUso _ -> "en-uso";
                case ErrorBoveda.BovedaDestruida _ -> "destruida";
                case ErrorBoveda.MemoriaInsuficiente _ -> "memoria";
                case ErrorBoveda.OperacionCancelada _ -> "cancelada";
                case ErrorBoveda.TokenMalEscrito _ -> "token";
                case ErrorBoveda.RotacionPendiente _ -> "rotacion";
            };
            assertFalse(claveMija.isEmpty());
        }
    }

    @Test
    void parametrosObligatoriosNoAceptanNulo() {
        assertThrows(IllegalArgumentException.class, () -> new ErrorBoveda.IntegridadFallida(null));
        assertThrows(IllegalArgumentException.class, () -> new ErrorBoveda.ParametrosFueraDeRango(null));
        assertThrows(IllegalArgumentException.class, () -> new ErrorBoveda.LimiteSistemaArchivos(null, 1));
    }
}
