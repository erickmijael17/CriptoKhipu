package dev.erick.cryptokipu.app.casos;

import dev.erick.cryptokipu.app.puertos.ContenedorAbierto;
import dev.erick.cryptokipu.app.puertos.RepositorioBoveda;
import dev.erick.cryptokipu.core.auth.Credenciales;
import dev.erick.cryptokipu.core.crypto.FuenteAleatoria;
import dev.erick.cryptokipu.core.error.ExcepcionBoveda;
import dev.erick.cryptokipu.core.vault.GestorSlots;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Abre con F1–F4. Cualquier factor equivocado → {@code CredencialesIncorrectas}; el intento se suma en la
 * Zona C y, al llegar al umbral, la bóveda se autodestruye ({@code BovedaDestruida}).
 */
public final class DesbloquearBoveda {

    public record Comando(Path rutaMija, Credenciales credencialesMija) {
        public Comando {
            Objects.requireNonNull(rutaMija, "rutaMija");
            Objects.requireNonNull(credencialesMija, "credencialesMija");
        }
    }

    private final RepositorioBoveda repositorioMija;
    private final GestorSlots gestorMija;

    public DesbloquearBoveda(RepositorioBoveda repositorioMija, FuenteAleatoria fuenteMija) {
        this.repositorioMija = Objects.requireNonNull(repositorioMija, "repositorioMija");
        this.gestorMija = new GestorSlots(fuenteMija);
    }

    public SesionBoveda ejecutar(Comando comandoMija) throws IOException {
        ContenedorAbierto contenedorMija = repositorioMija.abrir(comandoMija.rutaMija());
        try {
            Autenticacion.exigirNoDestruida(contenedorMija);
            Autenticacion.desbloquearConCredenciales(contenedorMija, gestorMija, comandoMija.credencialesMija());
            if (!contenedorMija.esSoloLectura() && contenedorMija.header().intentosFallidosMija() > 0) {
                contenedorMija.reiniciarIntentos();
            }
            return new SesionBoveda(contenedorMija, false);
        } catch (ExcepcionBoveda errorMija) {
            ExcepcionBoveda finalMija = Autenticacion.registrarFallo(contenedorMija, errorMija);
            contenedorMija.close();
            throw finalMija;
        } catch (IOException | RuntimeException errorMija) {
            contenedorMija.close();
            throw errorMija;
        }
    }
}
