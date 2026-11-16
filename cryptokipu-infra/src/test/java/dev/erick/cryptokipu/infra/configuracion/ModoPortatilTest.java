package dev.erick.cryptokipu.infra.configuracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erick.cryptokipu.infra.configuracion.ModoPortatil.Portatil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModoPortatilTest {

    @TempDir
    Path usbMija;

    private Path imagenMija() throws IOException {
        Path raizMija = Files.createDirectories(usbMija.resolve("CriptoKhipu-win"));
        Files.createDirectories(raizMija.resolve("app"));
        return raizMija;
    }

    @Test
    void sinMarcadorNoHayModoPortatil() throws IOException {
        Path raizMija = imagenMija();
        assertEquals(Optional.empty(), ModoPortatil.detectar(raizMija.resolve("app")));
    }

    @Test
    void elMarcadorJuntoALaAppOEnLaRaizDeLaImagenActivaElModo() throws IOException {
        Path raizMija = imagenMija();
        Files.writeString(raizMija.resolve(ModoPortatil.MARCADOR_MIJA), "");
        // $APPDIR de jpackage es <raíz>/app: el marcador está un nivel arriba.
        Portatil desdeAppMija = ModoPortatil.detectar(raizMija.resolve("app")).orElseThrow();
        assertEquals(raizMija.toAbsolutePath().normalize(), desdeAppMija.raizMija());
        assertTrue(desdeAppMija.guardarPreferenciasMija(), "por defecto se guardan junto a la app");
        // Marcador junto a APPDIR (otra distribución).
        Path otraMija = Files.createDirectories(usbMija.resolve("otra"));
        Files.writeString(otraMija.resolve(ModoPortatil.MARCADOR_MIJA), "guardarPreferencias=false");
        assertFalse(ModoPortatil.detectar(otraMija).orElseThrow().guardarPreferenciasMija());
    }

    @Test
    void lasPreferenciasQuedanJuntoALaAppONoSeGuardan() throws IOException {
        Path raizMija = imagenMija();
        Files.writeString(raizMija.resolve(ModoPortatil.MARCADOR_MIJA), "");
        Portatil guardaMija = ModoPortatil.detectar(raizMija).orElseThrow();
        PreferenciasStore almacenMija = guardaMija.preferencias();
        almacenMija.guardar(PreferenciasStore.Clave.TEMA, "OSCURO");
        assertTrue(almacenMija.persistente());
        assertTrue(Files.isRegularFile(raizMija.resolve("datos/preferencias.properties")));

        Path otraMija = Files.createDirectories(usbMija.resolve("sin-guardar"));
        Files.writeString(otraMija.resolve(ModoPortatil.MARCADOR_MIJA), "guardarPreferencias=false");
        PreferenciasStore memoriaMija = ModoPortatil.detectar(otraMija).orElseThrow().preferencias();
        assertFalse(memoriaMija.persistente());
        memoriaMija.guardar(PreferenciasStore.Clave.TEMA, "CLARO");
        memoriaMija.agregarReciente(usbMija.resolve("secreto.kipu"));
        assertEquals(Optional.of("CLARO"), memoriaMija.leer(PreferenciasStore.Clave.TEMA), "vive en memoria");
        try (Stream<Path> archivosMija = Files.list(otraMija)) {
            assertEquals(List.of(ModoPortatil.MARCADOR_MIJA),
                    archivosMija.map(rutaMija -> rutaMija.getFileName().toString()).toList(),
                    "no se escribió nada junto a la app");
        }
    }

    @Test
    void tmpYLogsSeCreanYTmpSeVaciaAlCerrar() throws IOException {
        Path raizMija = imagenMija();
        Files.writeString(raizMija.resolve(ModoPortatil.MARCADOR_MIJA), "");
        Portatil portatilMija = ModoPortatil.detectar(raizMija).orElseThrow();
        portatilMija.prepararCarpetas();
        assertTrue(Files.isDirectory(raizMija.resolve("logs")));
        assertTrue(Files.isDirectory(portatilMija.temporales()));

        Path subcarpetaMija = Files.createDirectories(portatilMija.temporales().resolve("cryptokipu-tmp-abc/dentro"));
        Files.writeString(subcarpetaMija.resolve("copia.txt"), "texto claro");
        Files.writeString(portatilMija.temporales().resolve("suelto.bin"), "x");

        assertEquals(0, portatilMija.vaciarTemporales());
        assertTrue(Files.isDirectory(portatilMija.temporales()), "la carpeta tmp se conserva");
        try (Stream<Path> restosMija = Files.list(portatilMija.temporales())) {
            assertEquals(0, restosMija.count());
        }
    }
}
