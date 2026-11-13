package dev.erick.cryptokipu.app.puertos;

import dev.erick.cryptokipu.core.crypto.SecureBytes;
import dev.erick.cryptokipu.core.format.Header;
import java.util.Optional;

/**
 * Abre los slots de un header candidato y devuelve la clave maestra, o vacío si no abre.
 * La clave devuelta pasa a ser responsabilidad del contenedor, que la cierra.
 */
@FunctionalInterface
public interface AbridorSlots {

    Optional<SecureBytes> abrir(Header candidatoMija);
}
