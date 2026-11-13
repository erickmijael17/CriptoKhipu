package dev.erick.cryptokipu.infra.storage;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Datos del sistema de archivos donde vive la bóveda.
 *
 * @param tipoSistemaMija   tipo reportado por el sistema operativo ({@code NTFS}, {@code exFAT}, {@code FAT32}, {@code vfat}…)
 * @param espacioUsableMija bytes libres utilizables
 */
public record InfoDestino(String tipoSistemaMija, long espacioUsableMija) {

    private static final Set<String> TIPOS_FAT_MIJA = Set.of("fat", "fat12", "fat16", "fat32", "vfat", "msdos");

    public InfoDestino {
        Objects.requireNonNull(tipoSistemaMija, "tipoSistemaMija");
    }

    /** FAT/FAT32 no admite archivos de más de 4 GiB − 1 B (exFAT sí). */
    public boolean esFat() {
        return TIPOS_FAT_MIJA.contains(tipoSistemaMija.toLowerCase(Locale.ROOT));
    }
}
