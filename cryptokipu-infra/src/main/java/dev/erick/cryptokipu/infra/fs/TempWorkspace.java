package dev.erick.cryptokipu.infra.fs;

import dev.erick.cryptokipu.core.registro.Registro;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/**
 * Carpeta temporal privada para "abrir con aplicación externa" (plan, Fase 4 y 7.2).
 *
 * <ul>
 *   <li>Permisos solo para el dueño: POSIX {@code 700}; en Windows, una ACL con una sola entrada para el dueño.</li>
 *   <li>Se borra al cerrar la sesión y, si la JVM termina sin cerrarla, con un shutdown hook.</li>
 *   <li>Al crear una nueva se limpian restos de ejecuciones anteriores. Cada carpeta viva mantiene un bloqueo
 *       sobre su archivo {@code .bloqueo}: solo se borran las que nadie bloquea.</li>
 * </ul>
 * En SSD el borrado no garantiza que los datos desaparezcan físicamente (THREAT_MODEL).
 */
public final class TempWorkspace implements AutoCloseable {

    public static final String PREFIJO_MIJA = "cryptokipu-tmp-";
    private static final String BLOQUEO_MIJA = ".bloqueo";
    private static final int INTENTOS_BORRADO_MIJA = 5;
    private static final java.time.Duration PAUSA_BORRADO_MIJA = java.time.Duration.ofMillis(200);
    private static final System.Logger REGISTRO_MIJA = Registro.para(TempWorkspace.class);

    private final Path carpetaMija;
    private final FileChannel canalBloqueoMija;
    private final FileLock bloqueoMija;
    private final Thread ganchoMija;
    private boolean cerradoMija;

    private TempWorkspace(Path carpetaMija, FileChannel canalBloqueoMija, FileLock bloqueoMija) {
        this.carpetaMija = carpetaMija;
        this.canalBloqueoMija = canalBloqueoMija;
        this.bloqueoMija = bloqueoMija;
        this.ganchoMija = new Thread(this::borrarSilencioso, "cryptokipu-limpieza-temporal");
        Runtime.getRuntime().addShutdownHook(ganchoMija);
    }

    /** Crea la carpeta privada dentro de {@code baseMija}, después de limpiar restos abandonados. */
    public static TempWorkspace crear(Path baseMija) throws IOException {
        Objects.requireNonNull(baseMija, "baseMija");
        Files.createDirectories(baseMija);
        limpiarRestos(baseMija);
        Path carpetaMija = Files.createTempDirectory(baseMija, PREFIJO_MIJA);
        restringirAlDueno(carpetaMija);
        FileChannel canalMija = FileChannel.open(carpetaMija.resolve(BLOQUEO_MIJA), StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
        return new TempWorkspace(carpetaMija, canalMija, canalMija.lock());
    }

    public Path carpeta() {
        exigirAbierto();
        return carpetaMija;
    }

    /** Borra las carpetas {@code cryptokipu-tmp-*} que ninguna instancia tiene bloqueadas. Devuelve cuántas borró. */
    public static int limpiarRestos(Path baseMija) throws IOException {
        int borradasMija = 0;
        try (DirectoryStream<Path> contenidoMija = Files.newDirectoryStream(baseMija, PREFIJO_MIJA + "*")) {
            for (Path carpetaMija : contenidoMija) {
                if (Files.isDirectory(carpetaMija) && estaAbandonada(carpetaMija)) {
                    borrarRecursivo(carpetaMija);
                    borradasMija++;
                }
            }
        }
        return borradasMija;
    }

    @Override
    public synchronized void close() {
        if (cerradoMija) {
            return;
        }
        borrarSilencioso();
        try {
            Runtime.getRuntime().removeShutdownHook(ganchoMija);
        } catch (IllegalStateException _) {
            // La JVM ya está terminando: el gancho se ejecuta solo.
        }
    }

    private synchronized void borrarSilencioso() {
        if (cerradoMija) {
            return;
        }
        cerradoMija = true;
        try {
            bloqueoMija.release();
            canalBloqueoMija.close();
        } catch (IOException _) {
            // Sin bloqueo la carpeta queda "abandonada": la limpia el próximo inicio aunque falle el borrado.
        }
        // En Windows la aplicación externa puede tener el archivo abierto: se reintenta unos instantes y, si
        // sigue bloqueado, la carpeta (ya sin .bloqueo activo) se borra con limpiarRestos al próximo inicio.
        for (int intentoMija = 1; intentoMija <= INTENTOS_BORRADO_MIJA; intentoMija++) {
            try {
                borrarRecursivo(carpetaMija);
                return;
            } catch (IOException _) {
                if (intentoMija < INTENTOS_BORRADO_MIJA) {
                    try {
                        Thread.sleep(PAUSA_BORRADO_MIJA);
                    } catch (InterruptedException _) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        REGISTRO_MIJA.log(System.Logger.Level.WARNING,
                "No se pudo borrar la carpeta temporal privada (archivo en uso); se limpiará al próximo inicio");
    }

    private void exigirAbierto() {
        if (cerradoMija) {
            throw new IllegalStateException("La carpeta temporal ya se cerró");
        }
    }

    private static boolean estaAbandonada(Path carpetaMija) {
        Path bloqueoMija = carpetaMija.resolve(BLOQUEO_MIJA);
        if (!Files.exists(bloqueoMija)) {
            return true;
        }
        try (FileChannel canalMija = FileChannel.open(bloqueoMija, StandardOpenOption.WRITE)) {
            FileLock pruebaMija = canalMija.tryLock();
            if (pruebaMija == null) {
                return false;
            }
            pruebaMija.release();
            return true;
        } catch (OverlappingFileLockException _) {
            return false;
        } catch (IOException _) {
            return false; // en Windows, un archivo bloqueado por otro proceso no se puede ni abrir
        }
    }

    private static void restringirAlDueno(Path carpetaMija) throws IOException {
        PosixFileAttributeView posixMija = Files.getFileAttributeView(carpetaMija, PosixFileAttributeView.class);
        if (posixMija != null) {
            posixMija.setPermissions(PosixFilePermissions.fromString("rwx------"));
            return;
        }
        AclFileAttributeView aclMija = Files.getFileAttributeView(carpetaMija, AclFileAttributeView.class);
        if (aclMija != null) {
            UserPrincipal duenoMija = aclMija.getOwner();
            AclEntry entradaMija = AclEntry.newBuilder()
                    .setType(AclEntryType.ALLOW)
                    .setPrincipal(duenoMija)
                    .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                    .setFlags(EnumSet.of(java.nio.file.attribute.AclEntryFlag.FILE_INHERIT,
                            java.nio.file.attribute.AclEntryFlag.DIRECTORY_INHERIT))
                    .build();
            aclMija.setAcl(List.of(entradaMija));
        }
    }

    private static void borrarRecursivo(Path carpetaMija) throws IOException {
        if (!Files.exists(carpetaMija)) {
            return;
        }
        Files.walkFileTree(carpetaMija, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path archivoMija, BasicFileAttributes atributosMija) throws IOException {
                Files.delete(archivoMija);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directorioMija, IOException errorMija) throws IOException {
                Files.delete(directorioMija);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
