package dev.erick.cryptokipu.infra.autenticacion;

import dev.erick.cryptokipu.core.auth.TokenRecuperacion;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Exporta los 6 tokens a un TXT. Los tokens se escriben desde {@code char[]} sin pasar por {@code String}.
 * Nunca sobrescribe un archivo existente.
 */
public final class ExportadorTokensTxt {

    public static final String AVISO_MIJA =
            "Guárdalo en un medio distinto al .kipu; este archivo NO está cifrado.";
    private static final DateTimeFormatter FECHA_MIJA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private ExportadorTokensTxt() {
    }

    public static void exportar(Path destinoMija, String nombreBovedaMija, LocalDateTime fechaMija,
            List<TokenRecuperacion> tokensMija) throws IOException {
        Objects.requireNonNull(nombreBovedaMija, "nombreBovedaMija");
        Objects.requireNonNull(fechaMija, "fechaMija");
        try (BufferedWriter escritorMija = Files.newBufferedWriter(destinoMija, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            escritorMija.write("CriptoKhipu — tokens de recuperación de un solo uso");
            escritorMija.newLine();
            escritorMija.write("Bóveda: " + nombreBovedaMija);
            escritorMija.newLine();
            escritorMija.write("Fecha: " + FECHA_MIJA.format(fechaMija));
            escritorMija.newLine();
            escritorMija.newLine();
            escritorMija.write(AVISO_MIJA);
            escritorMija.newLine();
            escritorMija.write("Cada token sirve una sola vez y siempre junto con tu contraseña numérica.");
            escritorMija.newLine();
            escritorMija.newLine();
            int numeroMija = 1;
            for (TokenRecuperacion tokenMija : tokensMija) {
                char[] formateadoMija = tokenMija.formatear();
                try {
                    escritorMija.write(numeroMija++ + ". ");
                    escritorMija.write(formateadoMija);
                    escritorMija.newLine();
                } finally {
                    Arrays.fill(formateadoMija, '\0');
                }
            }
        }
    }
}
