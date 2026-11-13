package dev.erick.cryptokipu.app.puertos;

import dev.erick.cryptokipu.core.crypto.SecureBytes;
import dev.erick.cryptokipu.core.format.Header;
import java.io.IOException;
import java.nio.file.Path;

/** Puerto de salida: crea y abre contenedores {@code .kipu}. Lo implementa {@code infra}. */
public interface RepositorioBoveda {

    /** Crea la bóveda y la devuelve ya desbloqueada con {@code claveMaestraMija}. */
    ContenedorAbierto crear(Path rutaMija, Header headerMija, SecureBytes claveMaestraMija) throws IOException;

    /** Abre y bloquea la bóveda, sin desbloquearla. */
    ContenedorAbierto abrir(Path rutaMija) throws IOException;
}
