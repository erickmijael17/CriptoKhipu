package dev.erick.cryptokipu.app.casos;

import dev.erick.cryptokipu.app.puertos.ContenedorAbierto;
import dev.erick.cryptokipu.app.puertos.RepositorioBoveda;
import dev.erick.cryptokipu.core.format.Header;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Lo que la interfaz necesita saber antes de pedir credenciales. Abre, lee los headers y cierra; no escribe nada.
 * Pide la llave si <b>cualquier</b> copia del header la requiere: así, invertir la marca en una copia no
 * impide abrir la bóveda (la AAD del slot 0 hace que esa copia falle y se use la otra).
 */
public final class InspeccionarBoveda {

    public record Comando(Path rutaMija) {
        public Comando {
            Objects.requireNonNull(rutaMija, "rutaMija");
        }
    }

    public record Resultado(boolean requiereLlaveMija, boolean destruidaMija, boolean soloLecturaMija,
            long memoriaArgonBytesMija) {
    }

    private final RepositorioBoveda repositorioMija;

    public InspeccionarBoveda(RepositorioBoveda repositorioMija) {
        this.repositorioMija = Objects.requireNonNull(repositorioMija, "repositorioMija");
    }

    public Resultado ejecutar(Comando comandoMija) throws IOException {
        try (ContenedorAbierto contenedorMija = repositorioMija.abrir(comandoMija.rutaMija())) {
            boolean llaveMija = contenedorMija.candidatosHeader().stream().anyMatch(Header::requiereLlaveMija);
            boolean destruidaMija = contenedorMija.candidatosHeader().stream().allMatch(Header::estaDestruido);
            return new Resultado(llaveMija, destruidaMija, contenedorMija.esSoloLectura(),
                    contenedorMija.header().zonaAMija().argonMija().memoriaBytes());
        }
    }
}
