package dev.erick.cryptokipu.infra.fs;

import dev.erick.cryptokipu.app.puertos.ColocadorArchivos;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Coloca el {@code .part} verificado en su destino sin pisar archivos existentes (plan, Fase 5 punto 0).
 *
 * <ul>
 *   <li>Con reemplazo: {@code ATOMIC_MOVE} (en Windows reemplaza; en POSIX, {@code rename()} también).</li>
 *   <li>Sin reemplazo: {@code Files.createLink(destino, parcial)} y borrar el {@code .part}. Crear un enlace duro
 *       es atómico y falla si el destino existe, en todos los sistemas.</li>
 *   <li>Si el sistema de archivos no admite enlaces duros (FAT32, exFAT: lo típico de un USB), se usa
 *       {@code Files.move} sin reemplazo. En Linux y macOS ese camino comprueba y luego renombra: queda una
 *       ventana mínima en la que un archivo creado justo entonces se pisaría (documentado en THREAT_MODEL).</li>
 * </ul>
 */
public final class ColocadorSeguro implements ColocadorArchivos {

    /** Crear un enlace duro; sustituible en pruebas para simular FAT32/exFAT. */
    @FunctionalInterface
    interface Enlazador {
        void enlazar(Path destinoMija, Path existenteMija) throws IOException;
    }

    private final Enlazador enlazadorMija;

    public ColocadorSeguro() {
        this(Files::createLink);
    }

    ColocadorSeguro(Enlazador enlazadorMija) {
        this.enlazadorMija = enlazadorMija;
    }

    @Override
    public boolean colocar(Path parcialMija, Path destinoMija, boolean reemplazarMija) throws IOException {
        if (reemplazarMija) {
            Files.move(parcialMija, destinoMija, StandardCopyOption.ATOMIC_MOVE);
            return true;
        }
        try {
            enlazadorMija.enlazar(destinoMija, parcialMija);
            Files.delete(parcialMija);
            return true;
        } catch (FileAlreadyExistsException _) {
            return false;
        } catch (UnsupportedOperationException | FileSystemException _) {
            return moverSinReemplazo(parcialMija, destinoMija);
        }
    }

    private static boolean moverSinReemplazo(Path parcialMija, Path destinoMija) throws IOException {
        if (Files.exists(destinoMija)) {
            return false;
        }
        try {
            Files.move(parcialMija, destinoMija);
            return true;
        } catch (FileAlreadyExistsException _) {
            return false;
        }
    }
}
