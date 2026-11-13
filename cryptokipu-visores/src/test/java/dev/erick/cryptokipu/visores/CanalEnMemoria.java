package dev.erick.cryptokipu.visores;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;

/** Canal de solo lectura sobre un arreglo (como el que entrega {@code AbrirEnMemoria}: nada en disco). */
final class CanalEnMemoria implements SeekableByteChannel {

    private final byte[] datosMija;
    private int posicionMija;
    private boolean abiertoMija = true;

    CanalEnMemoria(byte[] datosMija) {
        this.datosMija = datosMija;
    }

    @Override
    public int read(ByteBuffer destinoMija) {
        if (posicionMija >= datosMija.length) {
            return -1;
        }
        int cantidadMija = Math.min(destinoMija.remaining(), datosMija.length - posicionMija);
        destinoMija.put(datosMija, posicionMija, cantidadMija);
        posicionMija += cantidadMija;
        return cantidadMija;
    }

    @Override
    public int write(ByteBuffer origenMija) throws IOException {
        throw new IOException("solo lectura");
    }

    @Override
    public long position() {
        return posicionMija;
    }

    @Override
    public SeekableByteChannel position(long nuevaMija) {
        posicionMija = (int) nuevaMija;
        return this;
    }

    @Override
    public long size() {
        return datosMija.length;
    }

    @Override
    public SeekableByteChannel truncate(long tamanoMija) throws IOException {
        throw new IOException("solo lectura");
    }

    @Override
    public boolean isOpen() {
        return abiertoMija;
    }

    @Override
    public void close() {
        abiertoMija = false;
    }
}
