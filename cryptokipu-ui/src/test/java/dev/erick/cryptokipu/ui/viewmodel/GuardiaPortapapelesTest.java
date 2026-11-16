package dev.erick.cryptokipu.ui.viewmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Fase 8: el portapapeles se limpia a los 30 s, al bloquear y al salir, SOLO si sigue teniendo el token copiado. */
class GuardiaPortapapelesTest {

    private static final String TOKENS_MIJA = "CKP-AAAAA-BBBBB-CCCCC-DDDDD-EEEEE-F";

    /** Portapapeles de memoria. */
    private static final class Falso implements GuardiaPortapapeles.Portapapeles {
        private String contenidoMija;
        private int limpiezasMija;

        void copiar(String textoMija) {
            contenidoMija = textoMija;
        }

        @Override
        public Optional<String> texto() {
            return Optional.ofNullable(contenidoMija);
        }

        @Override
        public void limpiar() {
            contenidoMija = null;
            limpiezasMija++;
        }
    }

    /** Programador manual: guarda las tareas y las ejecuta cuando la prueba lo decide. */
    private static final class Manual implements GuardiaPortapapeles.Programador {
        private final List<Runnable> pendientesMija = new ArrayList<>();
        private final List<Duration> retardosMija = new ArrayList<>();

        @Override
        public Runnable programar(Duration retardoMija, Runnable tareaMija) {
            retardosMija.add(retardoMija);
            pendientesMija.add(tareaMija);
            return () -> pendientesMija.remove(tareaMija);
        }

        void dispararTodas() {
            new ArrayList<>(pendientesMija).forEach(Runnable::run);
        }
    }

    private final Falso portapapelesMija = new Falso();
    private final Manual programadorMija = new Manual();
    private final GuardiaPortapapeles guardiaMija = new GuardiaPortapapeles(portapapelesMija, programadorMija);

    @Test
    void sePideLimpiarA30Segundos() {
        portapapelesMija.copiar(TOKENS_MIJA);
        guardiaMija.copiado(TOKENS_MIJA);

        assertEquals(List.of(Duration.ofSeconds(30)), programadorMija.retardosMija);
    }

    @Test
    void aLos30SegundosLimpiaSiSigueElMismoTexto() {
        portapapelesMija.copiar(TOKENS_MIJA);
        guardiaMija.copiado(TOKENS_MIJA);

        programadorMija.dispararTodas();

        assertEquals(Optional.empty(), portapapelesMija.texto());
        assertEquals(1, portapapelesMija.limpiezasMija);
    }

    /** Mutación: «limpiar aunque el usuario haya copiado otra cosa» rompe esta prueba. */
    @Test
    void siElUsuarioCopioOtraCosaNoSeBorra() {
        portapapelesMija.copiar(TOKENS_MIJA);
        guardiaMija.copiado(TOKENS_MIJA);
        portapapelesMija.copiar("la lista del supermercado");

        programadorMija.dispararTodas();

        assertEquals(Optional.of("la lista del supermercado"), portapapelesMija.texto());
        assertEquals(0, portapapelesMija.limpiezasMija, "no debe tocar lo que el usuario copió después");
    }

    @Test
    void alBloquearOSalirLimpiaSiSigueIgualYNoSiCambio() {
        portapapelesMija.copiar(TOKENS_MIJA);
        guardiaMija.copiado(TOKENS_MIJA);
        guardiaMija.alBloquearOSalir();
        assertEquals(Optional.empty(), portapapelesMija.texto());

        portapapelesMija.copiar(TOKENS_MIJA);
        guardiaMija.copiado(TOKENS_MIJA);
        portapapelesMija.copiar("otra cosa");
        guardiaMija.alBloquearOSalir();
        assertEquals(Optional.of("otra cosa"), portapapelesMija.texto());
    }

    @Test
    void despuesDeLimpiarNoQuedaNadaPendienteNiSeRepite() {
        portapapelesMija.copiar(TOKENS_MIJA);
        guardiaMija.copiado(TOKENS_MIJA);
        assertTrue(guardiaMija.pendiente());

        assertTrue(guardiaMija.limpiarSiSigueIgual());

        assertFalse(guardiaMija.pendiente());
        portapapelesMija.copiar(TOKENS_MIJA); // el usuario vuelve a copiar lo mismo por su cuenta
        assertFalse(guardiaMija.limpiarSiSigueIgual(), "ya no es una copia que la aplicación vigile");
        assertEquals(Optional.of(TOKENS_MIJA), portapapelesMija.texto());
    }

    @Test
    void copiarDeNuevoCancelaElTemporizadorAnterior() {
        portapapelesMija.copiar(TOKENS_MIJA);
        guardiaMija.copiado(TOKENS_MIJA);
        guardiaMija.copiado(TOKENS_MIJA);

        assertEquals(1, programadorMija.pendientesMija.size());
    }

    @Test
    void sinCopiaPendienteNoHaceNada() {
        portapapelesMija.copiar("algo del usuario");

        guardiaMija.alBloquearOSalir();
        programadorMija.dispararTodas();

        assertEquals(Optional.of("algo del usuario"), portapapelesMija.texto());
        assertEquals(0, portapapelesMija.limpiezasMija);
    }

    @Test
    void noGuardaElTextoCopiado() throws Exception {
        portapapelesMija.copiar(TOKENS_MIJA);
        guardiaMija.copiado(TOKENS_MIJA);

        for (var campoMija : GuardiaPortapapeles.class.getDeclaredFields()) {
            campoMija.setAccessible(true);
            assertFalse(TOKENS_MIJA.equals(campoMija.get(guardiaMija)), "campo " + campoMija.getName());
        }
    }
}
