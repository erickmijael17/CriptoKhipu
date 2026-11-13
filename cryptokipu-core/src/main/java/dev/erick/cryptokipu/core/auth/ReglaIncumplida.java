package dev.erick.cryptokipu.core.auth;

/** Reglas de la política v1 que puede incumplir una credencial nueva. La UI muestra una línea por regla. */
public enum ReglaIncumplida {
    F1_SOLO_DIGITOS,
    F1_LONGITUD,
    F2_LONGITUD,
    F2_SOLO_ASCII,
    F2_FALTA_MAYUSCULA,
    F2_FALTA_MINUSCULA,
    F2_FALTA_DIGITO,
    F3_LONGITUD,
    /** La rotación necesita un factor que no se indicó (por ejemplo, tras usar un token). */
    FACTORES_INCOMPLETOS
}
