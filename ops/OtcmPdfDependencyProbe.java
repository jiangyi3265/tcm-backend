import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.awt.image.BufferedImage;

/** Synthetic, in-memory dependency check. Never starts Spring or calls a service method. */
public final class OtcmPdfDependencyProbe {
    public static void main(String[] args) {
        try {
            Class<?> serviceClass = Class.forName("com.ruoyi.hospital.service.impl.TcmPdfServiceImpl");
            Object service = serviceClass.getConstructor().newInstance();
            Method fontMethod = serviceClass.getDeclaredMethod("createFont");
            fontMethod.setAccessible(true);
            Object font = fontMethod.invoke(service);
            Class<?> fontClass = Class.forName("com.itextpdf.kernel.font.PdfFont");
            Class<?> writerClass = Class.forName("com.itextpdf.kernel.pdf.PdfWriter");
            Class<?> pdfClass = Class.forName("com.itextpdf.kernel.pdf.PdfDocument");
            Class<?> documentClass = Class.forName("com.itextpdf.layout.Document");
            Class<?> paragraphClass = Class.forName("com.itextpdf.layout.element.Paragraph");
            Class<?> blockClass = Class.forName("com.itextpdf.layout.element.IBlockElement");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            Object writer = writerClass.getConstructor(java.io.OutputStream.class).newInstance(output);
            Object pdf = pdfClass.getConstructor(writerClass).newInstance(writer);
            Object document = documentClass.getConstructor(pdfClass).newInstance(pdf);
            documentClass.getMethod("setFont", fontClass).invoke(document, font);
            Object paragraph = paragraphClass.getConstructor(String.class).newInstance("OTCM PDF \u4e2d\u6587\u6d4b\u8bd5");
            documentClass.getMethod("add", blockClass).invoke(document, paragraph);
            documentClass.getMethod("close").invoke(document);
            byte[] bytes = output.toByteArray();
            Class<?> loaderClass = Class.forName("org.apache.pdfbox.Loader");
            Class<?> pdDocumentClass = Class.forName("org.apache.pdfbox.pdmodel.PDDocument");
            Object read = loaderClass.getMethod("loadPDF", byte[].class).invoke(null, (Object) bytes);
            try {
                Class<?> stripperClass = Class.forName("org.apache.pdfbox.text.PDFTextStripper");
                Object stripper = stripperClass.getConstructor().newInstance();
                String text = (String) stripperClass.getMethod("getText", pdDocumentClass).invoke(stripper, read);
                boolean english = text.contains("OTCM PDF");
                boolean chinese = text.contains("\u4e2d\u6587\u6d4b\u8bd5");
                Class<?> rendererClass = Class.forName("org.apache.pdfbox.rendering.PDFRenderer");
                Object renderer = rendererClass.getConstructor(pdDocumentClass).newInstance(read);
                BufferedImage image = (BufferedImage) rendererClass.getMethod("renderImageWithDPI", int.class, float.class).invoke(renderer, 0, 72.0f);
                boolean rendered = image.getWidth() > 100 && image.getHeight() > 100;
                boolean ok = english && chinese && rendered && bytes.length > 100;
                System.out.println("OTCM_PDF_PROBE:{\"ok\":" + ok + ",\"englishText\":" + english + ",\"chineseText\":" + chinese
                        + ",\"rendered\":" + rendered + ",\"pdfBytes\":" + bytes.length + ",\"imageWidth\":" + image.getWidth()
                        + ",\"imageHeight\":" + image.getHeight() + "}");
                if (!ok) System.exit(2);
            } finally {
                pdDocumentClass.getMethod("close").invoke(read);
            }
        } catch (Throwable error) {
            while (error.getCause() != null && error.getCause() != error) error = error.getCause();
            String name = error.getClass().getSimpleName();
            String safe = name.matches("(?:UnsatisfiedLinkError|NoClassDefFoundError|ClassNotFoundException|NoSuchMethodException|InvocationTargetException|IOException|IllegalArgumentException|IllegalStateException|ExceptionInInitializerError|UnsupportedClassVersionError)") ? name : "OtherProbeFailure";
            System.out.println("OTCM_PDF_PROBE:{\"ok\":false,\"errorClass\":\"" + safe + "\"}");
            System.exit(1);
        }
    }
}
