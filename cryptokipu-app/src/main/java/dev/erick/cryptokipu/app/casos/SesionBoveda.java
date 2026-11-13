package dev.erick.cryptokipu.app.casos;

import dev.erick.cryptokipu.app.puertos.ContenedorAbierto;
import dev.erick.cryptokipu.core.error.ErrorBoveda;
import dev.erick.cryptokipu.core.error.ExcepcionBoveda;
import dev.erick.cryptokipu.core.format.Manifest;
import java.util.Objects;

/**
 * Bóveda desbloqueada: la clave maestra, el manifest y el canal único viven en el contenedor.
 *
 * <ul>
 *   <li>{@link #close()} borra la clave maestra, suelta el bloqueo y cierra el canal. Después, cualquier
 *       uso lanza {@link IllegalStateException}.</li>
 *   <li>Si se abrió con un token, queda en <b>rotación obligatoria</b>: solo se permiten
 *       {@link RotarCredenciales} y {@link #close()}; lo demás lanza {@code RotacionPendiente}.</li>
 * </ul>
 */
public final class SesionBoveda implements AutoCloseable {

    private final ContenedorAbierto contenedorMija;
    private boolean rotacionObligatoriaMija;
    private boolean cerradaMija;

    SesionBoveda(ContenedorAbierto contenedorMija, boolean rotacionObligatoriaMija) {
        this.contenedorMija = Objects.requireNonNull(contenedorMija, "contenedorMija");
        this.rotacionObligatoriaMija = rotacionObligatoriaMija;
    }

    public boolean rotacionObligatoria() {
        return rotacionObligatoriaMija;
    }

    public boolean cerrada() {
        return cerradaMija;
    }

    /** Contenedor para operaciones normales. */
    public ContenedorAbierto contenedor() {
        exigirAbierta();
        if (rotacionObligatoriaMija) {
            throw new ExcepcionBoveda(new ErrorBoveda.RotacionPendiente());
        }
        return contenedorMija;
    }

    public Manifest manifest() {
        return contenedor().manifest();
    }

    /** Contenedor para rotar credenciales: permitido también en rotación obligatoria. */
    ContenedorAbierto contenedorParaRotar() {
        exigirAbierta();
        return contenedorMija;
    }

    void rotacionCompletada() {
        rotacionObligatoriaMija = false;
    }

    @Override
    public void close() {
        if (!cerradaMija) {
            cerradaMija = true;
            contenedorMija.close();
        }
    }

    @Override
    public String toString() {
        return "SesionBoveda[rotacionObligatoria=" + rotacionObligatoriaMija + ", cerrada=" + cerradaMija + "]";
    }

    private void exigirAbierta() {
        if (cerradaMija) {
            throw new IllegalStateException("La sesión está cerrada");
        }
    }
}
