package dev.erick.cryptokipu.infra.storage;

import dev.erick.cryptokipu.app.puertos.Origen;
import dev.erick.cryptokipu.core.crypto.FuenteAleatoria;
import dev.erick.cryptokipu.core.crypto.KeyWrap;
import dev.erick.cryptokipu.core.crypto.ParametrosArgon2;
import dev.erick.cryptokipu.core.crypto.ProveedorNonce;
import dev.erick.cryptokipu.core.crypto.SecureBytes;
import dev.erick.cryptokipu.core.format.Header;
import dev.erick.cryptokipu.core.format.MetadatosPrincipal;
import dev.erick.cryptokipu.core.format.Slot;
import dev.erick.cryptokipu.core.format.TipoSlot;
import dev.erick.cryptokipu.core.format.ZonaA;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Genera el corpus semilla y la plantilla de headers del fuzzing de {@link ContenedorKipu} (Fase 9):
 * {@code ./gradlew :cryptokipu-infra:generarSemillasFuzz}. Se versionan los resultados; solo se repite si cambia el formato.
 */
public final class GeneradorSemillasFuzz {

    private static final FuenteAleatoria FUENTE_MIJA = FuenteAleatoria.delSistema();
    private static final int OFFSET_DATOS_MIJA = 2 * 16 * 1024;

    private GeneradorSemillasFuzz() {
    }

    public static void main(String[] argumentosMija) throws IOException {
        Path raizMija = Path.of(argumentosMija[0]).resolve("dev/erick/cryptokipu/infra/storage");
        Path carpetaSemillasMija = raizMija.resolve("FuzzContenedorTestInputs/abrirNuncaFallaConUnErrorNoControlado");
        Files.createDirectories(carpetaSemillasMija);
        Path temporalMija = Files.createTempDirectory("semillas-fuzz");
        Path bovedaMija = temporalMija.resolve("b.kipu");
        ZonaA zonaMija = ZonaA.nueva(new ParametrosArgon2(64 * 1024, 2, 1), FUENTE_MIJA);
        SecureBytes kekMija = SecureBytes.copiaDe(FUENTE_MIJA.bytes(32));
        SecureBytes maestraMija = SecureBytes.copiaDe(FUENTE_MIJA.bytes(32));
        List<Slot> slotsMija = new ArrayList<>();
        slotsMija.add(Slot.envolver(zonaMija, 0, TipoSlot.PRINCIPAL, new MetadatosPrincipal(false, 0),
                FUENTE_MIJA.bytes(32), kekMija, maestraMija, new KeyWrap(new ProveedorNonce(FUENTE_MIJA))));
        for (int indiceMija = 1; indiceMija < 7; indiceMija++) {
            slotsMija.add(Slot.vacio(FUENTE_MIJA));
        }
        ContenedorKipu.Opciones opcionesMija = new ContenedorKipu.Opciones(FUENTE_MIJA, _ -> new InfoDestino("NTFS",
                Long.MAX_VALUE), Interruptor.NINGUNO_MIJA);
        try (ContenedorKipu contenedorMija = ContenedorKipu.crear(bovedaMija, Header.nuevo(zonaMija, slotsMija, 0),
                maestraMija, opcionesMija)) {
            Path unoMija = temporalMija.resolve("uno.txt");
            Files.writeString(unoMija, "contenido de prueba para el corpus");
            Path dosMija = temporalMija.resolve("dos.bin");
            Files.write(dosMija, FUENTE_MIJA.bytes(5000));
            contenedorMija.agregar(List.of(new Origen(unoMija, "uno.txt")));
            contenedorMija.agregar(List.of(new Origen(dosMija, "carpeta/dos.bin")));
        }
        byte[] completoMija = Files.readAllBytes(bovedaMija);
        Files.write(raizMija.resolve("plantilla-headers.bin"), Arrays.copyOf(completoMija, OFFSET_DATOS_MIJA));
        byte[] colaMija = Arrays.copyOfRange(completoMija, OFFSET_DATOS_MIJA, completoMija.length);
        // El long de la pista va al FINAL del dato (Jazzer lo consume desde atrás): dos ordenaciones posibles.
        escribir(carpetaSemillasMija.resolve("completa-sin-pista"), colaMija, 0);
        escribir(carpetaSemillasMija.resolve("completa-pista-a"), colaMija, 1);
        byte[] conBasuraMija = Arrays.copyOf(colaMija, colaMija.length + 100);
        escribir(carpetaSemillasMija.resolve("basura-al-final"), conBasuraMija, 0);
        escribir(carpetaSemillasMija.resolve("truncada"), Arrays.copyOf(colaMija, colaMija.length - 7), 0);
        escribir(carpetaSemillasMija.resolve("sin-datos"), new byte[0], 0);
        escribir(carpetaSemillasMija.resolve("footer-solo"), Arrays.copyOfRange(colaMija, colaMija.length - 29, colaMija.length), 0);
        maestraMija.close();
        kekMija.close();
    }

    private static void escribir(Path destinoMija, byte[] colaMija, long pistaMija) throws IOException {
        ByteBuffer bufferMija = ByteBuffer.allocate(colaMija.length + 8);
        bufferMija.put(colaMija).putLong(pistaMija);
        Files.write(destinoMija, bufferMija.array());
    }
}
