package dev.erick.cryptokipu.core.format;

/**
 * Constantes y límites del formato {@code .kipu} v1 (plan, Fase 2.2; docs/FORMAT_SPEC.md).
 * Todo valor fuera de estos rangos se rechaza antes de reservar memoria o derivar claves.
 */
public final class LimitesFormato {

    public static final byte[] MAGIC_HEADER_MIJA = {'C', 'K', 'I', 'P', 'U'};
    public static final byte[] MAGIC_FOOTER_MIJA = {'C', 'K', 'E', 'N', 'D'};

    public static final int VERSION_FORMATO_MIJA = 1;
    public static final int VERSION_POLITICA_MIJA = 1;
    public static final int NORMALIZACION_F3_MIJA = 1;
    public static final int ALG_AEAD_AES256GCM_MIJA = 1;
    public static final int ALG_KDF_ARGON2ID_MIJA = 1;
    public static final int ALG_HASH_SHA256_MIJA = 1;

    public static final int TAMANO_HEADER_MIJA = 16 * 1024;
    public static final long OFFSET_HEADER_PRIMARIO_MIJA = 0;
    public static final long OFFSET_HEADER_RESPALDO_MIJA = TAMANO_HEADER_MIJA;
    public static final long OFFSET_DATOS_MIJA = 2L * TAMANO_HEADER_MIJA;

    public static final int ARGON2_MEMORIA_MIN_KIB_MIJA = 64 * 1024;
    public static final int ARGON2_MEMORIA_MAX_KIB_MIJA = 2 * 1024 * 1024;
    public static final int ARGON2_ITERACIONES_MIN_MIJA = 2;
    public static final int ARGON2_ITERACIONES_MAX_MIJA = 10;
    public static final int ARGON2_PARALELISMO_MIN_MIJA = 1;
    public static final int ARGON2_PARALELISMO_MAX_MIJA = 8;

    public static final int SLOTS_MIN_MIJA = 1;
    public static final int SLOTS_MAX_MIJA = 7;
    public static final int TAMANO_BLOQUE_MIJA = 1 << 20;

    public static final int MANIFEST_MAX_BYTES_MIJA = 64 * 1024 * 1024;
    public static final int MANIFEST_MAX_ENTRADAS_MIJA = 1_000_000;
    public static final int RUTA_MAX_BYTES_MIJA = 4096;
    public static final int MIME_MAX_BYTES_MIJA = 255;

    public static final int LONGITUD_SAL_MIJA = 32;
    public static final int LONGITUD_VAULT_ID_MIJA = 16;
    public static final int LONGITUD_MAC_MIJA = 32;

    /** Tamaño máximo de un archivo en FAT/FAT32: 4 GiB − 1 B. */
    public static final long LIMITE_FAT32_MIJA = 4L * 1024 * 1024 * 1024 - 1;

    private LimitesFormato() {
    }
}
