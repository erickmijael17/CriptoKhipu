package dev.erick.cryptokipu.ui.medios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.erick.cryptokipu.ui.viewmodel.HiloFx;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * El reproductor real de JavaFX contra el servidor de medios: adelantar y retroceder (cada salto es una petición
 * Range). Usa un WAV generado (JavaFX reproduce WAV en todas las plataformas; el MP4 H.264 va en la lista manual
 * {@code docs/PRUEBAS_UI.md}). Necesita pantalla: se omite sin ella.
 */
@Tag("toolkit")
class MediaPlayerFxTest {

    private static boolean disponibleMija;

    @TempDir
    Path carpetaMija;

    @BeforeAll
    static void iniciarToolkit() throws InterruptedException {
        String sistemaMija = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (sistemaMija.contains("linux") && System.getenv("DISPLAY") == null
                && System.getenv("WAYLAND_DISPLAY") == null) {
            return;
        }
        CountDownLatch listoMija = new CountDownLatch(1);
        try {
            Platform.startup(listoMija::countDown);
            Platform.setImplicitExit(false);
        } catch (IllegalStateException _) {
            listoMija.countDown();
        } catch (RuntimeException | UnsatisfiedLinkError _) {
            return;
        }
        disponibleMija = listoMija.await(10, TimeUnit.SECONDS);
        if (disponibleMija) {
            HiloFx.toolkitIniciado();
        }
    }

    private static <T> T enFx(Callable<T> accionMija) throws Exception {
        CompletableFuture<T> resultadoMija = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                resultadoMija.complete(accionMija.call());
            } catch (Exception | Error errorMija) {
                resultadoMija.completeExceptionally(errorMija);
            }
        });
        return resultadoMija.get(15, TimeUnit.SECONDS);
    }

    /** WAV PCM de 16 bits, mono, 8 kHz: un tono de {@code segundosMija} segundos. */
    static byte[] wav(int segundosMija) {
        int muestrasMija = 8000 * segundosMija;
        ByteBuffer datosMija = ByteBuffer.allocate(muestrasMija * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int indiceMija = 0; indiceMija < muestrasMija; indiceMija++) {
            datosMija.putShort((short) (Math.sin(indiceMija * 2 * Math.PI * 440 / 8000) * 8000));
        }
        ByteBuffer cabeceraMija = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        cabeceraMija.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + datosMija.capacity())
                .put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short) 1).putShort((short) 1)
                .putInt(8000).putInt(16000).putShort((short) 2).putShort((short) 16)
                .put("data".getBytes(StandardCharsets.US_ASCII)).putInt(datosMija.capacity());
        ByteArrayOutputStream salidaMija = new ByteArrayOutputStream();
        salidaMija.writeBytes(cabeceraMija.array());
        salidaMija.writeBytes(datosMija.array());
        return salidaMija.toByteArray();
    }

    @Test
    void elReproductorAdelantaYRetrocedeConRange() throws Exception {
        assumeTrue(disponibleMija, "Sin pantalla: prueba del toolkit omitida");
        byte[] contenidoMija = wav(6);
        Path archivoMija = Files.write(carpetaMija.resolve("tono.wav"), contenidoMija);
        try (ServidorMedios servidorMija = ServidorMedios.iniciar()) {
            SeekableByteChannel canalMija = Files.newByteChannel(archivoMija);
            ServidorMedios.Publicacion publicadaMija = servidorMija.publicar(canalMija, contenidoMija.length,
                    "audio/wav", "tono.wav");
            CompletableFuture<Void> listoMija = new CompletableFuture<>();
            CompletableFuture<String> falloMija = new CompletableFuture<>();
            MediaPlayer reproductorMija = enFx(() -> {
                MediaPlayer nuevoMija = new MediaPlayer(new Media(publicadaMija.urlMija()));
                nuevoMija.setOnReady(() -> listoMija.complete(null));
                nuevoMija.setOnError(() -> falloMija.complete(String.valueOf(nuevoMija.getError())));
                return nuevoMija;
            });
            try {
                try {
                    listoMija.orTimeout(15, TimeUnit.SECONDS).exceptionally(_ -> null).get();
                } catch (java.util.concurrent.ExecutionException errorMija) {
                    throw new IOException(errorMija);
                }
                assertTrue(listoMija.isDone() && !listoMija.isCompletedExceptionally(),
                        "MediaPlayer no llegó a READY: " + falloMija.getNow("sin error"));
                assertEquals(6.0, enFx(() -> reproductorMija.getTotalDuration().toSeconds()), 0.2);
                // Adelantar, retroceder y volver a adelantar: el reproductor pide otros rangos al servidor.
                for (double destinoMija : new double[] {4.0, 1.0, 5.0, 2.5}) {
                    enFx(() -> {
                        reproductorMija.seek(Duration.seconds(destinoMija));
                        return null;
                    });
                    double alcanzadoMija = esperarTiempo(reproductorMija, destinoMija);
                    assertEquals(destinoMija, alcanzadoMija, 0.5, "seek a " + destinoMija);
                }
            } finally {
                enFx(() -> {
                    reproductorMija.dispose();
                    return null;
                });
            }
        }
    }

    private static double esperarTiempo(MediaPlayer reproductorMija, double destinoMija) throws Exception {
        long limiteMija = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        double actualMija = -1;
        while (System.nanoTime() < limiteMija) {
            actualMija = enFx(() -> reproductorMija.getCurrentTime().toSeconds());
            if (Math.abs(actualMija - destinoMija) < 0.5) {
                return actualMija;
            }
            Thread.sleep(50);
        }
        return actualMija;
    }
}
