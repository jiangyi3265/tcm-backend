import java.io.ByteArrayInputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;

/** Offline guard tests: no application credentials, databases or network. */
public final class OtcmInvoiceOcrProbeTest {
    public interface Mapper {
        Setting selectSettingByKey(String key);
        Setting selectSettingForUpdate(String key);
        int insertSetting(Setting setting);
        int updateSetting(Setting setting);
        List<Setting> selectAllSettings();
    }
    public static final class Setting {
        private String key, value;
        public Setting() { }
        public void setSettingKey(String value) { key = value; }
        public void setSettingValue(String value) { this.value = value; }
        public String getSettingKey() { return key; }
        public String getSettingValue() { return value; }
    }
    static void check(boolean value) { if (!value) throw new AssertionError(); }
    static void denied(Runnable operation) {
        try { operation.run(); throw new AssertionError("unguarded operation"); }
        catch (UnsupportedOperationException expected) { }
    }
    static Setting row(String key, String value) {
        Setting result = new Setting(); result.setSettingKey(key); result.setSettingValue(value); return result;
    }
    public static void main(String[] args) throws Exception {
        AtomicInteger writes = new AtomicInteger(); AtomicReference<Object> memory = new AtomicReference<>();
        AtomicBoolean rejected = new AtomicBoolean();
        Mapper mapper = (Mapper) OtcmInvoiceOcrProbe.settingsMapper(Mapper.class, Setting.class,
            Map.of("deepseekApiKey", "synthetic-key"), writes, memory, rejected);
        check(mapper.selectSettingByKey("deepseekApiKey").getSettingValue().equals("synthetic-key"));
        check(mapper.selectSettingByKey("deepseekModel") == null);
        denied(() -> mapper.selectSettingByKey("smtpSettings"));
        denied(() -> mapper.selectSettingForUpdate("deepseekApiKey"));
        denied(mapper::selectAllSettings);
        denied(() -> mapper.updateSetting(row("deepseekApiKey", "changed")));
        denied(() -> mapper.insertSetting(row("deepseekApiKey", "changed")));
        denied(() -> mapper.insertSetting(row("inventoryInvoice:invalid", "{}")));
        check(writes.get() == 0 && memory.get() == null && rejected.get());
        check(mapper.insertSetting(row("inventoryInvoice:11111111-2222-3333-4444-555555555555", "{\"synthetic\":true}")) == 1);
        denied(() -> mapper.insertSetting(row("inventoryInvoice:11111111-2222-3333-4444-555555555555", "{}")));
        check(writes.get() == 1 && memory.get().equals("{\"synthetic\":true}"));

        Map<String, Object> item = Map.of("invoiceName", "Shan Zha (10g)", "quantity", 10, "unitPriceBeforeDiscount", "0.25");
        check(OtcmInvoiceOcrProbe.all(OtcmInvoiceOcrProbe.validatePreview(Map.of("supplier", "QA Supplies Ltd", "currency", "CAD", "items", List.of(item)))));
        check(!OtcmInvoiceOcrProbe.all(OtcmInvoiceOcrProbe.validatePreview(Map.of("supplier", "QA Supplies Ltd", "currency", "CAD", "items", List.of(item, item)))));
        check(!OtcmInvoiceOcrProbe.all(OtcmInvoiceOcrProbe.validatePreview(Map.of("supplier", "Different supplier", "currency", "CAD", "items", List.of(item)))));
        for (String field : List.of("quantity", "unitPriceBeforeDiscount", "invoiceName")) {
            java.util.HashMap<String, Object> wrong = new java.util.HashMap<>(item); wrong.put(field, "999");
            check(!OtcmInvoiceOcrProbe.all(OtcmInvoiceOcrProbe.validatePreview(Map.of("supplier", "QA Supplies Ltd", "currency", "CAD", "items", List.of(wrong)))));
        }
        check(!OtcmInvoiceOcrProbe.all(OtcmInvoiceOcrProbe.validatePreview(null)));
        byte[] image = OtcmInvoiceOcrProbe.syntheticInvoice();
        check(image.length < 100000);
        var decoded = ImageIO.read(new ByteArrayInputStream(image));
        check(decoded != null && decoded.getWidth() == 1400 && decoded.getHeight() == 560);
        for (String endpoint : List.of("https://api.deepseek.com/chat/completions", "https://proxy.example.com/v1/chat/completions")) {
            check(OtcmInvoiceOcrProbe.validEndpoint(URI.create(endpoint)));
        }
        for (String endpoint : List.of("http://proxy.example.com/chat/completions", "https://user:pass@proxy.example.com/chat/completions",
                "https://localhost/chat/completions", "https://host.local/chat/completions", "https://127.0.0.1/chat/completions",
                "https://proxy.example.com:444/chat/completions", "https://proxy.example.com/chat/completions?key=x",
                "https://proxy.example.com/chat/completions#x", "https://proxy.example.com/admin")) {
            check(!OtcmInvoiceOcrProbe.validEndpoint(URI.create(endpoint)));
        }
        check(OtcmInvoiceOcrProbe.networkFailure(new InvocationTargetException(new java.net.SocketTimeoutException())).equals("OCR_PROVIDER_TIMEOUT"));
        check(OtcmInvoiceOcrProbe.networkFailure(new java.net.ConnectException()).equals("OCR_PROVIDER_CONNECT_FAILED"));
        check(OtcmInvoiceOcrProbe.networkFailure(new javax.net.ssl.SSLException("synthetic")).equals("OCR_PROVIDER_TLS_FAILED"));
        check(OtcmInvoiceOcrProbe.networkFailure(new java.io.EOFException()).equals("OCR_PROVIDER_CONNECTION_CLOSED"));
        check(OtcmInvoiceOcrProbe.networkFailure(new java.io.IOException()).equals("OCR_PROVIDER_IO_FAILED"));
        System.out.println("OCR_PROBE_OFFLINE_GUARDS_PASSED");
    }
}
