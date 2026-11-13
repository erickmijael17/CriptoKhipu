package dev.erick.cryptokipu.app.casos;

import java.io.IOException;
import java.util.Objects;

/** Reescribe la bóveda solo con los archivos vivos (los bloques se copian cifrados, sin recifrar). */
public final class Compactar {

    public Compactar() {
        // Sin dependencias: opera sobre la sesión del comando.
    }

    public record Comando(SesionBoveda sesionMija) {
        public Comando {
            Objects.requireNonNull(sesionMija, "sesionMija");
        }
    }

    public void ejecutar(Comando comandoMija) throws IOException {
        comandoMija.sesionMija().contenedor().compactar();
    }
}
