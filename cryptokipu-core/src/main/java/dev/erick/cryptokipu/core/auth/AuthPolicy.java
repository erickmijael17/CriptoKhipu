package dev.erick.cryptokipu.core.auth;

/**
 * Política de autenticación v1 (plan, sección 2; ADR-009 y ADR-011). Regla R7: cualquier cambio en
 * estas constantes exige una política v2, porque las bóvedas existentes dejarían de abrir.
 */
public final class AuthPolicy {

    public static final int VERSION_MIJA = 1;

    public static final int F1_LONGITUD_MIN_MIJA = 12;
    public static final int F1_LONGITUD_MAX_MIJA = 25;

    public static final int F2_LONGITUD_MIN_MIJA = 12;
    public static final int F2_LONGITUD_MAX_MIJA = 25;
    public static final char F2_ASCII_MIN_MIJA = 0x20;
    public static final char F2_ASCII_MAX_MIJA = 0x7E;

    /** F3 se mide en puntos de código, después de normalizar. */
    public static final int F3_LONGITUD_MIN_MIJA = 15;
    public static final int F3_LONGITUD_MAX_MIJA = 25;

    public static final int CANTIDAD_TOKENS_MIJA = 6;
    public static final int LONGITUD_LLAVE_GENERADA_MIJA = 64;

    private AuthPolicy() {
    }
}
