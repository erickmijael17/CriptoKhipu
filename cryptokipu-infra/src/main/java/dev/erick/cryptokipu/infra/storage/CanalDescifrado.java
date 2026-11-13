package dev.erick.cryptokipu.infra.storage;

import dev.erick.cryptokipu.core.crypto.ChunkCipher;
import dev.erick.cryptokipu.core.crypto.FalloAutenticacion;
import dev.erick.cryptokipu.core.crypto.SecureBytes;
import dev.erick.cryptokipu.core.error.ErrorBoveda;
import dev.erick.cryptokipu.core.error.ErrorBoveda.IntegridadFallida.Zona;
import dev.erick.cryptokipu.core.error.ExcepcionBoveda;
import dev.erick.cryptokipu.core.format.EntradaManifest;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.SeekableByteChannel;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Canal de solo lectura con acceso aleatorio sobre un archivo de la bóveda (plan, Fase 4 punto 5).
 *
 * <p>Posición → índice de bloque ({@code posición / 1 MiB}); cada bloque se lee por el canal único del
 * contenedor (ADR-010), se verifica y se descifra antes de entregar bytes. Un bloque manipulado falla solo al
 * leer una posición dentro de ese bloque. Conserva en memoria el último bloque descifrado; nunca escribe a disco.
 */
final class CanalDescifrado implements SeekableByteChannel {

    private static final int BLOQUE_MIJA = ChunkCipher.TAMANO_BLOQUE_MIJA;
    private static final int BLOQUE_CIFRADO_MIJA = ChunkCipher.TAMANO_BLOQUE_CIFRADO_MIJA;

    private final FileChannel canalMija;
    private final EntradaManifest entradaMija;
    private final SecureBytes claveArchivoMija;
    private final Consumer<CanalDescifrado> alCerrarMija;
    private final long bloquesMija;
    private long posicionMija;
    private long indiceCacheMija = -1;
    private byte[] cacheMija;
    private boolean abiertoMija = true;

    CanalDescifrado(FileChannel canalMija, EntradaManifest entradaMija, SecureBytes claveArchivoMija,
            Consumer<CanalDescifrado> alCerrarMija) {
        this.canalMija = Objects.requireNonNull(canalMija, "canalMija");
        this.entradaMija = Objects.requireNonNull(entradaMija, "entradaMija");
        this.claveArchivoMija = Objects.requireNonNull(claveArchivoMija, "claveArchivoMija");
        this.alCerrarMija = Objects.requireNonNull(alCerrarMija, "alCerrarMija");
        if (entradaMija.longitudCifradaMija() != ChunkCipher.tamanoCifrado(entradaMija.tamanoMija())) {
            claveArchivoMija.close();
            throw new ExcepcionBoveda(new ErrorBoveda.IntegridadFallida(Zona.MANIFEST));
        }
        this.bloquesMija = Math.max(1, (entradaMija.tamanoMija() + BLOQUE_MIJA - 1) / BLOQUE_MIJA);
    }

    @Override
    public synchronized int read(ByteBuffer destinoMija) throws IOException {
        exigirAbierto();
        if (posicionMija >= entradaMija.tamanoMija()) {
            return -1;
        }
        int copiadosMija = 0;
        while (destinoMija.hasRemaining() && posicionMija < entradaMija.tamanoMija()) {
            long indiceMija = posicionMija / BLOQUE_MIJA;
            byte[] bloqueMija = bloque(indiceMija);
            int desdeMija = (int) (posicionMija - indiceMija * BLOQUE_MIJA);
            int cantidadMija = Math.min(destinoMija.remaining(), bloqueMija.length - desdeMija);
            destinoMija.put(bloqueMija, desdeMija, cantidadMija);
            posicionMija += cantidadMija;
            copiadosMija += cantidadMija;
        }
        return copiadosMija;
    }

    @Override
    public int write(ByteBuffer origenMija) {
        throw new NonWritableChannelException();
    }

    @Override
    public synchronized long position() throws IOException {
        exigirAbierto();
        return posicionMija;
    }

    @Override
    public synchronized SeekableByteChannel position(long nuevaPosicionMija) throws IOException {
        exigirAbierto();
        if (nuevaPosicionMija < 0) {
            throw new IllegalArgumentException("Posición negativa");
        }
        posicionMija = nuevaPosicionMija;
        return this;
    }

    @Override
    public long size() throws IOException {
        exigirAbierto();
        return entradaMija.tamanoMija();
    }

    @Override
    public SeekableByteChannel truncate(long tamanoMija) {
        throw new NonWritableChannelException();
    }

    @Override
    public synchronized boolean isOpen() {
        return abiertoMija;
    }

    @Override
    public synchronized void close() {
        if (!abiertoMija) {
            return;
        }
        abiertoMija = false;
        claveArchivoMija.close();
        if (cacheMija != null) {
            Arrays.fill(cacheMija, (byte) 0);
            cacheMija = null;
        }
        alCerrarMija.accept(this);
    }

    private byte[] bloque(long indiceMija) throws IOException {
        if (indiceMija == indiceCacheMija) {
            return cacheMija;
        }
        boolean esUltimoMija = indiceMija == bloquesMija - 1;
        long inicioMija = indiceMija * BLOQUE_CIFRADO_MIJA;
        int longitudMija = (int) (esUltimoMija ? entradaMija.longitudCifradaMija() - inicioMija : BLOQUE_CIFRADO_MIJA);
        byte[] cifradoMija = Canales.leer(canalMija, entradaMija.offsetMija() + inicioMija, longitudMija, Zona.BLOQUE);
        byte[] textoMija;
        try {
            textoMija = ChunkCipher.descifrarBloque(claveArchivoMija, entradaMija.fileIdMija(), indiceMija,
                    esUltimoMija, cifradoMija);
        } catch (FalloAutenticacion _) {
            throw new ExcepcionBoveda(new ErrorBoveda.IntegridadFallida(Zona.BLOQUE));
        }
        if (cacheMija != null) {
            Arrays.fill(cacheMija, (byte) 0);
        }
        cacheMija = textoMija;
        indiceCacheMija = indiceMija;
        return textoMija;
    }

    private void exigirAbierto() throws ClosedChannelException {
        if (!abiertoMija) {
            throw new ClosedChannelException();
        }
    }
}
