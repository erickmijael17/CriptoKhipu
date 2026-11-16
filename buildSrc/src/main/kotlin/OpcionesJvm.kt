/**
 * Opciones de JVM centralizadas (plan, Fase 0 tarea 13 y Fase 7.4).
 *
 * Las usan las pruebas, la tarea `run` y, en la Fase 10, `jpackage --java-options`.
 * Objetivo: que la JVM no deje rastros en el equipo anfitrión ni exponga la memoria.
 */
object OpcionesJvm {

    /** Módulo con código FFM (Fase 8: claves fuera del heap + VirtualLock/mlock). */
    const val MODULO_NATIVO = "dev.erick.cryptokipu.core"

    /** Ejecución en la ruta de módulos (`run`, jpackage): el acceso nativo se concede al módulo por nombre. */
    const val ACCESO_NATIVO_MODULOS = "--enable-native-access=$MODULO_NATIVO"

    /** Ejecución en classpath (pruebas, JMH): el código del núcleo está en el módulo sin nombre. */
    const val ACCESO_NATIVO_CLASSPATH = "--enable-native-access=ALL-UNNAMED"

    /** Opciones comunes a cualquier ejecución de CryptoKipu. */
    val comunes: List<String> = listOf(
        "-XX:-UsePerfData",                // no escribe hsperfdata en %TEMP%
        "-XX:-HeapDumpOnOutOfMemoryError", // nunca volcar el heap (contiene claves)
        "-XX:-CreateCoredumpOnCrash",      // sin core dump del proceso al caerse
        "-XX:+DisableAttachMechanism",     // jcmd/jmap no pueden conectarse a la JVM
        "-XX:MaxRAMPercentage=70",         // espacio para Argon2 hasta 1 GiB
    )

    /**
     * Opciones que dependen de una carpeta propia de la ejecución:
     * log de caídas y temporales quedan dentro de [carpeta], nunca en el perfil del usuario.
     *
     * En `jpackage` la carpeta será `$APPDIR/..` (expandida por el lanzador).
     */
    fun conCarpeta(carpeta: String): List<String> = comunes + ACCESO_NATIVO_CLASSPATH + listOf(
        "-XX:ErrorFile=$carpeta/logs/hs_err_%p.log",
        "-Djava.io.tmpdir=$carpeta/tmp",
    )

    /**
     * Opciones de `jpackage --java-options` del modo portátil (plan, Fase 7.4). `$APPDIR` lo expande el lanzador
     * de jpackage (en una línea de comandos hay que escaparlo: `\$APPDIR`). Todo lo que la JVM pueda escribir
     * (temporales, log de caídas) queda junto a la app, nunca en el anfitrión.
     */
    val portatil: List<String> = comunes + ACCESO_NATIVO_MODULOS + listOf(
        "-XX:ErrorFile=\$APPDIR/logs/hs_err_%p.log",
        "-Djava.io.tmpdir=\$APPDIR/tmp",
        "-Djava.awt.headless=true",        // Java2D de PDFBox/POI sin pantalla; JavaFX no depende de AWT
        "-Dcryptokipu.appdir=\$APPDIR",    // la app busca portable.properties aquí y en la carpeta superior
    )
}

/**
 * Identidad del paquete de `jpackage` (ADR-014). El nombre es el comercial; el UUID de actualización de Windows y el
 * identificador de macOS se fijan UNA vez: si cambian, una versión nueva convive con la vieja en vez de reemplazarla.
 */
object IdentidadPaquete {
    const val NOMBRE = "CriptoKhipu"
    const val WIN_UPGRADE_UUID = "7fd13518-ad1c-4405-8186-0957ebbe6a35"
    const val MAC_PACKAGE_ID = "dev.erick.criptokhipu"

    /** Opciones de identidad, una por elemento, tal como se pasan a `jpackage`. */
    val argumentos: List<String> = listOf(
        "--name", NOMBRE,
        "--win-upgrade-uuid", WIN_UPGRADE_UUID,
        "--mac-package-identifier", MAC_PACKAGE_ID,
    )
}
