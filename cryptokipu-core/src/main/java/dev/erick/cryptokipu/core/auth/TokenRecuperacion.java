package dev.erick.cryptokipu.core.auth;

import dev.erick.cryptokipu.core.crypto.FuenteAleatoria;
import dev.erick.cryptokipu.core.error.ErrorBoveda;
import dev.erick.cryptokipu.core.error.ExcepcionBoveda;
import java.util.Arrays;
import java.util.Objects;

/**
 * Token de recuperación de un solo uso (ADR-011).
 *
 * <p>Formato: {@code CKP-XXXXX-XXXXX-XXXXX-XXXXX-XXXXX-C}: 25 símbolos Base32 Crockford (125 bits
 * aleatorios) y un símbolo de verificación (valor mod 37, alfabeto Crockford ampliado {@code *~$=U}).
 * La lectura tolera minúsculas, guiones, espacios, el prefijo opcional y las confusiones
 * {@code O→0}, {@code I/L→1}. Un token mal escrito se rechaza con {@code TokenMalEscrito} <b>antes</b>
 * de derivar ninguna clave.
 *
 * @param simbolosMija los 25 símbolos canónicos (sin prefijo, guiones ni verificación). Se borran al cerrar.
 */
public record TokenRecuperacion(char[] simbolosMija) implements AutoCloseable {

    public static final int LONGITUD_MIJA = 25;
    private static final String ALFABETO_MIJA = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final String ALFABETO_VERIFICACION_MIJA = ALFABETO_MIJA + "*~$=U";
    private static final String PREFIJO_MIJA = "CKP";

    public TokenRecuperacion {
        Objects.requireNonNull(simbolosMija, "simbolosMija");
        if (simbolosMija.length != LONGITUD_MIJA) {
            throw new IllegalArgumentException("El token debe tener 25 símbolos");
        }
        for (char simboloMija : simbolosMija) {
            if (ALFABETO_MIJA.indexOf(simboloMija) < 0) {
                throw new IllegalArgumentException("Símbolo fuera del alfabeto");
            }
        }
    }

    /** 125 bits aleatorios. */
    public static TokenRecuperacion generar(FuenteAleatoria fuenteMija) {
        byte[] aleatorioMija = fuenteMija.bytes(16);
        char[] simbolosMija = new char[LONGITUD_MIJA];
        try {
            for (int indiceMija = 0; indiceMija < LONGITUD_MIJA; indiceMija++) {
                int valorMija = 0;
                for (int bitMija = 0; bitMija < 5; bitMija++) {
                    int posicionMija = indiceMija * 5 + bitMija;
                    int byteMija = aleatorioMija[posicionMija / 8] & 0xFF;
                    valorMija = (valorMija << 1) | ((byteMija >> (7 - posicionMija % 8)) & 1);
                }
                simbolosMija[indiceMija] = ALFABETO_MIJA.charAt(valorMija);
            }
            return new TokenRecuperacion(simbolosMija);
        } finally {
            Arrays.fill(aleatorioMija, (byte) 0);
        }
    }

    /**
     * Lee un token escrito por el usuario. No modifica {@code entradaMija}.
     *
     * @throws ExcepcionBoveda con {@code TokenMalEscrito} si el formato o la verificación fallan
     */
    public static TokenRecuperacion leer(char[] entradaMija) {
        Objects.requireNonNull(entradaMija, "entradaMija");
        char[] limpioMija = new char[entradaMija.length];
        int longitudMija = 0;
        try {
            for (char caracterMija : entradaMija) {
                if (caracterMija == '-' || Character.isWhitespace(caracterMija)) {
                    continue;
                }
                limpioMija[longitudMija++] = canonico(caracterMija);
            }
            int inicioMija = 0;
            if (longitudMija == LONGITUD_MIJA + 1 + PREFIJO_MIJA.length() && empiezaConPrefijo(limpioMija)) {
                inicioMija = PREFIJO_MIJA.length();
            }
            if (longitudMija - inicioMija != LONGITUD_MIJA + 1) {
                throw malEscrito();
            }
            char[] simbolosMija = Arrays.copyOfRange(limpioMija, inicioMija, inicioMija + LONGITUD_MIJA);
            char verificacionMija = limpioMija[inicioMija + LONGITUD_MIJA];
            for (char simboloMija : simbolosMija) {
                if (ALFABETO_MIJA.indexOf(simboloMija) < 0) {
                    Arrays.fill(simbolosMija, '\0');
                    throw malEscrito();
                }
            }
            if (verificacion(simbolosMija) != verificacionMija) {
                Arrays.fill(simbolosMija, '\0');
                throw malEscrito();
            }
            return new TokenRecuperacion(simbolosMija);
        } finally {
            Arrays.fill(limpioMija, '\0');
        }
    }

    /** {@code CKP-XXXXX-XXXXX-XXXXX-XXXXX-XXXXX-C}. Quien llama borra el resultado. */
    public char[] formatear() {
        char[] salidaMija = new char[PREFIJO_MIJA.length() + 1 + LONGITUD_MIJA + 5 + 1];
        int posicionMija = 0;
        for (char letraMija : PREFIJO_MIJA.toCharArray()) {
            salidaMija[posicionMija++] = letraMija;
        }
        for (int indiceMija = 0; indiceMija < LONGITUD_MIJA; indiceMija++) {
            if (indiceMija % 5 == 0) {
                salidaMija[posicionMija++] = '-';
            }
            salidaMija[posicionMija++] = simbolosMija[indiceMija];
        }
        salidaMija[posicionMija++] = '-';
        salidaMija[posicionMija] = verificacion(simbolosMija);
        return salidaMija;
    }

    /** Bytes ASCII de los 25 símbolos (entrada de la HKDF del slot). Quien llama los borra. */
    public byte[] bytesAscii() {
        byte[] salidaMija = new byte[LONGITUD_MIJA];
        for (int indiceMija = 0; indiceMija < LONGITUD_MIJA; indiceMija++) {
            salidaMija[indiceMija] = (byte) simbolosMija[indiceMija];
        }
        return salidaMija;
    }

    @Override
    public void close() {
        Arrays.fill(simbolosMija, '\0');
    }

    @Override
    public boolean equals(Object otroMija) {
        return otroMija instanceof TokenRecuperacion otroTokenMija
                && Arrays.equals(simbolosMija, otroTokenMija.simbolosMija);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(simbolosMija);
    }

    @Override
    public String toString() {
        return "***";
    }

    /** Valor de los 125 bits mod 37, calculado símbolo a símbolo (sin BigInteger). */
    static char verificacion(char[] simbolosMija) {
        int restoMija = 0;
        for (char simboloMija : simbolosMija) {
            restoMija = (restoMija * 32 + ALFABETO_MIJA.indexOf(simboloMija)) % 37;
        }
        return ALFABETO_VERIFICACION_MIJA.charAt(restoMija);
    }

    private static char canonico(char caracterMija) {
        char mayusculaMija = Character.toUpperCase(caracterMija);
        return switch (mayusculaMija) {
            case 'O' -> '0';
            case 'I', 'L' -> '1';
            default -> mayusculaMija;
        };
    }

    private static boolean empiezaConPrefijo(char[] limpioMija) {
        return limpioMija[0] == 'C' && limpioMija[1] == 'K' && limpioMija[2] == 'P';
    }

    private static ExcepcionBoveda malEscrito() {
        return new ExcepcionBoveda(new ErrorBoveda.TokenMalEscrito());
    }
}
