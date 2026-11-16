package dev.erick.cryptokipu.ui.viewmodel;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erick.cryptokipu.infra.configuracion.Contenedor;
import dev.erick.cryptokipu.infra.configuracion.PreferenciasStore;
import dev.erick.cryptokipu.infra.storage.ContenedorKipu;
import dev.erick.cryptokipu.infra.storage.InfoDestino;
import dev.erick.cryptokipu.infra.storage.PoliticaDestino;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/** Utilidades de las pruebas de ViewModels: bóvedas reales con el perfil Ligero (Argon2id de 64 MiB). */
final class BancoUi {

    static final String F1_MIJA = "482915736028";
    static final String F2_MIJA = "Kipu2Demo2026";
    static final String F3_MIJA = "el condor pasa el rio";
    static final PoliticaDestino DISCO_AMPLIO_MIJA = _ -> new InfoDestino("NTFS", 1L << 40);

    private BancoUi() {
    }

    static Contenedor contenedor(Path carpetaMija) {
        return new Contenedor(ContenedorKipu.Opciones.porDefecto(),
                PreferenciasStore.portatil(carpetaMija.resolve("preferencias")));
    }

    /**
     * ViewModel de desbloqueo cuyo reloj salta 5 minutos en cada lectura: los tests que fallan varias veces seguidas
     * (para comprobar mensajes) no chocan con el retardo creciente de la Fase 8.
     */
    static DesbloqueoViewModel desbloqueoSinEspera(Contenedor appMija) {
        AtomicLong relojMija = new AtomicLong();
        return new DesbloqueoViewModel(appMija, MemoriaDisponible::bytes,
                () -> relojMija.addAndGet(Duration.ofMinutes(5).toNanos()));
    }

    static char[] c(String valorMija) {
        return valorMija.toCharArray();
    }

    /** Un video, una foto, un Word y un Excel (contenido aleatorio: el formato del archivo no importa al cifrar). */
    static List<Path> cuatroArchivos(Path carpetaMija) throws IOException {
        Files.createDirectories(carpetaMija);
        Random aleatorioMija = new Random(42);
        return List.of(
                archivo(carpetaMija.resolve("viaje.mp4"), 3 * 1024 * 1024 + 17, aleatorioMija),
                archivo(carpetaMija.resolve("foto.jpg"), 400 * 1024, aleatorioMija),
                archivo(carpetaMija.resolve("informe.docx"), 64 * 1024, aleatorioMija),
                archivo(carpetaMija.resolve("presupuesto.xlsx"), 48 * 1024, aleatorioMija));
    }

    private static Path archivo(Path rutaMija, int tamanoMija, Random aleatorioMija) throws IOException {
        byte[] bytesMija = new byte[tamanoMija];
        aleatorioMija.nextBytes(bytesMija);
        Files.write(rutaMija, bytesMija);
        return rutaMija;
    }

    /** Asistente con las credenciales de prueba ya confirmadas, perfil Ligero y destino elegido. */
    static AsistenteCreacionViewModel asistenteListo(Contenedor appMija, Path destinoMija, List<Path> archivosMija) {
        AsistenteCreacionViewModel asistenteMija = new AsistenteCreacionViewModel(appMija, DISCO_AMPLIO_MIJA);
        asistenteMija.archivos().setAll(archivosMija);
        assertTrue(asistenteMija.fijarSecreto(ValidacionViva.Factor.F1, c(F1_MIJA), c(F1_MIJA)).isEmpty());
        assertTrue(asistenteMija.fijarSecreto(ValidacionViva.Factor.F2, c(F2_MIJA), c(F2_MIJA)).isEmpty());
        assertTrue(asistenteMija.fijarSecreto(ValidacionViva.Factor.F3, c(F3_MIJA), c(F3_MIJA)).isEmpty());
        asistenteMija.perfilProperty().set(PerfilUi.LIGERO);
        asistenteMija.destinoProperty().set(destinoMija);
        return asistenteMija;
    }

    /** Corre la acción en un hilo con el nombre del hilo de JavaFX (simula llamarla desde la vista sin Task). */
    static Throwable enHiloFx(ThrowingRunnable accionMija) throws InterruptedException {
        Throwable[] capturadoMija = new Throwable[1];
        Thread hiloMija = Thread.ofPlatform().name(HiloFx.NOMBRE_HILO_FX_MIJA).start(() -> {
            try {
                accionMija.run();
            } catch (Throwable errorMija) {
                capturadoMija[0] = errorMija;
            }
        });
        hiloMija.join();
        return capturadoMija[0];
    }

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Exception;
    }
}
