package dev.erick.cryptokipu.core.auth;

import java.io.Serial;
import java.util.List;

/** Credenciales nuevas que no cumplen la política v1. Lleva todas las reglas incumplidas. */
public final class ExcepcionValidacion extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient List<ReglaIncumplida> reglasMija;

    public ExcepcionValidacion(List<ReglaIncumplida> reglasMija) {
        super("Credenciales no válidas: " + reglasMija, null, false, false);
        this.reglasMija = List.copyOf(reglasMija);
    }

    public List<ReglaIncumplida> reglas() {
        return reglasMija;
    }
}
