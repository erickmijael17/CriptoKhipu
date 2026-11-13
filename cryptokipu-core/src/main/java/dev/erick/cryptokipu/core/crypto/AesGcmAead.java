package dev.erick.cryptokipu.core.crypto;

import java.security.GeneralSecurityException;
import java.util.Objects;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM (JCA), tag de 128 bits. La salida de {@link #cifrar} es {@code cifrado ‖ tag}.
 * Esta clase no genera nonces: los recibe de {@link ProveedorNonce}.
 */
public final class AesGcmAead {

    public static final int LONGITUD_CLAVE_MIJA = 32;
    public static final int LONGITUD_TAG_MIJA = 16;
    private static final String TRANSFORMACION_MIJA = "AES/GCM/NoPadding";

    private AesGcmAead() {
    }

    public static byte[] cifrar(SecureBytes claveMija, byte[] nonceMija, byte[] datosAsociadosMija, byte[] textoMija) {
        Objects.requireNonNull(textoMija, "textoMija");
        try {
            Cipher cifradorMija = iniciar(Cipher.ENCRYPT_MODE, claveMija, nonceMija, datosAsociadosMija);
            return cifradorMija.doFinal(textoMija);
        } catch (GeneralSecurityException errorMija) {
            throw new IllegalStateException("AES-GCM no disponible o mal configurado", errorMija);
        }
    }

    public static byte[] descifrar(SecureBytes claveMija, byte[] nonceMija, byte[] datosAsociadosMija, byte[] cifradoMija)
            throws FalloAutenticacion {
        Objects.requireNonNull(cifradoMija, "cifradoMija");
        if (cifradoMija.length < LONGITUD_TAG_MIJA) {
            throw new FalloAutenticacion();
        }
        try {
            Cipher cifradorMija = iniciar(Cipher.DECRYPT_MODE, claveMija, nonceMija, datosAsociadosMija);
            return cifradorMija.doFinal(cifradoMija);
        } catch (AEADBadTagException _) {
            throw new FalloAutenticacion();
        } catch (GeneralSecurityException errorMija) {
            throw new IllegalStateException("AES-GCM no disponible o mal configurado", errorMija);
        }
    }

    private static Cipher iniciar(int modoMija, SecureBytes claveMija, byte[] nonceMija, byte[] datosAsociadosMija)
            throws GeneralSecurityException {
        Objects.requireNonNull(claveMija, "claveMija");
        Objects.requireNonNull(nonceMija, "nonceMija");
        if (claveMija.longitud() != LONGITUD_CLAVE_MIJA) {
            throw new IllegalArgumentException("La clave debe medir 32 B");
        }
        if (nonceMija.length != ProveedorNonce.LONGITUD_NONCE_MIJA) {
            throw new IllegalArgumentException("El nonce debe medir 12 B");
        }
        Cipher cifradorMija = Cipher.getInstance(TRANSFORMACION_MIJA);
        cifradorMija.init(modoMija, new SecretKeySpec(claveMija.bytes(), "AES"),
                new GCMParameterSpec(LONGITUD_TAG_MIJA * 8, nonceMija));
        if (datosAsociadosMija != null && datosAsociadosMija.length > 0) {
            cifradorMija.updateAAD(datosAsociadosMija);
        }
        return cifradorMija;
    }
}
