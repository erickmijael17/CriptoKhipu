package dev.erick.cryptokipu.core.vault;

import dev.erick.cryptokipu.core.auth.AuthPolicy;
import dev.erick.cryptokipu.core.auth.CodificadorEntrada;
import dev.erick.cryptokipu.core.auth.CredentialNormalizer;
import dev.erick.cryptokipu.core.auth.Credenciales;
import dev.erick.cryptokipu.core.auth.TokenRecuperacion;
import dev.erick.cryptokipu.core.crypto.Argon2idKdf;
import dev.erick.cryptokipu.core.crypto.FuenteAleatoria;
import dev.erick.cryptokipu.core.crypto.HkdfDeriver;
import dev.erick.cryptokipu.core.crypto.KeyWrap;
import dev.erick.cryptokipu.core.crypto.ParametrosArgon2;
import dev.erick.cryptokipu.core.crypto.ProveedorNonce;
import dev.erick.cryptokipu.core.crypto.SecureBytes;
import dev.erick.cryptokipu.core.format.Header;
import dev.erick.cryptokipu.core.format.LimitesFormato;
import dev.erick.cryptokipu.core.format.MetadatosPrincipal;
import dev.erick.cryptokipu.core.format.Slot;
import dev.erick.cryptokipu.core.format.TipoSlot;
import dev.erick.cryptokipu.core.format.ZonaA;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Derivación de claves y construcción de slots (plan, Fases 3 y 4; ADR-003, ADR-004, ADR-011). Sin E/S.
 *
 * <pre>
 * Slot 0:     KEK₀  = Argon2id(len‖F1 ‖ len‖F2 ‖ len‖norm(F3), sal₀, secreto = SHA-256(F4), perfil)
 *             AAD   = Zona A ‖ 0 ‖ PRINCIPAL ‖ requiereLlave ‖ versiónPolítica ‖ umbral
 * Tokens:     K_F1  = Argon2id(len‖F1, salTokens, perfil)                 — una sola derivación
 *             KEK_i = HKDF-SHA256(K_F1 ‖ token_i, info = "ckp/v1/token-slot")
 *             AAD   = Zona A ‖ i ‖ TOKEN
 * </pre>
 */
public final class GestorSlots {

    public static final String INFO_TOKEN_MIJA = "ckp/v1/token-slot";
    public static final int INDICE_PRINCIPAL_MIJA = 0;

    private final FuenteAleatoria fuenteMija;
    private final KeyWrap envolturaMija;

    public GestorSlots(FuenteAleatoria fuenteMija) {
        this.fuenteMija = Objects.requireNonNull(fuenteMija, "fuenteMija");
        this.envolturaMija = new KeyWrap(new ProveedorNonce(fuenteMija));
    }

    /** Resultado de crear una bóveda. Cerrarlo borra la clave maestra y los tokens. */
    public record Creacion(Header headerMija, SecureBytes claveMaestraMija, List<TokenRecuperacion> tokensMija)
            implements AutoCloseable {
        @Override
        public void close() {
            claveMaestraMija.close();
            tokensMija.forEach(TokenRecuperacion::close);
        }

        @Override
        public String toString() {
            return "Creacion[***]";
        }
    }

    /** Resultado de abrir con un token: la clave maestra y la posición del slot usado. */
    public record AperturaToken(SecureBytes claveMaestraMija, int indiceMija) {
        @Override
        public String toString() {
            return "AperturaToken[indice=" + indiceMija + "]";
        }
    }

    /** Slots nuevos tras una rotación y, si se regeneraron, los 6 tokens nuevos (si no, lista vacía). */
    public record Rotacion(List<Slot> slotsMija, List<TokenRecuperacion> tokensNuevosMija) {
    }

    // ------------------------------------------------------------------ creación

    public Creacion crear(Credenciales credencialesMija, ParametrosArgon2 perfilMija, int umbralMija) {
        ZonaA zonaMija = ZonaA.nueva(perfilMija, fuenteMija);
        MetadatosPrincipal metadatosMija = new MetadatosPrincipal(credencialesMija.tieneLlave(), umbralMija);
        SecureBytes maestraMija = SecureBytes.envolver(fuenteMija.bytes(32));
        List<Slot> slotsMija = new ArrayList<>();
        slotsMija.add(slotPrincipal(zonaMija, metadatosMija, credencialesMija, maestraMija));
        List<TokenRecuperacion> tokensMija = new ArrayList<>();
        slotsMija.addAll(slotsDeToken(zonaMija, credencialesMija.pinMija(), maestraMija, tokensMija));
        Header headerMija = Header.nuevo(zonaMija, slotsMija, umbralMija, credencialesMija.tieneLlave());
        return new Creacion(headerMija, maestraMija, List.copyOf(tokensMija));
    }

    // ------------------------------------------------------------------ derivaciones

    /** KEK del slot principal. Siempre deriva: no hay salidas tempranas que dependan de los secretos. */
    public SecureBytes kekPrincipal(ZonaA zonaMija, Credenciales credencialesMija) {
        char[] fraseMija = CredentialNormalizer.normalizarFrase(credencialesMija.fraseMija());
        byte[] entradaMija = CodificadorEntrada.codificar(credencialesMija.pinMija(),
                credencialesMija.contrasenaMija(), fraseMija);
        try {
            return Argon2idKdf.derivar(entradaMija, zonaMija.salMija(), credencialesMija.huellaLlaveMija(),
                    zonaMija.argonMija());
        } finally {
            Arrays.fill(fraseMija, '\0');
            Arrays.fill(entradaMija, (byte) 0);
        }
    }

    /** {@code K_F1 = Argon2id(len‖F1, salTokens)}: una derivación sirve para probar los 6 slots de token. */
    public SecureBytes claveF1(ZonaA zonaMija, char[] pinMija) {
        byte[] entradaMija = CodificadorEntrada.codificar(pinMija);
        try {
            return Argon2idKdf.derivar(entradaMija, zonaMija.salTokensMija(), null, zonaMija.argonMija());
        } finally {
            Arrays.fill(entradaMija, (byte) 0);
        }
    }

    /** {@code KEK_i = HKDF(K_F1 ‖ token_i, info="ckp/v1/token-slot")}. */
    public static SecureBytes kekToken(SecureBytes claveF1Mija, TokenRecuperacion tokenMija) {
        byte[] tokenBytesMija = tokenMija.bytesAscii();
        byte[] materialMija = ByteBuffer.allocate(claveF1Mija.longitud() + tokenBytesMija.length)
                .put(claveF1Mija.bytes()).put(tokenBytesMija).array();
        try (SecureBytes ikmMija = SecureBytes.envolver(materialMija)) {
            return HkdfDeriver.derivar(ikmMija, null, INFO_TOKEN_MIJA.getBytes(StandardCharsets.UTF_8), 32);
        } finally {
            Arrays.fill(tokenBytesMija, (byte) 0);
        }
    }

    // ------------------------------------------------------------------ apertura

    /** Abre el slot principal; la AAD incluye los metadatos de ESTE header (requiereLlave, política, umbral). */
    public static Optional<SecureBytes> abrirPrincipal(Header headerMija, SecureBytes kekMija) {
        return headerMija.slotsMija().get(INDICE_PRINCIPAL_MIJA)
                .abrir(headerMija.zonaAMija(), INDICE_PRINCIPAL_MIJA, headerMija.metadatosPrincipal(), kekMija);
    }

    public static Optional<AperturaToken> abrirConToken(Header headerMija, SecureBytes claveF1Mija,
            TokenRecuperacion tokenMija) {
        try (SecureBytes kekMija = kekToken(claveF1Mija, tokenMija)) {
            List<Slot> slotsMija = headerMija.slotsMija();
            for (int indiceMija = 1; indiceMija < slotsMija.size(); indiceMija++) {
                Slot slotMija = slotsMija.get(indiceMija);
                if (slotMija.tipoMija() != TipoSlot.TOKEN) {
                    continue;
                }
                Optional<SecureBytes> maestraMija = slotMija.abrir(headerMija.zonaAMija(), indiceMija, null, kekMija);
                if (maestraMija.isPresent()) {
                    return Optional.of(new AperturaToken(maestraMija.get(), indiceMija));
                }
            }
            return Optional.empty();
        }
    }

    // ------------------------------------------------------------------ rotación y tokens

    /**
     * Slots tras cambiar credenciales o metadatos. El slot 0 siempre se rehace con los metadatos nuevos.
     * Si {@code regenerarTokensMija} (cambió F1), los 6 slots de token se rehacen con tokens nuevos; si no,
     * se conservan (sus KEK dependen solo de F1 y del token, y su AAD no incluye metadatos).
     */
    public Rotacion rotar(Header headerMija, SecureBytes claveMaestraMija, Credenciales nuevasMija, int umbralMija,
            boolean regenerarTokensMija) {
        ZonaA zonaMija = headerMija.zonaAMija();
        List<Slot> slotsMija = new ArrayList<>();
        slotsMija.add(slotPrincipal(zonaMija, new MetadatosPrincipal(nuevasMija.tieneLlave(), umbralMija),
                nuevasMija, claveMaestraMija));
        List<TokenRecuperacion> tokensMija = new ArrayList<>();
        if (regenerarTokensMija) {
            slotsMija.addAll(slotsDeToken(zonaMija, nuevasMija.pinMija(), claveMaestraMija, tokensMija));
        } else {
            slotsMija.addAll(headerMija.slotsMija().subList(1, headerMija.slotsMija().size()));
        }
        return new Rotacion(List.copyOf(slotsMija), List.copyOf(tokensMija));
    }

    /** Conserva el slot 0 y rehace los 6 slots de token con tokens nuevos. */
    public Rotacion regenerarTokens(Header headerMija, SecureBytes claveMaestraMija, char[] pinMija) {
        List<Slot> slotsMija = new ArrayList<>();
        slotsMija.add(headerMija.slotsMija().getFirst());
        List<TokenRecuperacion> tokensMija = new ArrayList<>();
        slotsMija.addAll(slotsDeToken(headerMija.zonaAMija(), pinMija, claveMaestraMija, tokensMija));
        return new Rotacion(List.copyOf(slotsMija), List.copyOf(tokensMija));
    }

    /** Slots con la posición {@code indiceMija} reemplazada por un slot vacío (token ya usado). */
    public List<Slot> conSlotVacio(Header headerMija, int indiceMija) {
        if (indiceMija == INDICE_PRINCIPAL_MIJA) {
            throw new IllegalArgumentException("El slot principal no se vacía");
        }
        List<Slot> slotsMija = new ArrayList<>(headerMija.slotsMija());
        slotsMija.set(indiceMija, Slot.vacio(fuenteMija));
        return List.copyOf(slotsMija);
    }

    private Slot slotPrincipal(ZonaA zonaMija, MetadatosPrincipal metadatosMija, Credenciales credencialesMija,
            SecureBytes maestraMija) {
        try (SecureBytes kekMija = kekPrincipal(zonaMija, credencialesMija)) {
            return Slot.envolver(zonaMija, INDICE_PRINCIPAL_MIJA, TipoSlot.PRINCIPAL, metadatosMija, relleno(),
                    kekMija, maestraMija, envolturaMija);
        }
    }

    private List<Slot> slotsDeToken(ZonaA zonaMija, char[] pinMija, SecureBytes maestraMija,
            List<TokenRecuperacion> tokensSalidaMija) {
        List<Slot> slotsMija = new ArrayList<>();
        try (SecureBytes claveF1Mija = claveF1(zonaMija, pinMija)) {
            for (int indiceMija = 1; indiceMija <= AuthPolicy.CANTIDAD_TOKENS_MIJA; indiceMija++) {
                TokenRecuperacion tokenMija = TokenRecuperacion.generar(fuenteMija);
                try (SecureBytes kekMija = kekToken(claveF1Mija, tokenMija)) {
                    slotsMija.add(Slot.envolver(zonaMija, indiceMija, TipoSlot.TOKEN, null, relleno(), kekMija,
                            maestraMija, envolturaMija));
                }
                tokensSalidaMija.add(tokenMija);
            }
        }
        return slotsMija;
    }

    /** La sal propia del slot no interviene en v1 (sal₀ y salTokens están en la Zona A): relleno aleatorio. */
    private byte[] relleno() {
        return fuenteMija.bytes(LimitesFormato.LONGITUD_SAL_MIJA);
    }
}
