import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import javax.imageio.ImageIO;

/** Deployed recognition code plus one synthetic image/provider call; no Spring or database connection. */
public final class OtcmInvoiceOcrProbe {
    static final Set<String> RUNTIME_KEYS = Set.of("deepseek.api-key", "deepseek.model", "deepseek.endpoint");
    static final Set<String> SETTING_KEYS = Set.of("deepseekApiKey", "deepseekModel");
    public static final class SilentLoggingConfiguration { public SilentLoggingConfiguration() { } }

    public static void main(String[] args) {
        PrintStream output = System.out;
        PrintStream silent = new PrintStream(OutputStream.nullOutputStream(), true, StandardCharsets.UTF_8);
        System.setOut(silent); System.setErr(silent);
        AtomicInteger requests = new AtomicInteger(), status = new AtomicInteger(), writes = new AtomicInteger();
        AtomicBoolean rejected = new AtomicBoolean();
        AtomicReference<Object> memoryPreview = new AtomicReference<>();
        AtomicReference<String> providerFailure = new AtomicReference<>("");
        boolean[] matches = new boolean[5];
        boolean success = false;
        String code = "OCR_PROBE_INPUT_INVALID";
        try {
            if (args.length != 0) throw new IllegalArgumentException();
            System.clearProperty("java.util.logging.config.file");
            System.setProperty("java.util.logging.config.class", SilentLoggingConfiguration.class.getName());
            LogManager.getLogManager().reset(); Logger.getLogger("").setLevel(Level.OFF);
            ClassLoader loader = OtcmInvoiceOcrProbe.class.getClassLoader();
            for (String resource : List.of("ch/qos/logback/classic/LoggerContext.class", "org/apache/logging/log4j/core/LoggerContext.class",
                    "org/apache/log4j/Logger.class", "org/slf4j/impl/StaticLoggerBinder.class", "logback.xml")) {
                if (loader.getResource(resource) != null) { code = "OCR_PROBE_UNSAFE_CLASSPATH"; throw new IllegalStateException(); }
            }
            byte[] input = System.in.readNBytes(65537);
            if (input.length == 0 || input.length > 65536) throw new IllegalArgumentException();
            Class<?> json = Class.forName("com.alibaba.fastjson2.JSON");
            Object parsed = json.getMethod("parseObject", String.class).invoke(null, new String(input, StandardCharsets.UTF_8));
            java.util.Arrays.fill(input, (byte) 0);
            if (!(parsed instanceof Map<?, ?> data) || !data.keySet().equals(Set.of("runtimeProperties", "storedSettings"))
                    || !(data.get("runtimeProperties") instanceof Map<?, ?> runtime)
                    || !(data.get("storedSettings") instanceof Map<?, ?> stored)) throw new IllegalArgumentException();
            validateProperties(runtime, RUNTIME_KEYS); validateProperties(stored, SETTING_KEYS);
            // Preserve an explicitly configured blank/invalid endpoint; it must not silently use a different provider.
            String endpoint = runtime.containsKey("deepseek.endpoint") ? (String) runtime.get("deepseek.endpoint")
                : "https://api.deepseek.com/chat/completions";
            code = "OCR_ENDPOINT_UNSUPPORTED";
            URI expectedEndpoint = URI.create(endpoint);
            if (!validEndpoint(expectedEndpoint)) { code = "OCR_ENDPOINT_UNSUPPORTED"; throw new IllegalArgumentException(); }

            code = "OCR_PROBE_RUNTIME_UNAVAILABLE";
            Class<?> setting = Class.forName("com.ruoyi.hospital.domain.TcmClinicSetting");
            Class<?> mapper = Class.forName("com.ruoyi.hospital.mapper.TcmClinicSettingMapper");
            Object settingsMapper = settingsMapper(mapper, setting, stored, writes, memoryPreview, rejected);
            Object ai = Class.forName("com.ruoyi.hospital.service.impl.TcmAiSettingsService").getConstructor().newInstance();
            set(ai, "settingMapper", settingsMapper);
            set(ai, "runtimeApiKey", text(runtime.get("deepseek.api-key")));
            set(ai, "runtimeModel", text(runtime.get("deepseek.model")));
            if (text(ai.getClass().getMethod("getApiKey").invoke(ai)).isEmpty()) { code = "OCR_NOT_CONFIGURED"; throw new IllegalStateException(); }
            Object service = Class.forName("com.ruoyi.hospital.service.impl.TcmInvoiceImportService").getConstructor().newInstance();
            set(service, "aiSettingsService", ai); set(service, "settingMapper", settingsMapper); set(service, "endpoint", endpoint);
            for (String field : List.of("inventoryMapper", "herbService", "inventoryService")) {
                Field f = service.getClass().getDeclaredField(field); f.setAccessible(true);
                f.set(service, Proxy.newProxyInstance(loader, new Class<?>[] { f.getType() }, (proxy, method, values) -> {
                    rejected.set(true); throw new UnsupportedOperationException();
                }));
            }
            Object client = get(service, "restTemplate");
            Object factory = client.getClass().getMethod("getRequestFactory").invoke(client);
            factory.getClass().getMethod("setConnectTimeout", int.class).invoke(factory, 15000);
            factory.getClass().getMethod("setReadTimeout", int.class).invoke(factory, 90000);
            observeClient(client, expectedEndpoint, requests, status, rejected, providerFailure);
            byte[] image = syntheticInvoice();
            Class<?> multipart = Class.forName("org.springframework.web.multipart.MultipartFile");
            Object file = Proxy.newProxyInstance(loader, new Class<?>[] { multipart }, (proxy, method, values) -> switch (method.getName()) {
                case "getBytes" -> image.clone();
                case "getSize" -> (long) image.length;
                case "isEmpty" -> false;
                case "getName" -> "file";
                case "getOriginalFilename" -> "synthetic-ocr-invoice.png";
                case "getContentType" -> "image/png";
                case "getInputStream" -> new ByteArrayInputStream(image);
                default -> { rejected.set(true); throw new UnsupportedOperationException(); }
            });
            code = "OCR_RECOGNITION_FAILED";
            // Bound the entire recognition, including connect/write/read, without changing its request.
            // A daemon worker cannot keep this isolated process alive after a timed-out main returns.
            java.util.concurrent.ExecutorService worker = java.util.concurrent.Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "otcm-synthetic-ocr-probe"); thread.setDaemon(true); return thread;
            });
            Object preview;
            try {
                preview = worker.submit(() -> service.getClass().getMethod("recognize", multipart).invoke(service, file))
                    .get(90, java.util.concurrent.TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException error) {
                providerFailure.set("OCR_PROVIDER_TIMEOUT"); throw error;
            } finally { worker.shutdownNow(); }
            matches = validatePreview(preview);
            success = requests.get() == 1 && status.get() == 200 && writes.get() == 1 && memoryPreview.get() != null
                    && !rejected.get() && all(matches);
            code = success ? "OCR_VERIFIED" : "OCR_RESULT_MISMATCH";
        } catch (Throwable ignored) {
            success = false;
            if (rejected.get()) code = "OCR_PROBE_ACCESS_REJECTED";
            else if (status.get() >= 300) code = "OCR_PROVIDER_REJECTED";
            else if (!providerFailure.get().isEmpty()) code = providerFailure.get();
        }
        output.println("{\"success\":" + success + ",\"code\":\"" + code + "\",\"providerRequests\":" + requests.get()
            + ",\"providerStatus\":" + status.get() + ",\"memoryPreviewWrites\":" + writes.get() + ",\"rejectedAccess\":" + rejected.get()
            + ",\"supplierMatched\":" + matches[0] + ",\"itemMatched\":" + matches[1] + ",\"quantityMatched\":" + matches[2]
            + ",\"priceMatched\":" + matches[3] + ",\"currencyMatched\":" + matches[4] + "}");
        output.flush();
    }

    static void observeClient(Object client, URI expectedEndpoint, AtomicInteger requests, AtomicInteger status,
            AtomicBoolean rejected, AtomicReference<String> providerFailure) throws Exception {
        ClassLoader loader = OtcmInvoiceOcrProbe.class.getClassLoader();
        Class<?> json = Class.forName("com.alibaba.fastjson2.JSON");
        Class<?> interceptor = Class.forName("org.springframework.http.client.ClientHttpRequestInterceptor");
        Class<?> requestType = Class.forName("org.springframework.http.HttpRequest");
        Class<?> execution = Class.forName("org.springframework.http.client.ClientHttpRequestExecution");
        Class<?> responseType = Class.forName("org.springframework.http.client.ClientHttpResponse");
        Object observer = Proxy.newProxyInstance(loader, new Class<?>[] { interceptor }, (proxy, method, values) -> {
            if (!method.getName().equals("intercept") || !expectedEndpoint.equals(requestType.getMethod("getURI").invoke(values[0]))
                    || !requests.compareAndSet(0, 1)) { rejected.set(true); throw new UnsupportedOperationException(); }
            Object response;
            try { response = execution.getMethod("execute", requestType, byte[].class).invoke(values[2], values[0], values[1]); }
            catch (Throwable error) { providerFailure.set(networkFailure(error)); throw error; }
            byte[] bytes;
            try {
                status.set((Integer) responseType.getMethod("getRawStatusCode").invoke(response));
                bytes = ((java.io.InputStream) responseType.getMethod("getBody").invoke(response)).readNBytes(262145);
                if (bytes.length > 262144) { providerFailure.set("OCR_RESPONSE_TOO_LARGE"); throw new IllegalStateException(); }
            } catch (Throwable error) {
                if (providerFailure.get().isEmpty()) providerFailure.set(networkFailure(error));
                responseType.getMethod("close").invoke(response);
                throw error;
            }
            if (status.get() == 200) providerFailure.set(responseFailure(json, bytes));
            return Proxy.newProxyInstance(loader, new Class<?>[] { responseType }, (p, responseMethod, responseValues) ->
                responseMethod.getName().equals("getBody") ? new ByteArrayInputStream(bytes) : responseMethod.invoke(response, responseValues));
        });
        client.getClass().getMethod("setInterceptors", List.class).invoke(client, List.of(observer));
    }

    static Object settingsMapper(Class<?> mapper, Class<?> setting, Map<?, ?> stored, AtomicInteger writes,
            AtomicReference<Object> memoryPreview, AtomicBoolean rejected) {
        return Proxy.newProxyInstance(mapper.getClassLoader(), new Class<?>[] { mapper }, (proxy, method, values) -> {
            if (method.getName().equals("selectSettingByKey") && values != null && values.length == 1 && SETTING_KEYS.contains(values[0])) {
                Object value = stored.get(values[0]);
                if (value == null) return null;
                Object row = setting.getConstructor().newInstance();
                setting.getMethod("setSettingKey", String.class).invoke(row, values[0]);
                setting.getMethod("setSettingValue", String.class).invoke(row, value);
                return row;
            }
            if (method.getName().equals("insertSetting") && values != null && values.length == 1 && setting.isInstance(values[0])) {
                String key = (String) setting.getMethod("getSettingKey").invoke(values[0]);
                String value = (String) setting.getMethod("getSettingValue").invoke(values[0]);
                if (key != null && key.matches("inventoryInvoice:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                        && value != null && value.length() <= 65536 && writes.compareAndSet(0, 1)) {
                    memoryPreview.set(value); return 1;
                }
            }
            rejected.set(true); throw new UnsupportedOperationException();
        });
    }

    static boolean validEndpoint(URI endpoint) {
        String host = endpoint.getHost();
        return "https".equalsIgnoreCase(endpoint.getScheme()) && host != null && host.matches("(?i)[a-z0-9][a-z0-9.-]*\\.[a-z]{2,}")
            && !host.toLowerCase(java.util.Locale.ROOT).matches(".*\\.(localhost|local|internal|invalid)")
            && (endpoint.getPort() == -1 || endpoint.getPort() == 443) && endpoint.getUserInfo() == null
            && endpoint.getQuery() == null && endpoint.getFragment() == null
            && endpoint.getPath().matches("/(?:[a-zA-Z0-9_-]+/)*chat/completions");
    }

    static String networkFailure(Throwable error) {
        for (int depth = 0; error != null && depth < 12; depth++, error = error.getCause()) {
            if (error instanceof java.net.SocketTimeoutException) return "OCR_PROVIDER_TIMEOUT";
            if (error instanceof javax.net.ssl.SSLException) return "OCR_PROVIDER_TLS_FAILED";
            if (error instanceof java.net.ConnectException || error instanceof java.net.UnknownHostException) return "OCR_PROVIDER_CONNECT_FAILED";
            if (error instanceof java.io.EOFException || error instanceof java.net.SocketException) return "OCR_PROVIDER_CONNECTION_CLOSED";
        }
        return "OCR_PROVIDER_IO_FAILED";
    }

    private static String responseFailure(Class<?> json, byte[] bytes) {
        try {
            Object parsed = json.getMethod("parseObject", String.class).invoke(null, new String(bytes, StandardCharsets.UTF_8));
            if (!(parsed instanceof Map<?, ?> envelope) || !(envelope.get("choices") instanceof List<?> choices) || choices.isEmpty()
                    || !(choices.get(0) instanceof Map<?, ?> choice) || !(choice.get("message") instanceof Map<?, ?> message)
                    || !(message.get("content") instanceof String content) || content.isBlank()) return "OCR_EMPTY_RESULT";
            Object decoded = json.getMethod("parseObject", String.class).invoke(null,
                content.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim());
            if (!(decoded instanceof Map<?, ?> preview) || !(preview.get("items") instanceof List<?> items) || items.isEmpty()) return "OCR_EMPTY_RESULT";
            return "";
        } catch (Throwable error) { return "OCR_INVALID_RESPONSE"; }
    }

    static boolean[] validatePreview(Object result) {
        boolean[] matches = new boolean[5];
        if (!(result instanceof Map<?, ?> preview)) return matches;
        matches[0] = normalized(preview.get("supplier")).equals("qasuppliesltd");
        matches[4] = "CAD".equalsIgnoreCase(text(preview.get("currency")));
        if (!(preview.get("items") instanceof List<?> items) || items.size() != 1 || !(items.get(0) instanceof Map<?, ?> item)) return matches;
        String name = normalized(item.get("invoiceName"));
        matches[1] = name.equals("shanzha10g") || name.equals("shanzha");
        matches[2] = decimalEquals(item.get("quantity"), "10");
        matches[3] = decimalEquals(item.get("unitPriceBeforeDiscount"), "0.25");
        return matches;
    }

    static byte[] syntheticInvoice() throws Exception {
        BufferedImage image = new BufferedImage(1400, 560, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, 1400, 560);
        graphics.setColor(Color.BLACK); graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 30));
        String[] lines = { "SYNTHETIC TEST INVOICE - NO REAL PURCHASE", "Supplier: QA Supplies Ltd", "Invoice: QA-OCR-001     Currency: CAD",
            "Herb / package        Quantity       Unit       Unit price before discount", "Shan Zha (10g)         10             bag        CAD 0.25",
            "Subtotal: CAD 2.50    Discount: CAD 0.00    Total: CAD 2.50" };
        for (int index = 0; index < lines.length; index++) graphics.drawString(lines[index], 35, 65 + index * 75);
        graphics.dispose(); ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output); return output.toByteArray();
    }

    private static void validateProperties(Map<?, ?> values, Set<String> allowed) {
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            if (!allowed.contains(entry.getKey()) || !(entry.getValue() instanceof String value)
                    || value.length() > 4096 || value.contains("\0") || value.contains("${")) throw new IllegalArgumentException();
        }
    }
    private static boolean decimalEquals(Object value, String expected) {
        try { return new BigDecimal(text(value)).compareTo(new BigDecimal(expected)) == 0; } catch (Exception e) { return false; }
    }
    static boolean all(boolean[] values) { for (boolean value : values) if (!value) return false; return true; }
    private static String normalized(Object value) { return text(value).toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", ""); }
    private static String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private static Object get(Object target, String name) throws Exception { Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target); }
    private static void set(Object target, String name, Object value) throws Exception { Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); f.set(target, value); }
}
