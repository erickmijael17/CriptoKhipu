package dev.erick.cryptokipu.ui.viewmodel;

import java.util.Optional;

/**
 * Lee ancho y alto de la cabecera de una imagen (PNG, JPEG, GIF, BMP) SIN decodificarla, para rechazar bombas de
 * descompresión (un PNG de pocos KB que dice medir 50 000 × 50 000 píxeles pediría 10 GB al decodificar).
 */
public final class DimensionesImagen {

    /** Máximo de píxeles que se decodifican para la vista previa (~100 megapíxeles). */
    public static final long PIXELES_MAXIMOS_MIJA = 100_000_000L;

    public record Dimensiones(int anchoMija, int altoMija) {
        public long pixeles() {
            return (long) anchoMija * altoMija;
        }
    }

    private DimensionesImagen() {
    }

    public static Optional<Dimensiones> leer(byte[] datosMija) {
        if (datosMija.length >= 24 && (datosMija[0] & 0xFF) == 0x89 && datosMija[1] == 'P' && datosMija[2] == 'N'
                && datosMija[3] == 'G') {
            return valida(entero32(datosMija, 16), entero32(datosMija, 20));
        }
        if (datosMija.length >= 10 && datosMija[0] == 'G' && datosMija[1] == 'I' && datosMija[2] == 'F') {
            return valida(entero16Le(datosMija, 6), entero16Le(datosMija, 8));
        }
        if (datosMija.length >= 26 && datosMija[0] == 'B' && datosMija[1] == 'M') {
            return valida(entero32Le(datosMija, 18), Math.abs(entero32Le(datosMija, 22)));
        }
        if (datosMija.length >= 4 && (datosMija[0] & 0xFF) == 0xFF && (datosMija[1] & 0xFF) == 0xD8) {
            return jpeg(datosMija);
        }
        return Optional.empty();
    }

    /** Recorre los segmentos JPEG hasta un SOF (C0–CF salvo C4 DHT, C8 JPG y CC DAC). */
    private static Optional<Dimensiones> jpeg(byte[] datosMija) {
        int posicionMija = 2;
        while (posicionMija + 9 < datosMija.length) {
            if ((datosMija[posicionMija] & 0xFF) != 0xFF) {
                return Optional.empty();
            }
            int marcadorMija = datosMija[posicionMija + 1] & 0xFF;
            if (marcadorMija == 0xFF) {
                posicionMija++;
                continue;
            }
            if (marcadorMija == 0xD8 || (marcadorMija >= 0xD0 && marcadorMija <= 0xD7) || marcadorMija == 0x01) {
                posicionMija += 2;
                continue;
            }
            int longitudMija = entero16(datosMija, posicionMija + 2);
            boolean sofMija = marcadorMija >= 0xC0 && marcadorMija <= 0xCF && marcadorMija != 0xC4
                    && marcadorMija != 0xC8 && marcadorMija != 0xCC;
            if (sofMija) {
                return valida(entero16(datosMija, posicionMija + 7), entero16(datosMija, posicionMija + 5));
            }
            if (marcadorMija == 0xD9 || marcadorMija == 0xDA || longitudMija < 2) {
                return Optional.empty();
            }
            posicionMija += 2 + longitudMija;
        }
        return Optional.empty();
    }

    private static Optional<Dimensiones> valida(long anchoMija, long altoMija) {
        if (anchoMija <= 0 || altoMija <= 0 || anchoMija > Integer.MAX_VALUE || altoMija > Integer.MAX_VALUE) {
            return Optional.empty();
        }
        return Optional.of(new Dimensiones((int) anchoMija, (int) altoMija));
    }

    private static long entero32(byte[] datosMija, int desdeMija) {
        return ((long) (datosMija[desdeMija] & 0xFF) << 24) | ((datosMija[desdeMija + 1] & 0xFF) << 16)
                | ((datosMija[desdeMija + 2] & 0xFF) << 8) | (datosMija[desdeMija + 3] & 0xFF);
    }

    private static int entero32Le(byte[] datosMija, int desdeMija) {
        return (datosMija[desdeMija] & 0xFF) | ((datosMija[desdeMija + 1] & 0xFF) << 8)
                | ((datosMija[desdeMija + 2] & 0xFF) << 16) | (datosMija[desdeMija + 3] << 24);
    }

    private static int entero16(byte[] datosMija, int desdeMija) {
        return ((datosMija[desdeMija] & 0xFF) << 8) | (datosMija[desdeMija + 1] & 0xFF);
    }

    private static int entero16Le(byte[] datosMija, int desdeMija) {
        return (datosMija[desdeMija] & 0xFF) | ((datosMija[desdeMija + 1] & 0xFF) << 8);
    }
}
