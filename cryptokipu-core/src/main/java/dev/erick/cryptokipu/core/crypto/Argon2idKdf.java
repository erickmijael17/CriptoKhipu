package dev.erick.cryptokipu.core.crypto;

import java.util.Objects;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

/**
 * Argon2id (RFC 9106, versión 0x13) con Bouncy Castle.
 *
 * <p>Uso en CryptoKipu: {@code entrada = len‖F1 ‖ len‖F2 ‖ len‖F3}, {@code secreto = SHA-256(F4)}.
 */
public final class Argon2idKdf {

    public static final int LONGITUD_SALIDA_MIJA = 32;

    private Argon2idKdf() {
    }

    /** Deriva 32 B. {@code secretoMija} puede ser {@code null} (slots de token). */
    public static SecureBytes derivar(byte[] entradaMija, byte[] salMija, byte[] secretoMija,
            ParametrosArgon2 parametrosMija) {
        return SecureBytes.envolver(derivar(entradaMija, salMija, secretoMija, null, parametrosMija,
                LONGITUD_SALIDA_MIJA));
    }

    /** Versión completa, con datos asociados y longitud libre. Usada por los vectores de prueba. */
    static byte[] derivar(byte[] entradaMija, byte[] salMija, byte[] secretoMija, byte[] datosAsociadosMija,
            ParametrosArgon2 parametrosMija, int longitudMija) {
        Objects.requireNonNull(entradaMija, "entradaMija");
        Objects.requireNonNull(salMija, "salMija");
        Objects.requireNonNull(parametrosMija, "parametrosMija");
        if (salMija.length < 8) {
            throw new IllegalArgumentException("La sal debe medir al menos 8 B");
        }
        Argon2Parameters.Builder constructorMija = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withMemoryAsKB(parametrosMija.memoriaKibMija())
                .withIterations(parametrosMija.iteracionesMija())
                .withParallelism(parametrosMija.paralelismoMija())
                .withSalt(salMija);
        if (secretoMija != null) {
            constructorMija.withSecret(secretoMija);
        }
        if (datosAsociadosMija != null) {
            constructorMija.withAdditional(datosAsociadosMija);
        }
        Argon2Parameters argonMija = constructorMija.build();
        try {
            Argon2BytesGenerator generadorMija = new Argon2BytesGenerator();
            generadorMija.init(argonMija);
            byte[] salidaMija = new byte[longitudMija];
            generadorMija.generateBytes(entradaMija, salidaMija);
            return salidaMija;
        } finally {
            argonMija.clear();
        }
    }
}
