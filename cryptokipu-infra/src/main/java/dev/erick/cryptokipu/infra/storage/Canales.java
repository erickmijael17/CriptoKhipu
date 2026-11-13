package dev.erick.cryptokipu.infra.storage;

import dev.erick.cryptokipu.core.error.ErrorBoveda;
import dev.erick.cryptokipu.core.error.ExcepcionBoveda;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Objects;

/** Lectura y escritura posicional sobre un {@link FileChannel}, sin mover su posición. */
final class Canales {

    private Canales() {
    }

    static byte[] leer(FileChannel canalMija, long posicionMija, int longitudMija, ErrorBoveda.IntegridadFallida.Zona zonaMija)
            throws IOException {
        ByteBuffer bufferMija = ByteBuffer.allocate(longitudMija);
        long actualMija = posicionMija;
        while (bufferMija.hasRemaining()) {
            int leidosMija = canalMija.read(bufferMija, actualMija);
            if (leidosMija < 0) {
                throw new ExcepcionBoveda(new ErrorBoveda.IntegridadFallida(zonaMija));
            }
            actualMija += leidosMija;
        }
        return bufferMija.array();
    }

    static void escribir(FileChannel canalMija, long posicionMija, byte[] datosMija) throws IOException {
        escribir(canalMija, posicionMija, datosMija, 0, datosMija.length);
    }

    static void escribir(FileChannel canalMija, long posicionMija, byte[] datosMija, int desdeMija, int longitudMija)
            throws IOException {
        ByteBuffer bufferMija = ByteBuffer.wrap(datosMija, desdeMija, longitudMija);
        long actualMija = posicionMija;
        while (bufferMija.hasRemaining()) {
            actualMija += canalMija.write(bufferMija, actualMija);
        }
    }

    /** Flujo de salida que escribe a partir de una posición y cuenta los bytes escritos. */
    static final class Salida extends OutputStream {

        private final FileChannel canalMija;
        private long posicionMija;
        private long escritosMija;

        Salida(FileChannel canalMija, long posicionMija) {
            this.canalMija = Objects.requireNonNull(canalMija, "canalMija");
            this.posicionMija = posicionMija;
        }

        long escritos() {
            return escritosMija;
        }

        @Override
        public void write(int byteMija) throws IOException {
            write(new byte[] {(byte) byteMija}, 0, 1);
        }

        @Override
        public void write(byte[] datosMija, int desdeMija, int longitudMija) throws IOException {
            escribir(canalMija, posicionMija, datosMija, desdeMija, longitudMija);
            posicionMija += longitudMija;
            escritosMija += longitudMija;
        }
    }

    /** Flujo de entrada sobre el rango {@code [inicio, inicio + longitud)} del canal. */
    static final class Rango extends InputStream {

        private final FileChannel canalMija;
        private long posicionMija;
        private long restantesMija;

        Rango(FileChannel canalMija, long inicioMija, long longitudMija) {
            this.canalMija = Objects.requireNonNull(canalMija, "canalMija");
            this.posicionMija = inicioMija;
            this.restantesMija = longitudMija;
        }

        @Override
        public int read() throws IOException {
            byte[] unoMija = new byte[1];
            int leidosMija = read(unoMija, 0, 1);
            return leidosMija < 0 ? -1 : Byte.toUnsignedInt(unoMija[0]);
        }

        @Override
        public int read(byte[] destinoMija, int desdeMija, int longitudMija) throws IOException {
            if (restantesMija <= 0) {
                return -1;
            }
            int pedidoMija = (int) Math.min(longitudMija, restantesMija);
            int leidosMija = canalMija.read(ByteBuffer.wrap(destinoMija, desdeMija, pedidoMija), posicionMija);
            if (leidosMija < 0) {
                return -1;
            }
            posicionMija += leidosMija;
            restantesMija -= leidosMija;
            return leidosMija;
        }
    }
}
