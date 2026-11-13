package dev.erick.cryptokipu.infra.storage;

import dev.erick.cryptokipu.app.puertos.ContenedorAbierto;
import dev.erick.cryptokipu.app.puertos.RepositorioBoveda;
import dev.erick.cryptokipu.core.crypto.SecureBytes;
import dev.erick.cryptokipu.core.format.Header;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/** Implementación del puerto {@link RepositorioBoveda} sobre archivos {@code .kipu}. */
public final class RepositorioKipu implements RepositorioBoveda {

    private final ContenedorKipu.Opciones opcionesMija;

    public RepositorioKipu(ContenedorKipu.Opciones opcionesMija) {
        this.opcionesMija = Objects.requireNonNull(opcionesMija, "opcionesMija");
    }

    public static RepositorioKipu porDefecto() {
        return new RepositorioKipu(ContenedorKipu.Opciones.porDefecto());
    }

    @Override
    public ContenedorAbierto crear(Path rutaMija, Header headerMija, SecureBytes claveMaestraMija) throws IOException {
        return ContenedorKipu.crear(rutaMija, headerMija, claveMaestraMija, opcionesMija);
    }

    @Override
    public ContenedorAbierto abrir(Path rutaMija) throws IOException {
        return ContenedorKipu.abrir(rutaMija, opcionesMija);
    }
}
