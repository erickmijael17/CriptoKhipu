package dev.erick.cryptokipu.ui.capturas;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelReader;

/** PNG RGBA de 8 bits a partir de una {@link Image} de JavaFX, sin depender de javafx.swing ni de AWT. */
public final class EscritorPng {

    private static final byte[] FIRMA_MIJA = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private EscritorPng() {
    }

    public static void escribir(Image imagenMija, Path destinoMija) throws IOException {
        Files.write(destinoMija, codificar(imagenMija));
    }

    public static byte[] codificar(Image imagenMija) throws IOException {
        int anchoMija = (int) imagenMija.getWidth();
        int altoMija = (int) imagenMija.getHeight();
        PixelReader lectorMija = imagenMija.getPixelReader();
        byte[] bgraMija = new byte[anchoMija * altoMija * 4];
        lectorMija.getPixels(0, 0, anchoMija, altoMija, PixelFormat.getByteBgraInstance(), bgraMija, 0, anchoMija * 4);

        ByteArrayOutputStream comprimidoMija = new ByteArrayOutputStream();
        try (DeflaterOutputStream deflateMija = new DeflaterOutputStream(comprimidoMija,
                new Deflater(Deflater.BEST_COMPRESSION))) {
            byte[] filaMija = new byte[anchoMija * 4 + 1];
            for (int yMija = 0; yMija < altoMija; yMija++) {
                filaMija[0] = 0;
                for (int xMija = 0; xMija < anchoMija; xMija++) {
                    int origenMija = (yMija * anchoMija + xMija) * 4;
                    int destinoMija = 1 + xMija * 4;
                    filaMija[destinoMija] = bgraMija[origenMija + 2];
                    filaMija[destinoMija + 1] = bgraMija[origenMija + 1];
                    filaMija[destinoMija + 2] = bgraMija[origenMija];
                    filaMija[destinoMija + 3] = bgraMija[origenMija + 3];
                }
                deflateMija.write(filaMija);
            }
        }

        ByteArrayOutputStream salidaMija = new ByteArrayOutputStream();
        salidaMija.write(FIRMA_MIJA);
        ByteArrayOutputStream cabeceraMija = new ByteArrayOutputStream();
        DataOutputStream datosMija = new DataOutputStream(cabeceraMija);
        datosMija.writeInt(anchoMija);
        datosMija.writeInt(altoMija);
        datosMija.writeByte(8);
        datosMija.writeByte(6);
        datosMija.writeByte(0);
        datosMija.writeByte(0);
        datosMija.writeByte(0);
        bloque(salidaMija, "IHDR", cabeceraMija.toByteArray());
        bloque(salidaMija, "IDAT", comprimidoMija.toByteArray());
        bloque(salidaMija, "IEND", new byte[0]);
        return salidaMija.toByteArray();
    }

    private static void bloque(OutputStream salidaMija, String tipoMija, byte[] datosMija) throws IOException {
        DataOutputStream escritorMija = new DataOutputStream(salidaMija);
        byte[] tipoBytesMija = tipoMija.getBytes(StandardCharsets.US_ASCII);
        escritorMija.writeInt(datosMija.length);
        escritorMija.write(tipoBytesMija);
        escritorMija.write(datosMija);
        CRC32 crcMija = new CRC32();
        crcMija.update(tipoBytesMija);
        crcMija.update(datosMija);
        escritorMija.writeInt((int) crcMija.getValue());
    }
}
