package dev.erick.cryptokipu.ui;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/** Textos de la interfaz ({@code textos.properties}, español; {@code textos_en.properties}, inglés). */
public final class Textos {

    public static final String PAQUETE_MIJA = "dev.erick.cryptokipu.ui.textos";

    private static ResourceBundle paqueteMija = ResourceBundle.getBundle(PAQUETE_MIJA, Locale.of("es"));

    private Textos() {
    }

    public static void usarIdioma(String idiomaMija) {
        paqueteMija = ResourceBundle.getBundle(PAQUETE_MIJA, Locale.of(idiomaMija));
    }

    public static ResourceBundle paquete() {
        return paqueteMija;
    }

    /** Texto de la clave; si no existe, la propia clave (visible para corregirla, nunca un fallo). */
    public static String t(String claveMija, Object... argumentosMija) {
        try {
            String patronMija = paqueteMija.getString(claveMija);
            return argumentosMija.length == 0 ? patronMija : MessageFormat.format(patronMija, argumentosMija);
        } catch (MissingResourceException _) {
            return claveMija;
        }
    }
}
