package dev.erick.cryptokipu.ui.viewmodel;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.code_intelligence.jazzer.junit.FuzzTest;
import java.util.Optional;

/**
 * Fuzzing del lector de cabeceras de imagen (PNG, GIF, BMP, JPEG): para CUALQUIER entrada responde sin lanzar y, si
 * devuelve dimensiones, son positivas y su producto no desborda. El tope de píxeles lo aplica quien llama
 * ({@code ExploradorViewModel}) comparando {@code pixeles()}, justamente por eso tiene que ser exacto.
 */
class FuzzImagenTest {

    @FuzzTest(maxDuration = "2m")
    void leerCabeceraNuncaLanzaYRespetaElTope(byte[] datosMija) {
        Optional<DimensionesImagen.Dimensiones> dimensionesMija = DimensionesImagen.leer(datosMija);
        dimensionesMija.ifPresent(encontradasMija -> {
            assertTrue(encontradasMija.anchoMija() > 0 && encontradasMija.altoMija() > 0, encontradasMija.toString());
            assertTrue(encontradasMija.pixeles() >= (long) encontradasMija.anchoMija(), encontradasMija.toString());
        });
    }
}
