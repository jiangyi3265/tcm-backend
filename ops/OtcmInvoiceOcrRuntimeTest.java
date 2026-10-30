import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Optional offline integration test against packaged application jars; HTTP is an in-memory fake. */
public final class OtcmInvoiceOcrRuntimeTest {
    static void check(boolean value) { if (!value) throw new AssertionError(); }
    static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    static Object get(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
    public static void main(String[] args) throws Exception {
        ClassLoader loader = OtcmInvoiceOcrRuntimeTest.class.getClassLoader();
        Class<?> json = Class.forName("com.alibaba.fastjson2.JSON");
        Class<?> mapper = Class.forName("com.ruoyi.hospital.mapper.TcmClinicSettingMapper");
        Class<?> setting = Class.forName("com.ruoyi.hospital.domain.TcmClinicSetting");
        Class<?> multipart = Class.forName("org.springframework.web.multipart.MultipartFile");
        Class<?> factoryType = Class.forName("org.springframework.http.client.ClientHttpRequestFactory");
        Class<?> requestType = Class.forName("org.springframework.http.client.ClientHttpRequest");
        Class<?> responseType = Class.forName("org.springframework.http.client.ClientHttpResponse");
        Class<?> headersType = Class.forName("org.springframework.http.HttpHeaders");
        Class<?> httpStatus = Class.forName("org.springframework.http.HttpStatus");
        URI endpoint = URI.create("https://synthetic.example.com/v1/chat/completions");
        for (String scenario : List.of("success", "empty", "invalid", "rejected", "timeout", "statusTimeout", "tooLarge")) {
            AtomicInteger requests = new AtomicInteger(), status = new AtomicInteger(), writes = new AtomicInteger(), closed = new AtomicInteger();
            AtomicBoolean rejected = new AtomicBoolean(); AtomicReference<Object> memory = new AtomicReference<>();
            AtomicReference<String> failure = new AtomicReference<>("");
            Object settings = OtcmInvoiceOcrProbe.settingsMapper(mapper, setting, Map.of("deepseekApiKey", "synthetic-key"), writes, memory, rejected);
            Object ai = Class.forName("com.ruoyi.hospital.service.impl.TcmAiSettingsService").getConstructor().newInstance();
            set(ai, "settingMapper", settings); set(ai, "runtimeApiKey", ""); set(ai, "runtimeModel", "ignored-model");
            Object service = Class.forName("com.ruoyi.hospital.service.impl.TcmInvoiceImportService").getConstructor().newInstance();
            set(service, "aiSettingsService", ai); set(service, "settingMapper", settings); set(service, "endpoint", endpoint.toString());
            ByteArrayOutputStream sent = new ByteArrayOutputStream();
            Object requestHeaders = headersType.getConstructor().newInstance(), responseHeaders = headersType.getConstructor().newInstance();
            headersType.getMethod("add", String.class, String.class).invoke(responseHeaders, "Content-Type", "application/json");
            String content = "{\"supplier\":\"QA Supplies Ltd\",\"currency\":\"CAD\",\"items\":[{\"invoiceName\":\"Shan Zha (10g)\",\"quantity\":10,\"unitPriceBeforeDiscount\":0.25}]}";
            Object envelope = Map.of("choices", List.of(Map.of("message", Map.of("content", content))));
            String body = (String) json.getMethod("toJSONString", Object.class).invoke(null, envelope);
            if (scenario.equals("empty")) body = "{\"choices\":[]}";
            if (scenario.equals("invalid")) body = "invalid json";
            if (scenario.equals("tooLarge")) body = "x".repeat(262145);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            int responseStatus = scenario.equals("rejected") ? 401 : 200;
            Object response = Proxy.newProxyInstance(loader, new Class<?>[] { responseType }, (p, method, values) -> switch (method.getName()) {
                case "getRawStatusCode" -> { if (scenario.equals("statusTimeout")) throw new java.net.SocketTimeoutException("synthetic"); yield responseStatus; }
                case "getStatusCode" -> httpStatus.getMethod("valueOf", int.class).invoke(null, responseStatus);
                case "getStatusText" -> "Synthetic";
                case "getHeaders" -> responseHeaders;
                case "getBody" -> new ByteArrayInputStream(bytes);
                case "close" -> { closed.incrementAndGet(); yield null; }
                default -> throw new AssertionError(method.getName());
            });
            Object request = Proxy.newProxyInstance(loader, new Class<?>[] { requestType }, (p, method, values) -> switch (method.getName()) {
                case "getHeaders" -> requestHeaders;
                case "getBody" -> sent;
                case "getURI" -> endpoint;
                case "getMethod" -> Class.forName("org.springframework.http.HttpMethod").getField("POST").get(null);
                case "getMethodValue" -> "POST";
                case "execute" -> { if (scenario.equals("timeout")) throw new java.net.SocketTimeoutException("synthetic"); yield response; }
                default -> throw new AssertionError(method.getName());
            });
            Object factory = Proxy.newProxyInstance(loader, new Class<?>[] { factoryType }, (p, method, values) -> {
                check(method.getName().equals("createRequest") && endpoint.equals(values[0])); return request;
            });
            Object client = get(service, "restTemplate");
            client.getClass().getMethod("setRequestFactory", factoryType).invoke(client, factory);
            OtcmInvoiceOcrProbe.observeClient(client, endpoint, requests, status, rejected, failure);
            byte[] image = OtcmInvoiceOcrProbe.syntheticInvoice();
            Object file = Proxy.newProxyInstance(loader, new Class<?>[] { multipart }, (p, method, values) -> switch (method.getName()) {
                case "getSize" -> (long) image.length;
                case "isEmpty" -> false;
                case "getBytes" -> image.clone();
                default -> throw new AssertionError(method.getName());
            });
            Object preview = null;
            try { preview = service.getClass().getMethod("recognize", multipart).invoke(service, file); }
            catch (java.lang.reflect.InvocationTargetException error) { check(!scenario.equals("success")); }
            check(requests.get() == 1 && !rejected.get());
            Object payload = json.getMethod("parseObject", String.class).invoke(null, sent.toString(StandardCharsets.UTF_8));
            check(payload instanceof Map<?, ?> map && "deepseek-flash".equals(map.get("model")));
            check(sent.toString(StandardCharsets.UTF_8).contains("data:image/png;base64,"));
            check(headersType.getMethod("getFirst", String.class).invoke(requestHeaders, "Authorization").equals("Bearer synthetic-key"));
            if (scenario.equals("success")) check(OtcmInvoiceOcrProbe.all(OtcmInvoiceOcrProbe.validatePreview(preview)) && writes.get() == 1 && failure.get().isEmpty());
            else check(writes.get() == 0 && memory.get() == null);
            if (scenario.equals("empty")) check(failure.get().equals("OCR_EMPTY_RESULT"));
            if (scenario.equals("invalid")) check(failure.get().equals("OCR_INVALID_RESPONSE"));
            if (scenario.equals("rejected")) check(status.get() == 401);
            if (scenario.equals("timeout") || scenario.equals("statusTimeout")) check(failure.get().equals("OCR_PROVIDER_TIMEOUT"));
            if (scenario.equals("tooLarge")) check(failure.get().equals("OCR_RESPONSE_TOO_LARGE"));
            if (!scenario.equals("timeout")) check(closed.get() == 1);
        }
        System.out.println("OCR_PROBE_PACKAGED_RUNTIME_MOCK_HTTP_PASSED");
    }
}
