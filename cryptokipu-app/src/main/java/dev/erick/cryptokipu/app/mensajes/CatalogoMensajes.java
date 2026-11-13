package dev.erick.cryptokipu.app.mensajes;

import dev.erick.cryptokipu.core.auth.ReglaIncumplida;
import dev.erick.cryptokipu.core.error.ErrorBoveda;

/**
 * Catálogo único de mensajes para el usuario. Lo usan la CLI y la UI, así que ambas dicen lo mismo.
 * Los {@code switch} son exhaustivos: un error o regla nueva no compila hasta tener su mensaje.
 * Ningún mensaje incluye datos de la bóveda ni dice qué factor falló.
 */
public final class CatalogoMensajes {

    private CatalogoMensajes() {
    }

    public static String mensaje(ErrorBoveda errorMija) {
        return switch (errorMija) {
            case ErrorBoveda.CredencialesIncorrectas _ -> "Credenciales incorrectas.";
            case ErrorBoveda.IntegridadFallida _ ->
                    "La bóveda está dañada o fue modificada: la verificación de integridad falló.";
            case ErrorBoveda.FormatoNoSoportado _ -> "El archivo no es una bóveda compatible con esta versión.";
            case ErrorBoveda.ParametrosFueraDeRango _ -> "El archivo no es una bóveda válida: parámetros fuera de rango.";
            case ErrorBoveda.EspacioInsuficiente espacioMija -> "No hay espacio suficiente: se necesitan "
                    + megas(espacioMija.bytesRequeridosMija()) + " y hay " + megas(espacioMija.bytesDisponiblesMija())
                    + " libres.";
            case ErrorBoveda.LimiteSistemaArchivos limiteMija -> "El destino (" + limiteMija.tipoSistemaMija()
                    + ") no admite archivos de más de 4 GiB. Formatea el USB en exFAT o NTFS.";
            case ErrorBoveda.BovedaEnUso _ -> "La bóveda ya está abierta en otro programa o ventana.";
            case ErrorBoveda.BovedaDestruida _ -> "Esta bóveda fue destruida tras demasiados intentos fallidos.";
            case ErrorBoveda.MemoriaInsuficiente memoriaMija -> "Este equipo no tiene memoria suficiente: la bóveda pide "
                    + megas(memoriaMija.bytesRequeridosMija()) + ".";
            case ErrorBoveda.OperacionCancelada _ -> "Operación cancelada. La bóveda quedó como estaba.";
            case ErrorBoveda.TokenMalEscrito _ -> "Token mal escrito: revisa cada carácter (no se intentó abrir la bóveda).";
            case ErrorBoveda.RotacionPendiente _ ->
                    "Abriste la bóveda con un token: primero define credenciales nuevas.";
        };
    }

    public static String mensaje(ReglaIncumplida reglaMija) {
        return switch (reglaMija) {
            case F1_SOLO_DIGITOS -> "La contraseña numérica solo admite dígitos (0-9).";
            case F1_LONGITUD -> "La contraseña numérica debe tener entre 12 y 25 dígitos.";
            case F2_LONGITUD -> "La contraseña alfanumérica debe tener entre 12 y 25 caracteres.";
            case F2_SOLO_ASCII -> "La contraseña alfanumérica no admite ñ ni tildes (debe poder escribirse en cualquier teclado).";
            case F2_FALTA_MAYUSCULA -> "La contraseña alfanumérica necesita al menos una mayúscula.";
            case F2_FALTA_MINUSCULA -> "La contraseña alfanumérica necesita al menos una minúscula.";
            case F2_FALTA_DIGITO -> "La contraseña alfanumérica necesita al menos un número.";
            case F3_LONGITUD -> "La frase debe tener entre 15 y 25 caracteres (sin contar tildes ni espacios repetidos).";
            case FACTORES_INCOMPLETOS -> "Faltan factores: indica todas las credenciales necesarias.";
        };
    }

    private static String megas(long bytesMija) {
        return (bytesMija / (1024 * 1024)) + " MiB";
    }
}
