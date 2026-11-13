package dev.erick.cryptokipu.app.casos;

import dev.erick.cryptokipu.app.puertos.ContenedorAbierto;
import dev.erick.cryptokipu.app.puertos.RepositorioBoveda;
import dev.erick.cryptokipu.core.crypto.ParametrosArgon2;
import dev.erick.cryptokipu.core.format.Header;
import dev.erick.cryptokipu.core.format.LimitesFormato;
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

    /**
     * @param umbralAutodestruccionMija 0 = desactivada (dato de la Zona B: solo orientativo antes de desbloquear)
     * @param intentosFallidosMija      contador de la Zona C (no confiable)
     */
    public record Resultado(int versionFormatoMija, ParametrosArgon2 perfilMija, boolean requiereLlaveMija,
            boolean destruidaMija, boolean soloLecturaMija, int umbralAutodestruccionMija, int intentosFallidosMija) {

        /** Intentos que quedan antes de la autodestrucción, o -1 si está desactivada. */
        public int intentosRestantes() {
            return umbralAutodestruccionMija == 0 ? -1 : Math.max(0, umbralAutodestruccionMija - intentosFallidosMija);
        }
    }

    private final RepositorioBoveda repositorioMija;

    public InspeccionarBoveda(RepositorioBoveda repositorioMija) {
        this.repositorioMija = Objects.requireNonNull(repositorioMija, "repositorioMija");
    }

    public Resultado ejecutar(Comando comandoMija) throws IOException {
        try (ContenedorAbierto contenedorMija = repositorioMija.abrir(comandoMija.rutaMija())) {
            boolean llaveMija = contenedorMija.candidatosHeader().stream().anyMatch(Header::requiereLlaveMija);
            boolean destruidaMija = contenedorMija.candidatosHeader().stream().allMatch(Header::estaDestruido);
            int umbralMija = contenedorMija.candidatosHeader().stream()
                    .mapToInt(Header::umbralAutodestruccionMija).max().orElse(0);
            int intentosMija = contenedorMija.candidatosHeader().stream()
                    .mapToInt(Header::intentosFallidosMija).max().orElse(0);
            return new Resultado(LimitesFormato.VERSION_FORMATO_MIJA, contenedorMija.header().zonaAMija().argonMija(),
                    llaveMija, destruidaMija, contenedorMija.esSoloLectura(), umbralMija, intentosMija);
        }
    }
}
