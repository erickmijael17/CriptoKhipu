package dev.erick.cryptokipu.core.crypto;

import dev.erick.cryptokipu.core.registro.Registro;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bloqueo de páginas de memoria (best-effort) para que las claves no lleguen al archivo de paginación o al swap:
 * {@code VirtualLock} en Windows, {@code mlock} en Linux y macOS, llamados con FFM.
 *
 * <p>Si el sistema no concede el bloqueo y es Windows, se intenta ampliar el conjunto de trabajo del proceso
 * ({@code SetProcessWorkingSetSize}) y reintentar una vez. Si aun así falla (cuota, {@code RLIMIT_MEMLOCK}, acceso nativo
 * denegado) la aplicación sigue funcionando y se registra <b>una sola vez</b> un aviso sin datos sensibles. Reduce el
 * riesgo de swap pero no lo elimina: no cubre la hibernación (THREAT_MODEL).
 */
@SuppressWarnings("restricted") // FFM: el acceso nativo se concede con --enable-native-access (OpcionesJvm)
final class BloqueoMemoria {

    private static final System.Logger REGISTRO_MIJA = Registro.para(BloqueoMemoria.class);
    private static final AtomicBoolean AVISADO_MIJA = new AtomicBoolean();
    private static final Funciones FUNCIONES_MIJA = Funciones.cargar();
    /** Margen que se agrega al conjunto de trabajo cuando Windows rechaza el bloqueo. */
    private static final long MARGEN_CONJUNTO_MIJA = 1L << 20;

    private record Funciones(MethodHandle bloquearMija, MethodHandle desbloquearMija, MethodHandle procesoActualMija,
            MethodHandle leerConjuntoMija, MethodHandle fijarConjuntoMija, boolean estiloWindowsMija, long paginaMija) {

        static Funciones cargar() {
            String sistemaMija = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            boolean windowsMija = sistemaMija.contains("win");
            try {
                Linker enlazadorMija = Linker.nativeLinker();
                SymbolLookup busquedaMija = windowsMija
                        ? SymbolLookup.libraryLookup("kernel32", Arena.global())
                        : enlazadorMija.defaultLookup();
                FunctionDescriptor firmaMija = FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
                        ValueLayout.JAVA_LONG);
                MethodHandle bloqueoMija = enlazadorMija.downcallHandle(
                        busquedaMija.find(windowsMija ? "VirtualLock" : "mlock").orElseThrow(), firmaMija);
                MethodHandle desbloqueoMija = enlazadorMija.downcallHandle(
                        busquedaMija.find(windowsMija ? "VirtualUnlock" : "munlock").orElseThrow(), firmaMija);
                MethodHandle procesoMija = null;
                MethodHandle leerMija = null;
                MethodHandle fijarMija = null;
                if (windowsMija) {
                    procesoMija = enlazadorMija.downcallHandle(busquedaMija.find("GetCurrentProcess").orElseThrow(),
                            FunctionDescriptor.of(ValueLayout.ADDRESS));
                    leerMija = enlazadorMija.downcallHandle(busquedaMija.find("GetProcessWorkingSetSize").orElseThrow(),
                            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                                    ValueLayout.ADDRESS));
                    fijarMija = enlazadorMija.downcallHandle(busquedaMija.find("SetProcessWorkingSetSize").orElseThrow(),
                            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
                                    ValueLayout.JAVA_LONG));
                }
                boolean macArmMija = sistemaMija.contains("mac")
                        && System.getProperty("os.arch", "").toLowerCase(Locale.ROOT).contains("aarch64");
                return new Funciones(bloqueoMija, desbloqueoMija, procesoMija, leerMija, fijarMija, windowsMija,
                        macArmMija ? 16384 : 4096);
            } catch (Throwable errorMija) { // NOPMD: cualquier fallo (permisos nativos, símbolo ausente) = sin bloqueo
                avisar("el acceso nativo no está disponible (" + errorMija.getClass().getSimpleName() + ")");
                return null;
            }
        }
    }

    private BloqueoMemoria() {
    }

    /** Tamaño de página del sistema (4 KiB salvo macOS en ARM). */
    static long pagina() {
        return FUNCIONES_MIJA == null ? 4096 : FUNCIONES_MIJA.paginaMija();
    }

    /** Bloquea {@code bytesMija} bytes desde el inicio del bloque. Devuelve {@code false} (sin lanzar) si no pudo. */
    static boolean bloquear(MemorySegment bloqueMija, long bytesMija) {
        if (FUNCIONES_MIJA == null) {
            return false;
        }
        try {
            if (intentar(bloqueMija, bytesMija)) {
                return true;
            }
            if (FUNCIONES_MIJA.estiloWindowsMija() && ampliarConjuntoDeTrabajo(bytesMija) && intentar(bloqueMija, bytesMija)) {
                return true;
            }
            avisar("el sistema no permitió bloquear más páginas (límite de memoria bloqueada)");
            return false;
        } catch (Throwable errorMija) { // NOPMD
            avisar("falló la llamada de bloqueo (" + errorMija.getClass().getSimpleName() + ")");
            return false;
        }
    }

    private static boolean intentar(MemorySegment bloqueMija, long bytesMija) throws Throwable {
        int resultadoMija = (int) FUNCIONES_MIJA.bloquearMija().invokeExact(bloqueMija, bytesMija);
        return FUNCIONES_MIJA.estiloWindowsMija() ? resultadoMija != 0 : resultadoMija == 0;
    }

    /** Windows: sube el mínimo y el máximo del conjunto de trabajo del proceso para que quepan las páginas bloqueadas. */
    private static boolean ampliarConjuntoDeTrabajo(long bytesMija) throws Throwable {
        try (Arena arenaMija = Arena.ofConfined()) {
            MemorySegment minimoMija = arenaMija.allocate(ValueLayout.JAVA_LONG);
            MemorySegment maximoMija = arenaMija.allocate(ValueLayout.JAVA_LONG);
            MemorySegment procesoMija = (MemorySegment) FUNCIONES_MIJA.procesoActualMija().invokeExact();
            int leidoMija = (int) FUNCIONES_MIJA.leerConjuntoMija().invokeExact(procesoMija, minimoMija, maximoMija);
            if (leidoMija == 0) {
                return false;
            }
            long nuevoMinimoMija = minimoMija.get(ValueLayout.JAVA_LONG, 0) + bytesMija + MARGEN_CONJUNTO_MIJA;
            long nuevoMaximoMija = Math.max(maximoMija.get(ValueLayout.JAVA_LONG, 0), nuevoMinimoMija + MARGEN_CONJUNTO_MIJA);
            return (int) FUNCIONES_MIJA.fijarConjuntoMija().invokeExact(procesoMija, nuevoMinimoMija, nuevoMaximoMija) != 0;
        }
    }

    /** Libera el bloqueo de un bloque que {@link #bloquear} bloqueó. Nunca lanza. */
    static void desbloquear(MemorySegment bloqueMija, long bytesMija) {
        if (FUNCIONES_MIJA == null) {
            return;
        }
        try {
            int _ = (int) FUNCIONES_MIJA.desbloquearMija().invokeExact(bloqueMija, bytesMija);
        } catch (Throwable _) { // NOPMD
            // Si no se pudo desbloquear, el sistema lo libera al liberar la memoria.
        }
    }

    /** {@code true} si este equipo permite llamar a las funciones de bloqueo (no garantiza que cada llamada triunfe). */
    static boolean disponible() {
        return FUNCIONES_MIJA != null;
    }

    /** {@code true} si ya se registró el aviso (único) de que el bloqueo no fue posible. */
    static boolean avisoEmitido() {
        return AVISADO_MIJA.get();
    }

    private static void avisar(String motivoMija) {
        if (AVISADO_MIJA.compareAndSet(false, true)) {
            REGISTRO_MIJA.log(System.Logger.Level.WARNING,
                    "Las claves siguen fuera del heap pero no se bloquearon en RAM: {0}. "
                            + "Podrían llegar al archivo de paginación.", motivoMija);
        }
    }
}
