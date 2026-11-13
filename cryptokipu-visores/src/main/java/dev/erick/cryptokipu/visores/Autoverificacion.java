package dev.erick.cryptokipu.visores;

import dev.erick.cryptokipu.app.puertos.VisorDocumentos.DocumentoAbierto;
import dev.erick.cryptokipu.app.puertos.VisorDocumentos.Paginado;
import dev.erick.cryptokipu.app.puertos.VisorDocumentos.Textual;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

/**
 * Comprobación de humo para la imagen {@code jlink}: genera un PDF, un Word, un Excel y un PowerPoint, los abre con
 * {@link VisoresEnMemoria} y los renderiza. Si falta un módulo (PDFBox, POI, xmlbeans…) o su {@code module-info} está
 * mal, falla con código de salida distinto de cero. Se ejecuta con {@code ./gradlew :cryptokipu-visores:verificarImagenJlink}.
 */
public final class Autoverificacion {

    private Autoverificacion() {
    }

    public static void main(String[] argumentosMija) throws IOException {
        System.setProperty("java.awt.headless", "true");
        VisoresEnMemoria visorMija = new VisoresEnMemoria();

        ByteArrayOutputStream pdfMija = new ByteArrayOutputStream();
        try (PDDocument documentoMija = new PDDocument()) {
            PDPage paginaMija = new PDPage();
            documentoMija.addPage(paginaMija);
            try (PDPageContentStream contenidoMija = new PDPageContentStream(documentoMija, paginaMija)) {
                contenidoMija.beginText();
                contenidoMija.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 20);
                contenidoMija.newLineAtOffset(72, 700);
                contenidoMija.showText("CryptoKipu");
                contenidoMija.endText();
            }
            documentoMija.save(pdfMija);
        }
        try (DocumentoAbierto abiertoMija = abrir(visorMija, "pdf", pdfMija.toByteArray())) {
            ((Paginado) abiertoMija).renderizar(0, 400);
        }

        ByteArrayOutputStream wordMija = new ByteArrayOutputStream();
        try (XWPFDocument documentoMija = new XWPFDocument()) {
            documentoMija.createParagraph().createRun().setText("Hola");
            documentoMija.write(wordMija);
        }
        try (DocumentoAbierto abiertoMija = abrir(visorMija, "docx", wordMija.toByteArray())) {
            comprobar(((Textual) abiertoMija).contenidoMija().contains("Hola"), "docx");
        }

        ByteArrayOutputStream excelMija = new ByteArrayOutputStream();
        try (XSSFWorkbook libroMija = new XSSFWorkbook()) {
            libroMija.createSheet("h").createRow(0).createCell(0).setCellValue("dato");
            libroMija.write(excelMija);
        }
        try (DocumentoAbierto abiertoMija = abrir(visorMija, "xlsx", excelMija.toByteArray())) {
            comprobar(((Textual) abiertoMija).contenidoMija().contains("dato"), "xlsx");
        }

        ByteArrayOutputStream pptMija = new ByteArrayOutputStream();
        try (XMLSlideShow presentacionMija = new XMLSlideShow()) {
            XSLFTextBox cajaMija = presentacionMija.createSlide().createTextBox();
            cajaMija.setAnchor(new Rectangle2D.Double(30, 30, 300, 60));
            cajaMija.setText("Diapositiva");
            presentacionMija.write(pptMija);
        }
        try (DocumentoAbierto abiertoMija = abrir(visorMija, "pptx", pptMija.toByteArray())) {
            ((Paginado) abiertoMija).renderizar(0, 400);
        }
        System.out.println("Autoverificación de visores: correcta (PDF, Word, Excel y PowerPoint)");
    }

    private static void comprobar(boolean cumpleMija, String formatoMija) {
        if (!cumpleMija) {
            throw new IllegalStateException("Contenido inesperado en " + formatoMija);
        }
    }

    private static DocumentoAbierto abrir(VisoresEnMemoria visorMija, String extensionMija, byte[] datosMija)
            throws IOException {
        return visorMija.abrir(extensionMija, new Memoria(datosMija), datosMija.length);
    }

    /** Canal de solo lectura sobre un arreglo. */
    private record Memoria(byte[] datosMija, int[] posicionMija) implements SeekableByteChannel {
        Memoria(byte[] datosMija) {
            this(datosMija, new int[1]);
        }

        @Override
        public int read(ByteBuffer destinoMija) {
            if (posicionMija[0] >= datosMija.length) {
                return -1;
            }
            int cantidadMija = Math.min(destinoMija.remaining(), datosMija.length - posicionMija[0]);
            destinoMija.put(datosMija, posicionMija[0], cantidadMija);
            posicionMija[0] += cantidadMija;
            return cantidadMija;
        }

        @Override
        public int write(ByteBuffer origenMija) throws IOException {
            throw new IOException("solo lectura");
        }

        @Override
        public long position() {
            return posicionMija[0];
        }

        @Override
        public SeekableByteChannel position(long nuevaMija) {
            posicionMija[0] = (int) nuevaMija;
            return this;
        }

        @Override
        public long size() {
            return datosMija.length;
        }

        @Override
        public SeekableByteChannel truncate(long tamanoMija) throws IOException {
            throw new IOException("solo lectura");
        }

        @Override
        public boolean isOpen() {
            return true;
        }

        @Override
        public void close() {
            // Solo memoria.
        }
    }
}
