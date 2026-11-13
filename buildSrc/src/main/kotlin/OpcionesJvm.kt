/**
 * Opciones de JVM centralizadas (plan, Fase 0 tarea 13 y Fase 7.4).
 *
 * Las usan las pruebas, la tarea `run` y, en la Fase 10, `jpackage --java-options`.
 * Objetivo: que la JVM no deje rastros en el equipo anfitrión ni exponga la memoria.
 */
object OpcionesJvm {

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
    fun conCarpeta(carpeta: String): List<String> = comunes + listOf(
        "-XX:ErrorFile=$carpeta/logs/hs_err_%p.log",
        "-Djava.io.tmpdir=$carpeta/tmp",
    )
}
