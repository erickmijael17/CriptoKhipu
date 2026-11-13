package dev.erick.cryptokipu.core.registro;

import java.util.Objects;

/**
 * Punto único para obtener un {@link System.Logger}.
 *
 * <p>Política (R6 del plan, detalle en {@code docs/POLITICA_REGISTRO.md}):
 * nivel INFO por defecto; prohibido registrar credenciales, tokens, claves, cualquier
 * {@code byte[]}/{@code char[]}, nombres de archivos de la bóveda o rutas internas.
 * Sin telemetría: los registros nunca salen del equipo.
 */
public final class Registro {

    private Registro() {
    }

    public static System.Logger para(Class<?> claseMija) {
        return System.getLogger(Objects.requireNonNull(claseMija, "claseMija").getName());
    }
}
