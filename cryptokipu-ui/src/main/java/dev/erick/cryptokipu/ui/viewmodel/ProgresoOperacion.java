package dev.erick.cryptokipu.ui.viewmodel;

import dev.erick.cryptokipu.core.progreso.ReporteProgreso;

/**
 * Progreso de una operación larga con etapas. Argon2id (Bouncy Castle) no es interrumpible: durante esa etapa la
 * interfaz muestra "No se puede cancelar en esta etapa" y, si el usuario cancela, descarta el resultado al final.
 */
public interface ProgresoOperacion extends ReporteProgreso {

    ProgresoOperacion NINGUNO_MIJA = new ProgresoOperacion() {
        @Override
        public void etapa(String claveTextoMija, boolean cancelableMija) {
            // Sin interfaz: nada que mostrar.
        }

        @Override
        public void avanzar(long hechoMija, long totalMija) {
            // Sin interfaz: nada que mostrar.
        }
    };

    /** @param claveTextoMija clave de {@code textos.properties} que describe la etapa */
    void etapa(String claveTextoMija, boolean cancelableMija);
}
