package dev.erick.cryptokipu.visores;

import dev.erick.cryptokipu.app.puertos.VisorDocumentos;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.io.RandomAccessStreamCache;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

/**
 * Proveedor de {@link VisorDocumentos}: PDF (PDFBox 3, caché solo en memoria, páginas bajo demanda), Word y Excel
 * (POI, como texto) y PowerPoint (POI, diapositivas renderizadas con Java2D sin pantalla). Todo se lee del canal
 * descifrado a un arreglo en memoria; ningún formato escribe en disco.
 */
public final class VisoresEnMemoria implements VisorDocumentos {

    static {
        ConfiguracionSegura.silenciarRegistrosDeTerceros();
    }

    /** Caché de flujos de PDFBox: solo memoria (nunca {@code createTempFileOnlyStreamCache}). */
    static final RandomAccessStreamCache.StreamCacheCreateFunction CACHE_PDF_MIJA = IOUtils.createMemoryOnlyStreamCache();

    private static final Set<String> EXTENSIONES_MIJA = Set.of("pdf", "docx", "xlsx", "pptx");
    /** Ancho máximo de una página renderizada (evita imágenes gigantes por un ancho pedido absurdo). */
    private static final int ANCHO_MAXIMO_MIJA = 2400;
    private static final int FILAS_MAXIMAS_MIJA = 2_000;
    private static final int COLUMNAS_MAXIMAS_MIJA = 50;

    public VisoresEnMemoria() {
        ConfiguracionSegura.aplicar();
    }

    @Override
    public boolean soporta(String extensionMija) {
        return EXTENSIONES_MIJA.contains(extensionMija.toLowerCase(Locale.ROOT));
    }

    @Override
    public DocumentoAbierto abrir(String extensionMija, SeekableByteChannel canalMija, long tamanoMija)
            throws IOException {
        byte[] contenidoMija = leerCompleto(canalMija, tamanoMija);
        return switch (extensionMija.toLowerCase(Locale.ROOT)) {
            case "pdf" -> new Pdf(Loader.loadPDF(new RandomAccessReadBuffer(contenidoMija), CACHE_PDF_MIJA));
            case "pptx" -> new Presentacion(new XMLSlideShow(new ByteArrayInputStream(contenidoMija)));
            case "docx" -> new Textual(word(contenidoMija));
            case "xlsx" -> new Textual(excel(contenidoMija));
            default -> throw new IOException("Formato no soportado: " + extensionMija);
        };
    }

    static byte[] leerCompleto(SeekableByteChannel canalMija, long tamanoMija) throws IOException {
        if (tamanoMija < 0 || tamanoMija > TAMANO_MAXIMO_MIJA) {
            throw new IOException("Documento demasiado grande para verlo en memoria");
        }
        ByteBuffer bufferMija = ByteBuffer.allocate((int) tamanoMija);
        canalMija.position(0);
        while (bufferMija.hasRemaining()) {
            if (canalMija.read(bufferMija) < 0) {
                throw new IOException("Fin inesperado del contenido");
            }
        }
        return bufferMija.array();
    }

    private static String word(byte[] contenidoMija) throws IOException {
        try (XWPFDocument documentoMija = new XWPFDocument(new ByteArrayInputStream(contenidoMija));
                XWPFWordExtractor extractorMija = new XWPFWordExtractor(documentoMija)) {
            return extractorMija.getText();
        }
    }

    private static String excel(byte[] contenidoMija) throws IOException {
        StringBuilder textoMija = new StringBuilder();
        DataFormatter formatoMija = new DataFormatter(Locale.getDefault());
        try (XSSFWorkbook libroMija = new XSSFWorkbook(new ByteArrayInputStream(contenidoMija))) {
            for (Sheet hojaMija : libroMija) {
                textoMija.append("== ").append(hojaMija.getSheetName()).append(" ==\n");
                int filasMija = 0;
                for (Row filaMija : hojaMija) {
                    if (filasMija++ >= FILAS_MAXIMAS_MIJA) {
                        textoMija.append("…\n");
                        break;
                    }
                    int ultimaMija = Math.min(filaMija.getLastCellNum(), COLUMNAS_MAXIMAS_MIJA);
                    for (int columnaMija = 0; columnaMija < ultimaMija; columnaMija++) {
                        Cell celdaMija = filaMija.getCell(columnaMija);
                        if (columnaMija > 0) {
                            textoMija.append('\t');
                        }
                        textoMija.append(celdaMija == null ? "" : formatoMija.formatCellValue(celdaMija));
                    }
                    textoMija.append('\n');
                }
                textoMija.append('\n');
            }
        }
        return textoMija.toString();
    }

    /** Copia los píxeles de Java2D a ARGB (la interfaz los pasa a JavaFX sin javafx.swing). */
    static Imagen aImagen(BufferedImage imagenMija) {
        int anchoMija = imagenMija.getWidth();
        int altoMija = imagenMija.getHeight();
        int[] argbMija = imagenMija.getRGB(0, 0, anchoMija, altoMija, null, 0, anchoMija);
        return new Imagen(anchoMija, altoMija, argbMija);
    }

    private static int ancho(int pedidoMija) {
        return Math.clamp(pedidoMija, 100, ANCHO_MAXIMO_MIJA);
    }

    /** PDF con PDFBox: cada página se renderiza al pedirla. */
    private static final class Pdf implements Paginado {
        private final PDDocument documentoMija;
        private final PDFRenderer renderizadorMija;

        Pdf(PDDocument documentoMija) {
            this.documentoMija = documentoMija;
            this.renderizadorMija = new PDFRenderer(documentoMija);
            this.renderizadorMija.setSubsamplingAllowed(true);
        }

        @Override
        public int paginas() {
            return documentoMija.getNumberOfPages();
        }

        @Override
        public synchronized Imagen renderizar(int indiceMija, int anchoMaximoMija) throws IOException {
            PDRectangle cajaMija = documentoMija.getPage(indiceMija).getCropBox();
            float escalaMija = Math.min(4f, ancho(anchoMaximoMija) / Math.max(1f, cajaMija.getWidth()));
            return aImagen(renderizadorMija.renderImage(indiceMija, escalaMija, ImageType.RGB));
        }

        @Override
        public void close() {
            try {
                documentoMija.close();
            } catch (IOException _) {
                // Solo memoria: no hay nada que liberar en disco.
            }
        }
    }

    /** PowerPoint con POI: cada diapositiva se dibuja con Java2D (java.awt.headless=true) al pedirla. */
    private static final class Presentacion implements Paginado {
        private final XMLSlideShow presentacionMija;
        private final List<XSLFSlide> diapositivasMija;

        Presentacion(XMLSlideShow presentacionMija) {
            this.presentacionMija = presentacionMija;
            this.diapositivasMija = presentacionMija.getSlides();
        }

        @Override
        public int paginas() {
            return diapositivasMija.size();
        }

        @Override
        public synchronized Imagen renderizar(int indiceMija, int anchoMaximoMija) {
            Dimension tamanoMija = presentacionMija.getPageSize();
            double escalaMija = (double) ancho(anchoMaximoMija) / Math.max(1, tamanoMija.width);
            int anchoMija = (int) Math.ceil(tamanoMija.width * escalaMija);
            int altoMija = (int) Math.ceil(tamanoMija.height * escalaMija);
            BufferedImage imagenMija = new BufferedImage(anchoMija, altoMija, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graficoMija = imagenMija.createGraphics();
            try {
                graficoMija.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graficoMija.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                graficoMija.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                graficoMija.setColor(Color.WHITE);
                graficoMija.fillRect(0, 0, anchoMija, altoMija);
                graficoMija.scale(escalaMija, escalaMija);
                diapositivasMija.get(indiceMija).draw(graficoMija);
            } finally {
                graficoMija.dispose();
            }
            return aImagen(imagenMija);
        }

        @Override
        public void close() {
            try {
                presentacionMija.close();
            } catch (IOException _) {
                // Solo memoria.
            }
        }
    }
}
