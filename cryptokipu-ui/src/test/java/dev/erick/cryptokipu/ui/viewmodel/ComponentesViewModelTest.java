package dev.erick.cryptokipu.ui.viewmodel;

import static dev.erick.cryptokipu.ui.viewmodel.BancoUi.c;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erick.cryptokipu.core.format.EntradaManifest;
import dev.erick.cryptokipu.core.format.LimitesFormato;
import dev.erick.cryptokipu.infra.storage.InfoDestino;
import dev.erick.cryptokipu.infra.storage.PoliticaDestino;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.scene.control.TreeItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** ViewModels sin bóveda: validación en vivo, avisos del destino, asistente y árbol del explorador. */
class ComponentesViewModelTest {

    @TempDir
    Path carpetaMija;

    @Test
    void validacionVivaMarcaCadaReglaYMideLaFortaleza() {
        ValidacionViva f2Mija = new ValidacionViva(ValidacionViva.Factor.F2);
        assertFalse(f2Mija.esValido(), "Vacío no es válido");
        f2Mija.evaluar(c("kipu2demo"));
        assertEquals(List.of(false, true, false, true, true),
                f2Mija.reglas().stream().map(ValidacionViva.EstadoRegla::cumplidaMija).toList());
        double debilMija = f2Mija.fortalezaProperty().get();
        f2Mija.evaluar(c("Kipu2Demo2026xY"));
        assertTrue(f2Mija.esValido());
        assertTrue(f2Mija.fortalezaProperty().get() > debilMija);

        ValidacionViva f1Mija = new ValidacionViva(ValidacionViva.Factor.F1);
        f1Mija.evaluar(c("12345a"));
        assertEquals(2, f1Mija.reglas().stream().filter(reglaMija -> !reglaMija.cumplidaMija()).count());
        ValidacionViva f3Mija = new ValidacionViva(ValidacionViva.Factor.F3);
        f3Mija.evaluar(c("Mi Pérro Ñato come"));
        assertTrue(f3Mija.esValido());
    }

    @Test
    void elAsistenteRechazaSecretosQueNoCoincidenONoCumplenYLosBorra() {
        AsistenteCreacionViewModel asistenteMija = new AsistenteCreacionViewModel(BancoUi.contenedor(carpetaMija),
                BancoUi.DISCO_AMPLIO_MIJA);
        assertTrue(asistenteMija.siguiente(), "Archivos → F1 (archivos opcionales)");
        assertFalse(asistenteMija.puedeAvanzar());
        char[] valorMija = c("482915736028");
        char[] repetidoMija = c("482915736029");
        Optional<String> errorMija = asistenteMija.fijarSecreto(ValidacionViva.Factor.F1, valorMija, repetidoMija);
        assertTrue(errorMija.isPresent());
        assertTrue(new String(valorMija).chars().allMatch(caracterMija -> caracterMija == 0), "valor borrado");
        assertTrue(new String(repetidoMija).chars().allMatch(caracterMija -> caracterMija == 0), "repetido borrado");
        assertTrue(asistenteMija.fijarSecreto(ValidacionViva.Factor.F1, c("123"), c("123")).isPresent());
        assertFalse(asistenteMija.siguiente());
        assertTrue(asistenteMija.fijarSecreto(ValidacionViva.Factor.F1, c("482915736028"), c("482915736028"))
                .isEmpty());
        assertTrue(asistenteMija.siguiente());
        assertEquals(AsistenteCreacionViewModel.Paso.F2, asistenteMija.pasoProperty().get());
        asistenteMija.limpiar();
        assertFalse(asistenteMija.tieneSecreto(ValidacionViva.Factor.F1));
        assertEquals(AsistenteCreacionViewModel.Paso.ARCHIVOS, asistenteMija.pasoProperty().get());
    }

    @Test
    void avisosDelDestino() throws Exception {
        Path sincronizadaMija = carpetaMija.resolve("OneDrive");
        Files.createDirectories(sincronizadaMija);
        List<Aviso> avisosMija = AvisosDestino.evaluar(sincronizadaMija.resolve("b.kipu"), 1024,
                BancoUi.DISCO_AMPLIO_MIJA);
        assertEquals(1, avisosMija.size());
        assertFalse(avisosMija.getFirst().bloqueanteMija(), "Carpeta sincronizada: aviso, no bloqueo");

        PoliticaDestino fatMija = _ -> new InfoDestino("FAT32", 1L << 40);
        assertTrue(AvisosDestino.evaluar(carpetaMija.resolve("b.kipu"), 5L << 30, fatMija).getFirst()
                .bloqueanteMija(), "FAT32 con más de 4 GiB");
        assertTrue(AvisosDestino.evaluar(carpetaMija.resolve("b.kipu"), 1L << 20, fatMija).isEmpty());

        PoliticaDestino llenoMija = _ -> new InfoDestino("NTFS", 1L << 20);
        assertTrue(AvisosDestino.evaluar(carpetaMija.resolve("b.kipu"), 1L << 20, llenoMija).getFirst()
                .bloqueanteMija(), "Sin margen del 5 %");
        assertTrue(AvisosDestino.evaluar(carpetaMija.resolve("no-existe/b.kipu"), 1, BancoUi.DISCO_AMPLIO_MIJA)
                .getFirst().bloqueanteMija());
    }

    @Test
    void elAsistenteNoAvanzaConUnDestinoBloqueadoNiExistente() throws Exception {
        AsistenteCreacionViewModel asistenteMija = new AsistenteCreacionViewModel(BancoUi.contenedor(carpetaMija),
                _ -> new InfoDestino("NTFS", 10));
        asistenteMija.pasoProperty().set(AsistenteCreacionViewModel.Paso.DESTINO);
        asistenteMija.destinoProperty().set(carpetaMija.resolve("b.kipu"));
        asistenteMija.evaluarDestino();
        assertFalse(asistenteMija.puedeAvanzar(), "Sin espacio");

        Path existenteMija = Files.writeString(carpetaMija.resolve("ya.kipu"), "x");
        AsistenteCreacionViewModel otroMija = new AsistenteCreacionViewModel(BancoUi.contenedor(carpetaMija),
                BancoUi.DISCO_AMPLIO_MIJA);
        otroMija.pasoProperty().set(AsistenteCreacionViewModel.Paso.DESTINO);
        otroMija.destinoProperty().set(existenteMija);
        otroMija.evaluarDestino();
        assertFalse(otroMija.puedeAvanzar(), "Nunca sobrescribe una bóveda existente");
    }

    @Test
    void arbolDeCienMilEntradasConFiltro() {
        List<EntradaManifest> entradasMija = new ArrayList<>(100_000);
        for (int indiceMija = 0; indiceMija < 100_000; indiceMija++) {
            byte[] idMija = new byte[16];
            idMija[0] = (byte) indiceMija;
            idMija[1] = (byte) (indiceMija >> 8);
            idMija[2] = (byte) (indiceMija >> 16);
            entradasMija.add(new EntradaManifest(idMija, "carpeta" + indiceMija % 100 + "/sub" + indiceMija % 7
                    + "/archivo" + indiceMija + ".txt", indiceMija, 1_700_000_000_000L, "text/plain", LimitesFormato.OFFSET_DATOS_MIJA, 32));
        }
        ExploradorViewModel exploradorMija = new ExploradorViewModel(BancoUi.contenedor(carpetaMija));
        TreeItem<ExploradorViewModel.Nodo> raizMija = assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> exploradorMija.arbol(entradasMija));
        assertEquals(100, raizMija.getChildren().size());
        assertEquals(100_000, ExploradorViewModel.entradasBajo(raizMija).size());
        TreeItem<ExploradorViewModel.Nodo> primeraCarpetaMija = raizMija.getChildren().getFirst();
        assertTrue(primeraCarpetaMija.getValue().esCarpeta());
        assertEquals(1000, ExploradorViewModel.entradasBajo(primeraCarpetaMija).size());

        exploradorMija.filtroProperty().set("ARCHIVO99999");
        TreeItem<ExploradorViewModel.Nodo> filtradoMija = exploradorMija.arbol(entradasMija);
        List<EntradaManifest> encontradasMija = ExploradorViewModel.entradasBajo(filtradoMija);
        assertEquals(1, encontradasMija.size());
        assertEquals("carpeta99/sub4/archivo99999.txt", encontradasMija.getFirst().rutaMija());
    }
}
