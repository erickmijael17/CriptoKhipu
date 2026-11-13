package dev.erick.cryptokipu.ui.viewmodel;

import dev.erick.cryptokipu.app.mensajes.CatalogoMensajes;
import dev.erick.cryptokipu.core.auth.CredentialNormalizer;
import dev.erick.cryptokipu.core.auth.ReglaIncumplida;
import dev.erick.cryptokipu.core.auth.ValidadorFactores;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Validación en vivo de UN factor nuevo (al crear o rotar; nunca al desbloquear): lista ✓/✗ por regla de la
 * política v1 y un medidor de fortaleza 0..1. Los demás factores se rellenan con valores válidos para aislar las
 * reglas del factor evaluado.
 */
public final class ValidacionViva {

    /** Factor que se valida. */
    public enum Factor { F1, F2, F3 }

    /** Una línea de la lista: el texto de la regla y si se cumple. */
    public record EstadoRegla(String textoMija, boolean cumplidaMija) {
    }

    private static final char[] F1_VALIDO_MIJA = "123456789012".toCharArray();
    private static final char[] F2_VALIDO_MIJA = "Aa1aaaaaaaaa".toCharArray();
    private static final char[] F3_VALIDO_MIJA = "aaaaaaaaaaaaaaa".toCharArray();

    private final Factor factorMija;
    private final ObservableList<EstadoRegla> reglasMija = FXCollections.observableArrayList();
    private final DoubleProperty fortalezaMija = new SimpleDoubleProperty();

    public ValidacionViva(Factor factorMija) {
        this.factorMija = factorMija;
        evaluar(new char[0]);
    }

    /** Reevalúa con el valor actual del campo (no lo guarda). */
    public void evaluar(char[] valorMija) {
        List<ReglaIncumplida> incumplidasMija = switch (factorMija) {
            case F1 -> ValidadorFactores.validar(valorMija, F2_VALIDO_MIJA, F3_VALIDO_MIJA);
            case F2 -> ValidadorFactores.validar(F1_VALIDO_MIJA, valorMija, F3_VALIDO_MIJA);
            case F3 -> ValidadorFactores.validar(F1_VALIDO_MIJA, F2_VALIDO_MIJA, valorMija);
        };
        reglasMija.setAll(reglasDelFactor().stream()
                .map(reglaMija -> new EstadoRegla(CatalogoMensajes.mensaje(reglaMija), !incumplidasMija.contains(reglaMija)))
                .toList());
        fortalezaMija.set(fortaleza(valorMija));
    }

    public boolean esValido() {
        return reglasMija.stream().allMatch(EstadoRegla::cumplidaMija);
    }

    public ObservableList<EstadoRegla> reglas() {
        return reglasMija;
    }

    public DoubleProperty fortalezaProperty() {
        return fortalezaMija;
    }

    private Set<ReglaIncumplida> reglasDelFactor() {
        return switch (factorMija) {
            case F1 -> EnumSet.of(ReglaIncumplida.F1_LONGITUD, ReglaIncumplida.F1_SOLO_DIGITOS);
            case F2 -> EnumSet.of(ReglaIncumplida.F2_LONGITUD, ReglaIncumplida.F2_SOLO_ASCII,
                    ReglaIncumplida.F2_FALTA_MAYUSCULA, ReglaIncumplida.F2_FALTA_MINUSCULA, ReglaIncumplida.F2_FALTA_DIGITO);
            case F3 -> EnumSet.of(ReglaIncumplida.F3_LONGITUD);
        };
    }

    /** Estimación simple: bits = longitud × log2(tamaño del alfabeto usado); 100 bits = 1. */
    private double fortaleza(char[] valorMija) {
        char[] evaluadoMija = factorMija == Factor.F3 ? CredentialNormalizer.normalizarFrase(valorMija) : valorMija;
        try {
            boolean digitosMija = false;
            boolean minusculasMija = false;
            boolean mayusculasMija = false;
            boolean otrosMija = false;
            for (char caracterMija : evaluadoMija) {
                digitosMija |= Character.isDigit(caracterMija);
                minusculasMija |= Character.isLowerCase(caracterMija);
                mayusculasMija |= Character.isUpperCase(caracterMija);
                otrosMija |= !Character.isLetterOrDigit(caracterMija);
            }
            int alfabetoMija = (digitosMija ? 10 : 0) + (minusculasMija ? 26 : 0) + (mayusculasMija ? 26 : 0)
                    + (otrosMija ? 33 : 0);
            if (alfabetoMija == 0) {
                return 0;
            }
            double bitsMija = evaluadoMija.length * (Math.log(alfabetoMija) / Math.log(2));
            return Math.min(1.0, bitsMija / 100.0);
        } finally {
            if (evaluadoMija != valorMija) {
                Arrays.fill(evaluadoMija, '\0');
            }
        }
    }
}
