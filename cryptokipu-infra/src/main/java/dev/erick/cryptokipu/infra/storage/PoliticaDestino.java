package dev.erick.cryptokipu.infra.storage;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;

/** Describe el destino de escritura. Se sustituye en pruebas para simular FAT32 o disco lleno. */
@FunctionalInterface
public interface PoliticaDestino {

    InfoDestino describir(Path directorioMija) throws IOException;

    static PoliticaDestino delSistema() {
        return directorioMija -> {
            FileStore almacenMija = Files.getFileStore(directorioMija);
            return new InfoDestino(almacenMija.type(), almacenMija.getUsableSpace());
        };
    }
}
