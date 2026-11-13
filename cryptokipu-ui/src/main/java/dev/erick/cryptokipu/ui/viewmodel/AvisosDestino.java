package dev.erick.cryptokipu.ui.viewmodel;

import static dev.erick.cryptokipu.ui.Textos.t;

import dev.erick.cryptokipu.core.format.LimitesFormato;
import dev.erick.cryptokipu.infra.fs.DetectorCarpetaSincronizada;
import dev.erick.cryptokipu.infra.storage.InfoDestino;
import dev.erick.cryptokipu.infra.storage.PoliticaDestino;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Avisos sobre el lugar donde se guardará la bóveda, mostrados ANTES de escribir: carpeta sincronizada (aviso),
 * FAT32 con más de 4 GiB y falta de espacio (bloqueantes; los mismos límites que verifica el contenedor).
 */
public final class AvisosDestino {

    private AvisosDestino() {
    }

    public static List<Aviso> evaluar(Path destinoMija, long bytesEstimadosMija, PoliticaDestino politicaMija) {
        List<Aviso> avisosMija = new ArrayList<>();
        DetectorCarpetaSincronizada.servicio(destinoMija).ifPresent(servicioMija ->
                avisosMija.add(new Aviso(t("aviso.sincronizada", servicioMija), false)));
        Path carpetaMija = destinoMija.toAbsolutePath().getParent();
        if (carpetaMija == null || !Files.isDirectory(carpetaMija)) {
            avisosMija.add(new Aviso(t("aviso.carpetaInexistente"), true));
            return avisosMija;
        }
        try {
            InfoDestino infoMija = politicaMija.describir(carpetaMija);
            if (infoMija.esFat() && bytesEstimadosMija > LimitesFormato.LIMITE_FAT32_MIJA) {
                avisosMija.add(new Aviso(t("aviso.fat32", infoMija.tipoSistemaMija()), true));
            }
            if (infoMija.espacioUsableMija() < bytesEstimadosMija * 105 / 100) {
                avisosMija.add(new Aviso(t("aviso.espacio", bytesEstimadosMija / (1024 * 1024),
                        infoMija.espacioUsableMija() / (1024 * 1024)), true));
            }
        } catch (IOException _) {
            avisosMija.add(new Aviso(t("aviso.destinoIlegible"), true));
        }
        return avisosMija;
    }
}
