package dev.erick.cryptokipu.core.error;

/**
 * Errores que puede devolver cualquier operación sobre una bóveda.
 *
 * <p>Jerarquía cerrada: la UI y la CLI traducen cada caso a un mensaje con un
 * {@code switch} exhaustivo, así que agregar un error obliga a manejarlo en todas partes.
 *
 * <p>Reglas (R6 del plan):
 * <ul>
 *   <li>Ningún componente lleva credenciales, tokens, claves, nombres de archivos
 *       de la bóveda ni rutas internas.</li>
 *   <li>{@link CredencialesIncorrectas} no dice qué factor falló.</li>
 * </ul>
 */
public sealed interface ErrorBoveda {

    /** Algún factor (o la combinación) no abre ningún slot. Nunca se indica cuál. */
    record CredencialesIncorrectas() implements ErrorBoveda {
    }

    /** Un MAC o tag de autenticación no coincide: el archivo fue alterado o está dañado. */
    record IntegridadFallida(Zona zonaMija) implements ErrorBoveda {

        public IntegridadFallida {
            requerirNoNulo(zonaMija, "zonaMija");
        }

        /** Parte del contenedor donde se detectó el fallo. */
        public enum Zona { HEADER, SLOT, MANIFEST, BLOQUE, FOOTER }
    }

    /** Versión de formato o identificador de algoritmo desconocido. */
    record FormatoNoSoportado(int versionFormatoMija) implements ErrorBoveda {
    }

    /** Un parámetro del header está fuera de los límites del parser (Fase 2.2). */
    record ParametrosFueraDeRango(String campoMija) implements ErrorBoveda {

        public ParametrosFueraDeRango {
            requerirNoNulo(campoMija, "campoMija");
        }
    }

    /** No hay espacio libre suficiente en el destino. */
    record EspacioInsuficiente(long bytesRequeridosMija, long bytesDisponiblesMija) implements ErrorBoveda {
    }

    /** El sistema de archivos del destino no admite el tamaño resultante (p. ej. FAT32). */
    record LimiteSistemaArchivos(String tipoSistemaMija, long limiteBytesMija) implements ErrorBoveda {

        public LimiteSistemaArchivos {
            requerirNoNulo(tipoSistemaMija, "tipoSistemaMija");
        }
    }

    /** Otra instancia tiene la bóveda abierta. */
    record BovedaEnUso() implements ErrorBoveda {
    }

    /** La bóveda fue autodestruida: sus slots ya no contienen la clave maestra. */
    record BovedaDestruida() implements ErrorBoveda {
    }

    /** El equipo no tiene memoria suficiente para el perfil de Argon2 de la bóveda. */
    record MemoriaInsuficiente(long bytesRequeridosMija, long bytesDisponiblesMija) implements ErrorBoveda {
    }

    /** El usuario canceló una operación larga. */
    record OperacionCancelada() implements ErrorBoveda {
    }

    /**
     * El token no tiene el formato esperado o su carácter de verificación no coincide.
     * Se detecta <b>antes</b> de derivar claves: es un error de tipeo, no de credenciales.
     */
    record TokenMalEscrito() implements ErrorBoveda {
    }

    /** La sesión se abrió con un token: solo se permite rotar credenciales o cerrar. */
    record RotacionPendiente() implements ErrorBoveda {
    }

    private static void requerirNoNulo(Object valorMija, String nombreMija) {
        if (valorMija == null) {
            throw new IllegalArgumentException(nombreMija + " no puede ser nulo");
        }
    }
}
