package dev.erick.cryptokipu.infra.fs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erick.cryptokipu.infra.configuracion.PreferenciasStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TempWorkspaceYPreferenciasTest {

    @TempDir
    Path carpetaMija;

    @Test
    void laCarpetaEsPrivadaYSeBorraAlCerrar() throws IOException {
        Path creadaMija;
        try (TempWorkspace espacioMija = TempWorkspace.crear(carpetaMija)) {
            creadaMija = espacioMija.carpeta();
            assertTrue(creadaMija.getFileName().toString().startsWith(TempWorkspace.PREFIJO_MIJA));
            Files.writeString(creadaMija.resolve("abierto con app externa.txt"), "contenido descifrado");

            PosixFileAttributeView posixMija = Files.getFileAttributeView(creadaMija, PosixFileAttributeView.class);
            if (posixMija != null) {
                assertEquals("rwx------", PosixFilePermissions.toString(posixMija.readAttributes().permissions()));
            } else {
                AclFileAttributeView aclMija = Files.getFileAttributeView(creadaMija, AclFileAttributeView.class);
                assertEquals(1, aclMija.getAcl().size(), "una sola entrada: el dueño");
                assertEquals(aclMija.getOwner(), aclMija.getAcl().getFirst().principal());
            }
        }
        assertFalse(Files.exists(creadaMija));
    }

    @Test
    void seLimpianRestosAbandonadosPeroNoLosDeOtraInstanciaViva() throws IOException {
        Path abandonadaMija = Files.createDirectory(carpetaMija.resolve(TempWorkspace.PREFIJO_MIJA + "viejo"));
        Files.writeString(abandonadaMija.resolve("resto.tmp"), "x");
        try (TempWorkspace vivaMija = TempWorkspace.crear(carpetaMija)) {
            assertFalse(Files.exists(abandonadaMija), "resto de una ejecución anterior");
            try (TempWorkspace otraMija = TempWorkspace.crear(carpetaMija)) {
                assertTrue(Files.exists(vivaMija.carpeta()), "la instancia viva conserva su carpeta");
                assertTrue(Files.exists(otraMija.carpeta()));
            }
        }
    }

    @Test
    void usarDespuesDeCerrarFalla() throws IOException {
        TempWorkspace espacioMija = TempWorkspace.crear(carpetaMija);
        espacioMija.close();
        espacioMija.close();
        assertThrows(IllegalStateException.class, espacioMija::carpeta);
    }

    @Test
    void preferenciasSoloConClavesPermitidasYRecientesKipu() throws IOException {
        PreferenciasStore preferenciasMija = PreferenciasStore.portatil(carpetaMija.resolve("app"));
        preferenciasMija.guardar(PreferenciasStore.Clave.TEMA, "oscuro");
        preferenciasMija.agregarReciente(carpetaMija.resolve("a.kipu"));
        preferenciasMija.agregarReciente(carpetaMija.resolve("b.kipu"));
        preferenciasMija.agregarReciente(carpetaMija.resolve("a.kipu"));

        assertEquals(Optional.of("oscuro"), preferenciasMija.leer(PreferenciasStore.Clave.TEMA));
        assertEquals(List.of(carpetaMija.resolve("a.kipu").toAbsolutePath(), carpetaMija.resolve("b.kipu").toAbsolutePath()),
                preferenciasMija.recientes());
        assertThrows(IllegalArgumentException.class,
                () -> preferenciasMija.agregarReciente(carpetaMija.resolve("fotos/secreto.jpg")));
        assertThrows(IllegalArgumentException.class,
                () -> preferenciasMija.guardar(PreferenciasStore.Clave.IDIOMA, "es\ncontrasena=1234"));

        Files.writeString(carpetaMija.resolve("app/preferencias.properties"), "contrasena=1234\n",
                java.nio.file.StandardOpenOption.APPEND);
        preferenciasMija.guardar(PreferenciasStore.Clave.IDIOMA, "es");
        assertFalse(Files.readString(carpetaMija.resolve("app/preferencias.properties")).contains("contrasena"),
                "las claves desconocidas se descartan");
    }
}
