package dev.erick.cryptokipu.core.format;

import static dev.erick.cryptokipu.core.format.LimitesFormato.*;

import dev.erick.cryptokipu.core.crypto.FuenteAleatoria;
import dev.erick.cryptokipu.core.crypto.ParametrosArgon2;
import dev.erick.cryptokipu.core.error.ErrorBoveda;
import dev.erick.cryptokipu.core.error.ExcepcionBoveda;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

/**
 * Zona A del header: inmutable desde la creación y autenticada como AAD de cada slot.
 * Si alguien cambia los parámetros de Argon2, las sales o la política, ningún slot abre.
 *
 * @param argonMija      perfil de Argon2id de la bóveda
 * @param salMija        sal₀ del slot principal
 * @param salTokensMija  sal de {@code K_F1} para los slots de token (ADR-011)
 * @param vaultIdMija    identificador aleatorio de la bóveda
 */
public record ZonaA(ParametrosArgon2 argonMija, byte[] salMija, byte[] salTokensMija, byte[] vaultIdMija) {

    /**
     * magic(5) versión(2) aead(1) kdf(1) hash(1) política(2) normF3(1) m(4) t(1) p(1) sal₀(32)
     * salTokens(32) vaultId(16) bloque(4).
     */
    public static final int TAMANO_MIJA = 5 + 2 + 1 + 1 + 1 + 2 + 1 + 4 + 1 + 1 + LONGITUD_SAL_MIJA
            + LONGITUD_SAL_MIJA + LONGITUD_VAULT_ID_MIJA + 4;

    public ZonaA {
        Objects.requireNonNull(argonMija, "argonMija");
        salMija = copiarLongitud(salMija, LONGITUD_SAL_MIJA, "salMija");
        salTokensMija = copiarLongitud(salTokensMija, LONGITUD_SAL_MIJA, "salTokensMija");
        vaultIdMija = copiarLongitud(vaultIdMija, LONGITUD_VAULT_ID_MIJA, "vaultIdMija");
        validarArgon(argonMija.memoriaKibMija(), argonMija.iteracionesMija(), argonMija.paralelismoMija());
    }

    public static ZonaA nueva(ParametrosArgon2 argonMija, FuenteAleatoria fuenteMija) {
        return new ZonaA(argonMija, fuenteMija.bytes(LONGITUD_SAL_MIJA), fuenteMija.bytes(LONGITUD_SAL_MIJA),
                fuenteMija.bytes(LONGITUD_VAULT_ID_MIJA));
    }

    @Override
    public byte[] salMija() {
        return salMija.clone();
    }

    @Override
    public byte[] salTokensMija() {
        return salTokensMija.clone();
    }

    @Override
    public byte[] vaultIdMija() {
        return vaultIdMija.clone();
    }

    public byte[] codificar() {
        return ByteBuffer.allocate(TAMANO_MIJA)
                .put(MAGIC_HEADER_MIJA)
                .putShort((short) VERSION_FORMATO_MIJA)
                .put((byte) ALG_AEAD_AES256GCM_MIJA)
                .put((byte) ALG_KDF_ARGON2ID_MIJA)
                .put((byte) ALG_HASH_SHA256_MIJA)
                .putShort((short) VERSION_POLITICA_MIJA)
                .put((byte) NORMALIZACION_F3_MIJA)
                .putInt(argonMija.memoriaKibMija())
                .put((byte) argonMija.iteracionesMija())
                .put((byte) argonMija.paralelismoMija())
                .put(salMija)
                .put(salTokensMija)
                .put(vaultIdMija)
                .putInt(TAMANO_BLOQUE_MIJA)
                .array();
    }

    /** Lee y valida la zona A. Lanza {@link ExcepcionBoveda} ante formato desconocido o límites violados. */
    public static ZonaA decodificar(ByteBuffer bufferMija) {
        byte[] magicMija = new byte[MAGIC_HEADER_MIJA.length];
        bufferMija.get(magicMija);
        if (!Arrays.equals(magicMija, MAGIC_HEADER_MIJA)) {
            throw new ExcepcionBoveda(new ErrorBoveda.IntegridadFallida(ErrorBoveda.IntegridadFallida.Zona.HEADER));
        }
        int versionMija = Short.toUnsignedInt(bufferMija.getShort());
        exigir(versionMija == VERSION_FORMATO_MIJA, versionMija);
        int aeadMija = Byte.toUnsignedInt(bufferMija.get());
        int kdfMija = Byte.toUnsignedInt(bufferMija.get());
        int hashMija = Byte.toUnsignedInt(bufferMija.get());
        exigir(aeadMija == ALG_AEAD_AES256GCM_MIJA, aeadMija);
        exigir(kdfMija == ALG_KDF_ARGON2ID_MIJA, kdfMija);
        exigir(hashMija == ALG_HASH_SHA256_MIJA, hashMija);
        int politicaMija = Short.toUnsignedInt(bufferMija.getShort());
        exigir(politicaMija == VERSION_POLITICA_MIJA, politicaMija);
        int normalizacionMija = Byte.toUnsignedInt(bufferMija.get());
        exigir(normalizacionMija == NORMALIZACION_F3_MIJA, normalizacionMija);
        long memoriaMija = Integer.toUnsignedLong(bufferMija.getInt());
        int iteracionesMija = Byte.toUnsignedInt(bufferMija.get());
        int paralelismoMija = Byte.toUnsignedInt(bufferMija.get());
        if (memoriaMija > ARGON2_MEMORIA_MAX_KIB_MIJA) {
            throw fueraDeRango("argon2.m");
        }
        validarArgon((int) memoriaMija, iteracionesMija, paralelismoMija);
        byte[] salMija = new byte[LONGITUD_SAL_MIJA];
        bufferMija.get(salMija);
        byte[] salTokensMija = new byte[LONGITUD_SAL_MIJA];
        bufferMija.get(salTokensMija);
        byte[] vaultIdMija = new byte[LONGITUD_VAULT_ID_MIJA];
        bufferMija.get(vaultIdMija);
        int bloqueMija = bufferMija.getInt();
        if (bloqueMija != TAMANO_BLOQUE_MIJA) {
            throw fueraDeRango("tamanoBloque");
        }
        return new ZonaA(new ParametrosArgon2((int) memoriaMija, iteracionesMija, paralelismoMija), salMija,
                salTokensMija, vaultIdMija);
    }

    @Override
    public boolean equals(Object otroMija) {
        return otroMija instanceof ZonaA otraMija && argonMija.equals(otraMija.argonMija)
                && Arrays.equals(salMija, otraMija.salMija) && Arrays.equals(salTokensMija, otraMija.salTokensMija)
                && Arrays.equals(vaultIdMija, otraMija.vaultIdMija);
    }

    @Override
    public int hashCode() {
        return Objects.hash(argonMija, Arrays.hashCode(salMija), Arrays.hashCode(salTokensMija),
                Arrays.hashCode(vaultIdMija));
    }

    @Override
    public String toString() {
        return "ZonaA[" + argonMija + "]";
    }

    private static void validarArgon(int memoriaKibMija, int iteracionesMija, int paralelismoMija) {
        if (memoriaKibMija < ARGON2_MEMORIA_MIN_KIB_MIJA || memoriaKibMija > ARGON2_MEMORIA_MAX_KIB_MIJA) {
            throw fueraDeRango("argon2.m");
        }
        if (iteracionesMija < ARGON2_ITERACIONES_MIN_MIJA || iteracionesMija > ARGON2_ITERACIONES_MAX_MIJA) {
            throw fueraDeRango("argon2.t");
        }
        if (paralelismoMija < ARGON2_PARALELISMO_MIN_MIJA || paralelismoMija > ARGON2_PARALELISMO_MAX_MIJA) {
            throw fueraDeRango("argon2.p");
        }
    }

    private static void exigir(boolean condicionMija, int valorMija) {
        if (!condicionMija) {
            throw new ExcepcionBoveda(new ErrorBoveda.FormatoNoSoportado(valorMija));
        }
    }

    private static ExcepcionBoveda fueraDeRango(String campoMija) {
        return new ExcepcionBoveda(new ErrorBoveda.ParametrosFueraDeRango(campoMija));
    }

    private static byte[] copiarLongitud(byte[] datosMija, int longitudMija, String nombreMija) {
        Objects.requireNonNull(datosMija, nombreMija);
        if (datosMija.length != longitudMija) {
            throw new IllegalArgumentException(nombreMija + " debe medir " + longitudMija + " B");
        }
        return datosMija.clone();
    }
}
