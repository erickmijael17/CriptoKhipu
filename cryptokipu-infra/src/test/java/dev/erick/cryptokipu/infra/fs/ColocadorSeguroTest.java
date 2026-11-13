package dev.erick.cryptokipu.infra.fs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ColocadorSeguroTest {

    @TempDir
    Path carpetaMija;

    private Path parcialMija;
    private Path destinoMija;

    @BeforeEach
    void preparar() throws IOException {
        parcialMija = Files.writeString(carpetaMija.resolve("datos.txt.part"), "verificado");
        destinoMija = carpetaMija.resolve("datos.txt");
    }

    @Test
    void conEnlaceDuroColocaYBorraElPart() throws IOException {
        assertTrue(new ColocadorSeguro().colocar(parcialMija, destinoMija, false));
        assertEquals("verificado", Files.readString(destinoMija));
        assertFalse(Files.exists(parcialMija));
    }

    @Test
    void destinoCreadoJustoAntesDelEnlaceNoSePisa() throws IOException {
        ColocadorSeguro colocadorMija = new ColocadorSeguro((destinoEnlaceMija, existenteMija) -> {
            Files.writeString(destinoEnlaceMija, "intruso"); // otro proceso gana la carrera
            Files.createLink(destinoEnlaceMija, existenteMija);
        });
        assertFalse(colocadorMija.colocar(parcialMija, destinoMija, false));
        assertEquals("intruso", Files.readString(destinoMija));
        assertTrue(Files.exists(parcialMija), "lo borra quien llama");
    }

    @Test
    void sinEnlacesDurosComoEnFat32UsaMoveSinReemplazo() throws IOException {
        ColocadorSeguro fatMija = new ColocadorSeguro((_, _) -> {
            throw new FileSystemException("FAT32: función incorrecta");
        });
        assertTrue(fatMija.colocar(parcialMija, destinoMija, false));
        assertEquals("verificado", Files.readString(destinoMija));

        Path otroParcialMija = Files.writeString(carpetaMija.resolve("otro.part"), "nuevo");
        assertFalse(fatMija.colocar(otroParcialMija, destinoMija, false));
        assertEquals("verificado", Files.readString(destinoMija));
    }

    @Test
    void conReemplazoSustituyeElDestino() throws IOException {
        Files.writeString(destinoMija, "viejo");
        assertTrue(new ColocadorSeguro().colocar(parcialMija, destinoMija, true));
        assertEquals("verificado", Files.readString(destinoMija));
    }
}
