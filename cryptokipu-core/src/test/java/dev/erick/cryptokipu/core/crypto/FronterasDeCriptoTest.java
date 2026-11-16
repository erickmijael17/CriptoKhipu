package dev.erick.cryptokipu.core.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/** Fronteras exactas de la criptografía y del pool de memoria (mutantes de PIT, Fase 9). */
class FronterasDeCriptoTest {

    private static final byte[] FILE_ID_MIJA = new byte[16];

    private static SecureBytes clave() {
        byte[] materialMija = new byte[32];
        Arrays.fill(materialMija, (byte) 3);
        return SecureBytes.envolver(materialMija);
    }

    // ------------------------------------------------------------------ ChunkCipher

    private static byte[] cifrar(SecureBytes claveMija, byte[] textoMija, long[] bloquesMija) throws IOException {
        ByteArrayOutputStream salidaMija = new ByteArrayOutputStream();
        bloquesMija[0] = ChunkCipher.cifrar(claveMija, FILE_ID_MIJA, new ByteArrayInputStream(textoMija), salidaMija);
        return salidaMija.toByteArray();
    }

    @Test
    void cifrarYDescifrarInformanElNumeroExactoDeBloques() throws IOException {
        int[][] casosMija = {{0, 1}, {1, 1}, {ChunkCipher.TAMANO_BLOQUE_MIJA - 1, 1}, {ChunkCipher.TAMANO_BLOQUE_MIJA, 1},
            {ChunkCipher.TAMANO_BLOQUE_MIJA + 1, 2}, {2 * ChunkCipher.TAMANO_BLOQUE_MIJA, 2},
            {2 * ChunkCipher.TAMANO_BLOQUE_MIJA + 5, 3}};
        try (SecureBytes claveMija = clave()) {
            for (int[] casoMija : casosMija) {
                byte[] textoMija = new byte[casoMija[0]];
                for (int indiceMija = 0; indiceMija < textoMija.length; indiceMija += 4093) {
                    textoMija[indiceMija] = (byte) (indiceMija / 4093 + 1);
                }
                long[] bloquesMija = new long[1];
                byte[] cifradoMija = cifrar(claveMija, textoMija, bloquesMija);
                assertEquals(casoMija[1], bloquesMija[0], "bloques al cifrar " + casoMija[0]);
                assertEquals(ChunkCipher.tamanoCifrado(casoMija[0]), cifradoMija.length);

                ByteArrayOutputStream claroMija = new ByteArrayOutputStream();
                long descifradosMija = ChunkCipher.descifrar(claveMija, FILE_ID_MIJA, new ByteArrayInputStream(cifradoMija),
                        claroMija);
                assertEquals(casoMija[1], descifradosMija, "bloques al descifrar " + casoMija[0]);
                assertArrayEquals(textoMija, claroMija.toByteArray());
            }
        }
    }

    @Test
    void tamanoCifradoEsExactoEnLasFronteras() {
        long tagMija = AesGcmAead.LONGITUD_TAG_MIJA;
        assertEquals(tagMija, ChunkCipher.tamanoCifrado(0));
        assertEquals(1 + tagMija, ChunkCipher.tamanoCifrado(1));
        assertEquals(ChunkCipher.TAMANO_BLOQUE_MIJA + tagMija, ChunkCipher.tamanoCifrado(ChunkCipher.TAMANO_BLOQUE_MIJA));
        assertEquals(ChunkCipher.TAMANO_BLOQUE_MIJA + 1 + 2 * tagMija,
                ChunkCipher.tamanoCifrado(ChunkCipher.TAMANO_BLOQUE_MIJA + 1L));
    }

    @Test
    void unBloqueDeUnMebibyteCabeYUnByteMasNo() {
        try (SecureBytes claveMija = clave()) {
            assertDoesNotThrow(() -> ChunkCipher.cifrarBloque(claveMija, FILE_ID_MIJA, 0, true,
                    new byte[ChunkCipher.TAMANO_BLOQUE_MIJA]));
            assertThrows(IllegalArgumentException.class, () -> ChunkCipher.cifrarBloque(claveMija, FILE_ID_MIJA, 0, true,
                    new byte[ChunkCipher.TAMANO_BLOQUE_MIJA + 1]));
        }
    }

    @Test
    void elFileIdDebeMedirDieciseisBytesEnTodasLasOperaciones() {
        try (SecureBytes claveMija = clave()) {
            byte[] cortoMija = new byte[15];
            byte[] largoMija = new byte[17];
            for (byte[] malMija : List.of(cortoMija, largoMija)) {
                assertThrows(IllegalArgumentException.class, () -> ChunkCipher.cifrar(claveMija, malMija,
                        new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream()));
                assertThrows(IllegalArgumentException.class, () -> ChunkCipher.descifrar(claveMija, malMija,
                        new ByteArrayInputStream(new byte[16]), new ByteArrayOutputStream()));
                assertThrows(IllegalArgumentException.class,
                        () -> ChunkCipher.cifrarBloque(claveMija, malMija, 0, true, new byte[1]));
                assertThrows(IllegalArgumentException.class,
                        () -> ChunkCipher.descifrarBloque(claveMija, malMija, 0, true, new byte[16]));
                assertThrows(IllegalArgumentException.class, () -> ChunkCipher.datosAsociados(malMija, 0, true));
            }
        }
    }

    @Test
    void losDatosAsociadosDistinguenIndiceYUltimoBloque() {
        byte[] baseMija = ChunkCipher.datosAsociados(FILE_ID_MIJA, 1, false);
        assertEquals(25, baseMija.length);
        assertFalse(Arrays.equals(baseMija, ChunkCipher.datosAsociados(FILE_ID_MIJA, 2, false)));
        assertFalse(Arrays.equals(baseMija, ChunkCipher.datosAsociados(FILE_ID_MIJA, 1, true)));
        assertEquals(1, ChunkCipher.datosAsociados(FILE_ID_MIJA, 1, true)[24]);
        assertEquals(0, baseMija[24]);
    }

    // ------------------------------------------------------------------ Argon2id

    @Test
    void argonExigeSalDeOchoBytesYAceptaSecretoYDatosAsociadosNulos() {
        ParametrosArgon2 parametrosMija = new ParametrosArgon2(64, 1, 1);
        byte[] entradaMija = {1, 2, 3};
        assertThrows(IllegalArgumentException.class,
                () -> Argon2idKdf.derivar(entradaMija, new byte[7], null, parametrosMija));
        byte[] sinSecretoMija = Argon2idKdf.derivar(entradaMija, new byte[8], null, null, parametrosMija, 32);
        byte[] conSecretoMija = Argon2idKdf.derivar(entradaMija, new byte[8], new byte[] {9}, null, parametrosMija, 32);
        byte[] conDatosMija = Argon2idKdf.derivar(entradaMija, new byte[8], null, new byte[] {9}, parametrosMija, 32);
        assertEquals(32, sinSecretoMija.length);
        assertNotEquals(Arrays.toString(sinSecretoMija), Arrays.toString(conSecretoMija));
        assertNotEquals(Arrays.toString(sinSecretoMija), Arrays.toString(conDatosMija));
        assertNotEquals(Arrays.toString(conSecretoMija), Arrays.toString(conDatosMija));
        assertEquals(16, Argon2idKdf.derivar(entradaMija, new byte[8], null, null, parametrosMija, 16).length);
        try (SecureBytes derivadaMija = Argon2idKdf.derivar(entradaMija, new byte[8], null, parametrosMija)) {
            assertEquals(32, derivadaMija.longitud());
        }
    }

    // ------------------------------------------------------------------ MkEnvuelta

    @Test
    void envolturaIgualaPorNonceYCifrado() {
        KeyWrap.MkEnvuelta baseMija = new KeyWrap.MkEnvuelta(new byte[12], new byte[48]);
        byte[] otroNonceMija = new byte[12];
        otroNonceMija[0] = 1;
        byte[] otroCifradoMija = new byte[48];
        otroCifradoMija[47] = 1;
        assertEquals(baseMija, new KeyWrap.MkEnvuelta(new byte[12], new byte[48]));
        assertNotEquals(baseMija, new KeyWrap.MkEnvuelta(otroNonceMija, new byte[48]));
        assertNotEquals(baseMija, new KeyWrap.MkEnvuelta(new byte[12], otroCifradoMija));
        assertNotEquals(baseMija, "otra cosa");
        assertNotEquals(baseMija, null);
        baseMija.nonceMija()[0]++;
        baseMija.cifradoMija()[0]++;
        assertEquals(baseMija, new KeyWrap.MkEnvuelta(new byte[12], new byte[48]), "los accesores devuelven copias");
    }

    // ------------------------------------------------------------------ SecureBytes y PoolMemoria

    @Test
    void secureBytesInformaSuEstadoYEntregaLosBytesAlConsumidor() {
        SecureBytes seguroMija = SecureBytes.copiaDe(new byte[] {4, 5, 6});
        assertFalse(seguroMija.estaCerrado());
        assertEquals(3, seguroMija.longitud());
        AtomicBoolean llamadoMija = new AtomicBoolean();
        seguroMija.usarBytes(copiaMija -> {
            llamadoMija.set(true);
            assertArrayEquals(new byte[] {4, 5, 6}, copiaMija);
        });
        assertTrue(llamadoMija.get(), "usarBytes debe invocar al consumidor");
        seguroMija.close();
        assertTrue(seguroMija.estaCerrado());
        assertTrue(PoolMemoria.global().bloques() > 0);
    }

    @Test
    void elLimiteDeLaRanuraEsSesentaYCuatroBytes() {
        for (int longitudMija : new int[] {1, 63, 64, 65, 200}) {
            byte[] datosMija = new byte[longitudMija];
            for (int indiceMija = 0; indiceMija < longitudMija; indiceMija++) {
                datosMija[indiceMija] = (byte) (indiceMija + 1);
            }
            try (SecureBytes seguroMija = SecureBytes.copiaDe(datosMija)) {
                assertEquals(longitudMija, seguroMija.longitud());
                assertArrayEquals(datosMija, seguroMija.bytes());
                assertNotSame(datosMija, seguroMija.bytes());
            }
        }
    }

    @Test
    void elPoolDevuelveRanurasDistintasLasReutilizaYSeAmplia() {
        PoolMemoria poolMija = PoolMemoria.global();
        int bloquesAntesMija = poolMija.bloques();
        long ranurasPorBloqueMija = PoolMemoria.BLOQUE_MIJA / PoolMemoria.RANURA_MIJA;
        List<PoolMemoria.Ranura> prestadasMija = new ArrayList<>();
        Set<String> identidadesMija = new HashSet<>();
        try {
            // Más ranuras de las que caben en un bloque obliga a ampliar el pool.
            for (long indiceMija = 0; indiceMija < ranurasPorBloqueMija + 10; indiceMija++) {
                PoolMemoria.Ranura ranuraMija = poolMija.tomar();
                assertEquals(PoolMemoria.RANURA_MIJA, ranuraMija.segmentoMija().byteSize());
                assertTrue(identidadesMija.add(ranuraMija.indiceBloqueMija() + ":" + ranuraMija.indiceRanuraMija()),
                        "dos préstamos no pueden compartir ranura");
                ranuraMija.segmentoMija().fill((byte) (indiceMija % 250 + 1));
                prestadasMija.add(ranuraMija);
            }
            assertTrue(poolMija.bloques() >= bloquesAntesMija, "el pool no encoge");
            // Cada ranura conserva su propio valor: no se pisan entre sí.
            for (int indiceMija = 0; indiceMija < prestadasMija.size(); indiceMija++) {
                byte esperadoMija = (byte) (indiceMija % 250 + 1);
                assertEquals(esperadoMija, prestadasMija.get(indiceMija).segmentoMija().get(ValueLayout.JAVA_BYTE, 0));
                assertEquals(esperadoMija, prestadasMija.get(indiceMija).segmentoMija().get(ValueLayout.JAVA_BYTE,
                        PoolMemoria.RANURA_MIJA - 1));
            }
        } finally {
            for (PoolMemoria.Ranura ranuraMija : prestadasMija) {
                ranuraMija.segmentoMija().fill((byte) 0);
                poolMija.devolver(ranuraMija);
            }
        }
        // Lo devuelto se reutiliza: tomar de nuevo no amplía el pool.
        int bloquesTrasMija = poolMija.bloques();
        PoolMemoria.Ranura reutilizadaMija = poolMija.tomar();
        assertEquals(bloquesTrasMija, poolMija.bloques());
        assertEquals(0, reutilizadaMija.segmentoMija().get(ValueLayout.JAVA_BYTE, 0));
        poolMija.devolver(reutilizadaMija);
    }

    @Test
    void ampliarElPoolAgregaUnBloqueConTodasSusRanuras() {
        PoolMemoria poolMija = PoolMemoria.global();
        long ranurasPorBloqueMija = PoolMemoria.BLOQUE_MIJA / PoolMemoria.RANURA_MIJA;
        List<PoolMemoria.Ranura> prestadasMija = new ArrayList<>();
        try {
            int bloquesAntesMija = poolMija.bloques();
            // Se vacía lo que haya libre y se pide una más: tiene que nacer exactamente un bloque nuevo.
            for (int indiceMija = 0; indiceMija < ranurasPorBloqueMija * 3; indiceMija++) {
                prestadasMija.add(poolMija.tomar());
                if (poolMija.bloques() > bloquesAntesMija) {
                    break;
                }
            }
            assertEquals(bloquesAntesMija + 1, poolMija.bloques());
            // El bloque nuevo ofrece sus 1024 ranuras sin ampliar otra vez (menos la que ya se tomó).
            int bloquesTrasMija = poolMija.bloques();
            for (long indiceMija = 1; indiceMija < ranurasPorBloqueMija; indiceMija++) {
                prestadasMija.add(poolMija.tomar());
            }
            assertEquals(bloquesTrasMija, poolMija.bloques());
        } finally {
            for (PoolMemoria.Ranura ranuraMija : prestadasMija) {
                ranuraMija.segmentoMija().fill((byte) 0);
                poolMija.devolver(ranuraMija);
            }
        }
    }

    @Test
    void cerrarUnaClaveDevuelveLaRanuraAlPool() {
        PoolMemoria poolMija = PoolMemoria.global();
        Set<String> vistasMija = new HashSet<>();
        for (int indiceMija = 0; indiceMija < 200; indiceMija++) {
            PoolMemoria.Ranura ranuraMija = poolMija.tomar();
            vistasMija.add(ranuraMija.indiceBloqueMija() + ":" + ranuraMija.indiceRanuraMija());
            poolMija.devolver(ranuraMija);
        }
        assertEquals(1, vistasMija.size(), "tomar y devolver reutiliza siempre la misma ranura");
    }
}
