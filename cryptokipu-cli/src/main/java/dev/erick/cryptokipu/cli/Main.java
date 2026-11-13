package dev.erick.cryptokipu.cli;

import dev.erick.cryptokipu.cli.comandos.Cli;
import dev.erick.cryptokipu.infra.configuracion.Contenedor;
import java.io.Console;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Punto de entrada de {@code kipu}.
 *
 * <p>Ctrl+C (o SIGTERM): el shutdown hook activa la cancelación; la operación en curso lanza
 * {@code OperacionCancelada}, no escribe el footer y cierra la sesión (clave maestra en cero). El hook espera a
 * que el hilo principal termine esa limpieza y sale con código 130.
 */
public final class Main {

    private static final long ESPERA_LIMPIEZA_SEGUNDOS_MIJA = 15;

    private Main() {
    }

    public static void main(String[] argumentosMija) {
        Cancelacion cancelacionMija = new Cancelacion(System.err);
        Console consolaMija = System.console();
        Optional<LectorSecretos> lectorConsolaMija = consolaMija != null && consolaMija.isTerminal()
                ? Optional.of(new LectorConsola(consolaMija)) : Optional.empty();
        Terminal terminalMija = Terminal.delSistema();
        // En una terminal se respeta su codificación; hacia tuberías y archivos (--json, scripts) siempre UTF-8,
        // en vez del código de página nativo (en Windows, Cp1252), que rompería ñ y tildes.
        PrintStream salidaMija = terminalMija.salidaEsTty() ? System.out
                : new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        Entorno entornoMija = new Entorno(Contenedor.porDefecto(), salidaMija, System.err, System.in,
                lectorConsolaMija, terminalMija, cancelacionMija);

        CountDownLatch limpiezaMija = new CountDownLatch(1);
        AtomicBoolean salidaNormalMija = new AtomicBoolean();
        Thread ganchoMija = new Thread(() -> {
            if (salidaNormalMija.get()) {
                return;
            }
            cancelacionMija.cancelar();
            try {
                limpiezaMija.await(ESPERA_LIMPIEZA_SEGUNDOS_MIJA, TimeUnit.SECONDS);
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
            }
            System.err.println("Cancelado.");
            Runtime.getRuntime().halt(CodigosSalida.CANCELADO_MIJA);
        }, "kipu-cancelacion");
        Runtime.getRuntime().addShutdownHook(ganchoMija);

        int codigoMija;
        try {
            codigoMija = Cli.ejecutar(argumentosMija, entornoMija);
        } finally {
            limpiezaMija.countDown();
        }
        salidaNormalMija.set(true);
        System.exit(codigoMija);
    }
}
