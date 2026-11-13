package dev.erick.cryptokipu.cli;

/** JSON mínimo para {@code --json} (sin dependencias). */
public final class Json {

    private Json() {
    }

    public static String texto(String valorMija) {
        StringBuilder salidaMija = new StringBuilder("\"");
        for (int indiceMija = 0; indiceMija < valorMija.length(); indiceMija++) {
            char caracterMija = valorMija.charAt(indiceMija);
            switch (caracterMija) {
                case '"' -> salidaMija.append("\\\"");
                case '\\' -> salidaMija.append("\\\\");
                case '\n' -> salidaMija.append("\\n");
                case '\r' -> salidaMija.append("\\r");
                case '\t' -> salidaMija.append("\\t");
                default -> {
                    if (caracterMija < 0x20) {
                        salidaMija.append(String.format("\\u%04x", (int) caracterMija));
                    } else {
                        salidaMija.append(caracterMija);
                    }
                }
            }
        }
        return salidaMija.append('"').toString();
    }
}
