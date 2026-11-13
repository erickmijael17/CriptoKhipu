package dev.erick.cryptokipu.core.format;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Header de la bóveda (plan, Fase 2.1).
 *
 * <ul>
 *   <li>Zona A: {@link #zonaAMija()} (inmutable, autenticada).</li>
 *   <li>Zona B: generación, umbral de autodestrucción, si se requiere llave y slots; protegida por {@link #macMija()}.</li>
 *   <li>Zona C: intentos fallidos y pista de footer; <b>no autenticada, no confiable</b>.</li>
 * </ul>
 *
 * @param generacionMija               aumenta en cada reescritura del header; decide entre primario y respaldo
 * @param umbralAutodestruccionMija    0 = desactivada
 * @param requiereLlaveMija            el slot principal usa una llave de seguridad (F4); indica a la interfaz si pedirla.
 *                                     Antes de desbloquear es solo una pista: si es falsa, el slot simplemente no abre
 * @param macMija                      HMAC-SHA256(K_hdr, Zona A ‖ Zona B)
 * @param intentosFallidosMija         Zona C
 * @param pistaFooterMija              Zona C: offset del último footer escrito (0 = sin pista)
 */
public record Header(ZonaA zonaAMija, List<Slot> slotsMija, long generacionMija, int umbralAutodestruccionMija,
        boolean requiereLlaveMija, byte[] macMija, int intentosFallidosMija, long pistaFooterMija) {

    public Header {
        Objects.requireNonNull(zonaAMija, "zonaAMija");
        slotsMija = List.copyOf(Objects.requireNonNull(slotsMija, "slotsMija"));
        if (slotsMija.size() < LimitesFormato.SLOTS_MIN_MIJA || slotsMija.size() > LimitesFormato.SLOTS_MAX_MIJA) {
            throw new IllegalArgumentException("Cantidad de slots fuera de rango");
        }
        macMija = Objects.requireNonNull(macMija, "macMija").clone();
        if (macMija.length != LimitesFormato.LONGITUD_MAC_MIJA) {
            throw new IllegalArgumentException("macMija debe medir 32 B");
        }
        if (generacionMija < 0 || umbralAutodestruccionMija < 0 || intentosFallidosMija < 0 || pistaFooterMija < 0) {
            throw new IllegalArgumentException("Valores negativos en el header");
        }
    }

    /** Header nuevo, generación 1, sin MAC calculado todavía. */
    public static Header nuevo(ZonaA zonaAMija, List<Slot> slotsMija, int umbralAutodestruccionMija,
            boolean requiereLlaveMija) {
        return new Header(zonaAMija, slotsMija, 1, umbralAutodestruccionMija, requiereLlaveMija,
                new byte[LimitesFormato.LONGITUD_MAC_MIJA], 0, 0);
    }

    /** Header nuevo sin llave de seguridad. */
    public static Header nuevo(ZonaA zonaAMija, List<Slot> slotsMija, int umbralAutodestruccionMija) {
        return nuevo(zonaAMija, slotsMija, umbralAutodestruccionMija, false);
    }

    @Override
    public byte[] macMija() {
        return macMija.clone();
    }

    /** Metadatos que forman parte de la AAD del slot principal. */
    public MetadatosPrincipal metadatosPrincipal() {
        return new MetadatosPrincipal(requiereLlaveMija, umbralAutodestruccionMija);
    }

    /** El slot principal está vacío: la bóveda fue autodestruida. */
    public boolean estaDestruido() {
        return slotsMija.getFirst().tipoMija() == TipoSlot.VACIO;
    }

    public Header conMac(byte[] nuevoMacMija) {
        return new Header(zonaAMija, slotsMija, generacionMija, umbralAutodestruccionMija, requiereLlaveMija,
                nuevoMacMija, intentosFallidosMija, pistaFooterMija);
    }

    /** Nueva versión de la zona B con generación + 1; el MAC debe recalcularse. */
    public Header conZonaB(List<Slot> nuevosSlotsMija, int nuevoUmbralMija, boolean nuevoRequiereLlaveMija) {
        return new Header(zonaAMija, nuevosSlotsMija, generacionMija + 1, nuevoUmbralMija, nuevoRequiereLlaveMija,
                new byte[LimitesFormato.LONGITUD_MAC_MIJA], intentosFallidosMija, pistaFooterMija);
    }

    /** Nueva versión de la zona B conservando si se requiere llave. */
    public Header conZonaB(List<Slot> nuevosSlotsMija, int nuevoUmbralMija) {
        return conZonaB(nuevosSlotsMija, nuevoUmbralMija, requiereLlaveMija);
    }

    public Header conZonaC(int intentosMija, long pistaMija) {
        return new Header(zonaAMija, slotsMija, generacionMija, umbralAutodestruccionMija, requiereLlaveMija, macMija,
                intentosMija, pistaMija);
    }

    @Override
    public boolean equals(Object otroMija) {
        return otroMija instanceof Header otroHeaderMija && zonaAMija.equals(otroHeaderMija.zonaAMija)
                && slotsMija.equals(otroHeaderMija.slotsMija) && generacionMija == otroHeaderMija.generacionMija
                && umbralAutodestruccionMija == otroHeaderMija.umbralAutodestruccionMija
                && requiereLlaveMija == otroHeaderMija.requiereLlaveMija
                && Arrays.equals(macMija, otroHeaderMija.macMija)
                && intentosFallidosMija == otroHeaderMija.intentosFallidosMija
                && pistaFooterMija == otroHeaderMija.pistaFooterMija;
    }

    @Override
    public int hashCode() {
        return Objects.hash(zonaAMija, slotsMija, generacionMija, umbralAutodestruccionMija, requiereLlaveMija,
                Arrays.hashCode(macMija), intentosFallidosMija, pistaFooterMija);
    }

    @Override
    public String toString() {
        return "Header[generacion=" + generacionMija + ", slots=" + slotsMija + "]";
    }
}
