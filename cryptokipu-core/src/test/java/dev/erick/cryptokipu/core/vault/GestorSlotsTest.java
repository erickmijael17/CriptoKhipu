package dev.erick.cryptokipu.core.vault;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erick.cryptokipu.core.auth.Credenciales;
import dev.erick.cryptokipu.core.auth.TokenRecuperacion;
import dev.erick.cryptokipu.core.crypto.FuenteAleatoria;
import dev.erick.cryptokipu.core.crypto.ParametrosArgon2;
import dev.erick.cryptokipu.core.crypto.SecureBytes;
import dev.erick.cryptokipu.core.format.Slot;
import dev.erick.cryptokipu.core.format.TipoSlot;
import java.util.List;
import org.junit.jupiter.api.Test;

class GestorSlotsTest {

    /** Perfil mínimo permitido por el lector (64 MiB, t=2): rápido para pruebas. */
    static final ParametrosArgon2 RAPIDO_MIJA = new ParametrosArgon2(64 * 1024, 2, 1);
    private final GestorSlots gestorMija = new GestorSlots(FuenteAleatoria.delSistema());

    static Credenciales credenciales(String pinMija, String contrasenaMija, String fraseMija, byte[] huellaMija) {
        return new Credenciales(pinMija.toCharArray(), contrasenaMija.toCharArray(), fraseMija.toCharArray(),
                huellaMija == null ? null : huellaMija.clone());
    }

    @Test
    void creaUnSlotPrincipalYSeisTokensQueAbrenLaMismaClave() {
        try (Credenciales credencialesMija = credenciales("123456789012", "Clave Segura 2026", "mi perro se llama toby",
                null);
                GestorSlots.Creacion creacionMija = gestorMija.crear(credencialesMija, RAPIDO_MIJA, 0)) {
            List<Slot> slotsMija = creacionMija.headerMija().slotsMija();
            assertEquals(7, slotsMija.size());
            assertSame(TipoSlot.PRINCIPAL, slotsMija.getFirst().tipoMija());
            assertTrue(slotsMija.stream().skip(1).allMatch(slotMija -> slotMija.tipoMija() == TipoSlot.TOKEN));
            assertEquals(6, creacionMija.tokensMija().size());

            try (SecureBytes kekMija = gestorMija.kekPrincipal(creacionMija.headerMija().zonaAMija(), credencialesMija);
                    SecureBytes abiertaMija = GestorSlots.abrirPrincipal(creacionMija.headerMija(), kekMija).orElseThrow()) {
                assertArrayEquals(creacionMija.claveMaestraMija().bytes(), abiertaMija.bytes());
            }
            try (SecureBytes claveF1Mija = gestorMija.claveF1(creacionMija.headerMija().zonaAMija(),
                    "123456789012".toCharArray())) {
                for (int indiceMija = 0; indiceMija < 6; indiceMija++) {
                    TokenRecuperacion tokenMija = creacionMija.tokensMija().get(indiceMija);
                    GestorSlots.AperturaToken aperturaMija =
                            GestorSlots.abrirConToken(creacionMija.headerMija(), claveF1Mija, tokenMija).orElseThrow();
                    assertEquals(indiceMija + 1, aperturaMija.indiceMija());
                    assertArrayEquals(creacionMija.claveMaestraMija().bytes(), aperturaMija.claveMaestraMija().bytes());
                    aperturaMija.claveMaestraMija().close();
                }
            }
        }
    }

    @Test
    void laFraseSeNormalizaAntesDeDerivar() {
        try (Credenciales escritaMija = credenciales("123456789012", "Clave Segura 2026", "Mi Pérro  Ñato Corre",
                null);
                GestorSlots.Creacion creacionMija = gestorMija.crear(escritaMija, RAPIDO_MIJA, 0);
                Credenciales otraFormaMija = credenciales("123456789012", "Clave Segura 2026", "mi perro nato corre",
                        null);
                SecureBytes kekMija = gestorMija.kekPrincipal(creacionMija.headerMija().zonaAMija(), otraFormaMija)) {
            assertTrue(GestorSlots.abrirPrincipal(creacionMija.headerMija(), kekMija).isPresent());
        }
    }
}
