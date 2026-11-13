package dev.erick.cryptokipu.core.auth;

import static dev.erick.cryptokipu.core.auth.AuthPolicy.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Valida credenciales <b>nuevas</b> (al crear o rotar) contra la política v1 y devuelve todas las
 * reglas incumplidas, para que la UI marque cada una. No se usa al desbloquear: ahí siempre se deriva,
 * sin salidas tempranas que dependan de los secretos.
 */
public final class ValidadorFactores {

    private ValidadorFactores() {
    }

    public static List<ReglaIncumplida> validar(char[] f1Mija, char[] f2Mija, char[] f3Mija) {
        Objects.requireNonNull(f1Mija, "f1Mija");
        Objects.requireNonNull(f2Mija, "f2Mija");
        Objects.requireNonNull(f3Mija, "f3Mija");
        List<ReglaIncumplida> reglasMija = new ArrayList<>();
        validarF1(f1Mija, reglasMija);
        validarF2(f2Mija, reglasMija);
        char[] normalizadaMija = CredentialNormalizer.normalizarFrase(f3Mija);
        try {
            int longitudMija = CredentialNormalizer.longitud(normalizadaMija);
            if (longitudMija < F3_LONGITUD_MIN_MIJA || longitudMija > F3_LONGITUD_MAX_MIJA) {
                reglasMija.add(ReglaIncumplida.F3_LONGITUD);
            }
        } finally {
            Arrays.fill(normalizadaMija, '\0');
        }
        return List.copyOf(reglasMija);
    }

    private static void validarF1(char[] f1Mija, List<ReglaIncumplida> reglasMija) {
        if (f1Mija.length < F1_LONGITUD_MIN_MIJA || f1Mija.length > F1_LONGITUD_MAX_MIJA) {
            reglasMija.add(ReglaIncumplida.F1_LONGITUD);
        }
        for (char caracterMija : f1Mija) {
            if (caracterMija < '0' || caracterMija > '9') {
                reglasMija.add(ReglaIncumplida.F1_SOLO_DIGITOS);
                return;
            }
        }
    }

    private static void validarF2(char[] f2Mija, List<ReglaIncumplida> reglasMija) {
        if (f2Mija.length < F2_LONGITUD_MIN_MIJA || f2Mija.length > F2_LONGITUD_MAX_MIJA) {
            reglasMija.add(ReglaIncumplida.F2_LONGITUD);
        }
        boolean asciiMija = true;
        boolean mayusculaMija = false;
        boolean minusculaMija = false;
        boolean digitoMija = false;
        for (char caracterMija : f2Mija) {
            asciiMija &= caracterMija >= F2_ASCII_MIN_MIJA && caracterMija <= F2_ASCII_MAX_MIJA;
            mayusculaMija |= caracterMija >= 'A' && caracterMija <= 'Z';
            minusculaMija |= caracterMija >= 'a' && caracterMija <= 'z';
            digitoMija |= caracterMija >= '0' && caracterMija <= '9';
        }
        if (!asciiMija) {
            reglasMija.add(ReglaIncumplida.F2_SOLO_ASCII);
        }
        if (!mayusculaMija) {
            reglasMija.add(ReglaIncumplida.F2_FALTA_MAYUSCULA);
        }
        if (!minusculaMija) {
            reglasMija.add(ReglaIncumplida.F2_FALTA_MINUSCULA);
        }
        if (!digitoMija) {
            reglasMija.add(ReglaIncumplida.F2_FALTA_DIGITO);
        }
    }
}
