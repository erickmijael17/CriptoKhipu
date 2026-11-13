package dev.erick.cryptokipu.core.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Objects;
import javax.crypto.KDF;
import javax.crypto.spec.HKDFParameterSpec;

/**
 * HKDF-SHA256 (RFC 5869) con la API estándar {@link KDF} del JDK 25 (JEP 510).
 * Deriva las subclaves de la clave maestra (plan, Fase 2.3).
 */
public final class HkdfDeriver {

    public static final String INFO_HEADER_MAC_MIJA = "ckp/v1/header-mac";
    public static final String INFO_MANIFEST_MIJA = "ckp/v1/manifest";
    public static final String INFO_ARCHIVO_MIJA = "ckp/v1/file";
    public static final int LONGITUD_FILE_ID_MIJA = 16;
    private static final String ALGORITMO_MIJA = "HKDF-SHA256";

    private HkdfDeriver() {
    }

    /** {@code K_hdr = HKDF(MK, info="ckp/v1/header-mac")}. */
    public static SecureBytes claveHeaderMac(SecureBytes claveMaestraMija) {
        return derivar(claveMaestraMija, null, utf8(INFO_HEADER_MAC_MIJA), AesGcmAead.LONGITUD_CLAVE_MIJA);
    }

    /** {@code K_manifest = HKDF(MK, info="ckp/v1/manifest")}. */
    public static SecureBytes claveManifest(SecureBytes claveMaestraMija) {
        return derivar(claveMaestraMija, null, utf8(INFO_MANIFEST_MIJA), AesGcmAead.LONGITUD_CLAVE_MIJA);
    }

    /** {@code K_file = HKDF(MK, salt=fileId, info="ckp/v1/file")}. */
    public static SecureBytes claveArchivo(SecureBytes claveMaestraMija, byte[] fileIdMija) {
        Objects.requireNonNull(fileIdMija, "fileIdMija");
        if (fileIdMija.length != LONGITUD_FILE_ID_MIJA) {
            throw new IllegalArgumentException("fileId debe medir 16 B");
        }
        return derivar(claveMaestraMija, fileIdMija, utf8(INFO_ARCHIVO_MIJA), AesGcmAead.LONGITUD_CLAVE_MIJA);
    }

    /** Extract-then-expand. {@code salMija} nula o vacía = sal de ceros (RFC 5869). */
    public static SecureBytes derivar(SecureBytes materialMija, byte[] salMija, byte[] infoMija, int longitudMija) {
        Objects.requireNonNull(materialMija, "materialMija");
        Objects.requireNonNull(infoMija, "infoMija");
        try {
            HKDFParameterSpec.Builder extraccionMija = HKDFParameterSpec.ofExtract().addIKM(materialMija.bytes());
            if (salMija != null && salMija.length > 0) {
                extraccionMija.addSalt(salMija);
            }
            KDF hkdfMija = KDF.getInstance(ALGORITMO_MIJA);
            return SecureBytes.envolver(hkdfMija.deriveData(extraccionMija.thenExpand(infoMija, longitudMija)));
        } catch (GeneralSecurityException errorMija) {
            throw new IllegalStateException("HKDF-SHA256 no disponible", errorMija);
        }
    }

    private static byte[] utf8(String textoMija) {
        return textoMija.getBytes(StandardCharsets.UTF_8);
    }
}
