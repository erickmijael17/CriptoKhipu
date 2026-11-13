package dev.erick.cryptokipu.app.casos;

import dev.erick.cryptokipu.app.puertos.ContenedorAbierto;
import dev.erick.cryptokipu.app.puertos.RepositorioBoveda;
import dev.erick.cryptokipu.core.auth.Credenciales;
import dev.erick.cryptokipu.core.auth.TokenRecuperacion;
import dev.erick.cryptokipu.core.crypto.FuenteAleatoria;
import dev.erick.cryptokipu.core.crypto.ParametrosArgon2;
import dev.erick.cryptokipu.core.vault.GestorSlots;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Crea una bóveda nueva y devuelve la sesión abierta y los 6 tokens (se muestran una sola vez). */
public final class CrearBoveda {

    /** @param umbralAutodestruccionMija 0 = autodestrucción desactivada */
    public record Comando(Path rutaMija, Credenciales credencialesMija, ParametrosArgon2 perfilMija,
            int umbralAutodestruccionMija) {
        public Comando {
            Objects.requireNonNull(rutaMija, "rutaMija");
            Objects.requireNonNull(credencialesMija, "credencialesMija");
            Objects.requireNonNull(perfilMija, "perfilMija");
        }
    }

    public record Resultado(SesionBoveda sesionMija, List<TokenRecuperacion> tokensMija) implements AutoCloseable {
        @Override
        public void close() {
            tokensMija.forEach(TokenRecuperacion::close);
            sesionMija.close();
        }
    }

    private final RepositorioBoveda repositorioMija;
    private final GestorSlots gestorMija;

    public CrearBoveda(RepositorioBoveda repositorioMija, FuenteAleatoria fuenteMija) {
        this.repositorioMija = Objects.requireNonNull(repositorioMija, "repositorioMija");
        this.gestorMija = new GestorSlots(fuenteMija);
    }

    public Resultado ejecutar(Comando comandoMija) throws IOException {
        Autenticacion.validar(comandoMija.credencialesMija());
        try (GestorSlots.Creacion creacionMija = gestorMija.crear(comandoMija.credencialesMija(),
                comandoMija.perfilMija(), comandoMija.umbralAutodestruccionMija())) {
            ContenedorAbierto contenedorMija = repositorioMija.crear(comandoMija.rutaMija(), creacionMija.headerMija(),
                    creacionMija.claveMaestraMija());
            return new Resultado(new SesionBoveda(contenedorMija, false), Autenticacion.copiar(creacionMija.tokensMija()));
        }
    }
}
