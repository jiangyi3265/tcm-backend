package com.ruoyi.hospital.util;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.utils.StringUtils;

/**
 * 邮件模板白名单与渲染工具。
 */
public final class EmailTemplateRegistry
{
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{\\s*([^}]+?)\\s*\\}\\}");
    private static final Map<String, TemplateDefinition> DEFAULTS = new LinkedHashMap<String, TemplateDefinition>();
    private static final Map<String, String> ALIASES = new LinkedHashMap<String, String>();

    static
    {
        register("appointmentConfirmation", "预约确认",
                "{{clinicName}}｜预约确认",
                "您好 {{patientName}}，您的预约已确认。\n\n"
                        + "服务：{{serviceLabel}}\n"
                        + "医师：{{practitionerName}}\n"
                        + "时间：{{appointmentDate}} {{appointmentTime}}\n"
                        + "地址：{{clinicAddress}}\n\n"
                        + "如需管理或取消预约，请使用此链接：{{manageLink}}");
        register("appointmentCancellation", "预约取消",
                "{{clinicName}}｜预约取消通知",
                "您好 {{patientName}}，您的预约已取消。\n\n"
                        + "原预约时间：{{appointmentDate}} {{appointmentTime}}\n"
                        + "服务：{{serviceLabel}}\n\n"
                        + "如需重新预约，请联系诊所或使用预约管理链接：{{manageLink}}");
        register("reminder", "预约提醒",
                "{{clinicName}}｜预约提醒",
                "您好 {{patientName}}，提醒您将在 {{appointmentDate}} {{appointmentTime}} 到诊。\n\n"
                        + "服务：{{serviceLabel}}\n"
                        + "医师：{{practitionerName}}\n"
                        + "地址：{{clinicAddress}}\n\n"
                        + "如需调整，请使用预约管理链接：{{manageLink}}");
        register("consent", "知情同意书",
                "{{clinicName}}｜知情同意书签署",
                "您好 {{patientName}}，请在就诊前阅读并签署知情同意书：\n"
                        + "{{consentLink}}\n\n"
                        + "如有疑问，请联系 {{clinicName}}。");
        register("intake", "就诊资料表",
                "{{clinicName}}｜就诊资料表",
                "您好 {{patientName}}，请在就诊前填写就诊资料表：\n"
                        + "{{intakeLink}}\n\n"
                        + "这会帮助医师提前了解您的情况。");
        register("consultationRecord", "问诊报告",
                "{{clinicName}}｜问诊报告",
                "您好 {{patientName}}，您的问诊报告已生成。\n\n"
                        + "问诊编号：{{consultationId}}\n"
                        + "日期：{{consultationDate}}\n"
                        + "主诉：{{chiefComplaint}}\n\n"
                        + "报告链接：{{reportLink}}\n"
                        + "如有疑问请联系诊所。");
        register("invoice", "发票",
                "{{clinicName}}｜发票",
                "您好 {{patientName}}，您的发票已生成。\n\n"
                        + "问诊编号：{{consultationId}}\n"
                        + "日期：{{consultationDate}}\n"
                        + "金额：{{amount}}\n"
                        + "发票链接：{{invoiceLink}}\n\n"
                        + "感谢您的到访。");
        register("appointmentChange", "预约变更",
                "{{clinicName}}｜预约变动通知",
                "您好 {{patientName}}，您的预约信息已有变动，请以最新安排为准。\n\n"
                        + "原安排：\n{{previousAppointmentSummary}}\n"
                        + "最新安排：\n{{appointmentSummary}}\n"
                        + "如新时间不方便，请联系诊所，或使用预约管理链接：{{manageLink}}");
        register("aftercare", "治疗后护理",
                "{{clinicName}}｜治疗后护理提醒",
                "您好 {{patientName}}，感谢您今天到 {{clinicName}} 接受治疗。\n\n"
                        + "为帮助身体恢复，请留意以下护理建议：\n"
                        + "1. 今天请注意保暖，避免受凉、淋雨和二次损伤。\n"
                        + "2. 针灸后 4 小时内尽量不要洗澡；24 小时内避免饮酒、熬夜和剧烈运动。\n"
                        + "3. 如治疗部位出现轻微酸胀、发热或瘀痕，通常属于正常反应，请先休息观察。\n"
                        + "4. 如果出现明显疼痛、肿胀、持续出血、头晕或其他异常，请尽快联系诊所。\n\n"
                        + "地址：{{clinicAddress}}\n{{clinicName}}");
        register("followUp", "治疗后回访",
                "{{clinicName}}｜治疗后回访",
                "您好 {{patientName}}，这是治疗后的回访邮件，想了解您这几天的恢复情况。\n\n"
                        + "如果恢复顺利，请继续按护理建议休息和观察；如果仍有不适或出现异常，请及时联系诊所。\n\n"
                        + "感谢您对 {{clinicName}} 的信任。");
        register("internalBooking", "内部新预约通知",
                "{{clinicName}}｜新预约通知",
                "新预约通知\n\n"
                        + "病人：{{patientName}}\n"
                        + "电话：{{patientPhone}}\n"
                        + "邮箱：{{patientEmail}}\n\n"
                        + "预约信息：\n{{appointmentSummary}}\n"
                        + "管理链接：{{manageLink}}");
        register("internalAppointmentChange", "内部预约变更通知",
                "{{clinicName}}｜预约变动通知",
                "预约变动通知\n\n"
                        + "病人：{{patientName}}\n"
                        + "原安排：\n{{previousAppointmentSummary}}\n"
                        + "新安排：\n{{appointmentSummary}}");
        register("internalAppointmentCancellation", "内部预约取消通知",
                "{{clinicName}}｜预约取消通知",
                "预约取消通知\n\n"
                        + "病人：{{patientName}}\n"
                        + "预约信息：\n{{previousAppointmentSummary}}\n"
                        + "取消来源：{{cancellationSource}}");

        alias("appointment_confirm", "appointmentConfirmation");
        alias("appointment_confirmation", "appointmentConfirmation");
        alias("appointmentConfirmationEmail", "appointmentConfirmation");
        alias("appointment_cancel", "appointmentCancellation");
        alias("appointment_cancellation", "appointmentCancellation");
        alias("appointmentCancellationEmail", "appointmentCancellation");
        alias("appointment_reminder", "reminder");
        alias("reminderEmail", "reminder");
        alias("consent_form", "consent");
        alias("consentEmail", "consent");
        alias("intake_form", "intake");
        alias("intakeEmail", "intake");
        alias("consultationReport", "consultationRecord");
        alias("consultation_report", "consultationRecord");
        alias("report", "consultationRecord");
        alias("reportEmail", "consultationRecord");
        alias("invoiceEmail", "invoice");
        alias("appointment_change", "appointmentChange");
        alias("appointmentChangeEmail", "appointmentChange");
        alias("appointment_aftercare", "aftercare");
        alias("aftercareEmail", "aftercare");
        alias("appointment_follow_up", "followUp");
        alias("follow_up", "followUp");
        alias("followUpEmail", "followUp");
        alias("appointment_internal_new", "internalBooking");
        alias("internalBookingEmail", "internalBooking");
        alias("appointment_change_internal", "internalAppointmentChange");
        alias("internalAppointmentChangeEmail", "internalAppointmentChange");
        alias("appointment_cancel_internal", "internalAppointmentCancellation");
        alias("internalAppointmentCancellationEmail", "internalAppointmentCancellation");
    }

    private EmailTemplateRegistry()
    {
    }

    public static Set<String> keys()
    {
        return new LinkedHashSet<String>(DEFAULTS.keySet());
    }

    public static String canonicalKey(String rawKey)
    {
        if (StringUtils.isBlank(rawKey))
        {
            return "";
        }
        String key = rawKey.trim();
        if (DEFAULTS.containsKey(key))
        {
            return key;
        }
        String alias = ALIASES.get(key);
        return alias != null ? alias : "";
    }

    public static JSONObject normalize(Object value)
    {
        JSONObject normalized = defaultTemplates();
        Object source = parseSource(value);
        if (source instanceof JSONObject)
        {
            JSONObject object = (JSONObject) source;
            for (Map.Entry<String, Object> entry : object.entrySet())
            {
                mergeTemplate(normalized, entry.getKey(), entry.getValue());
            }
        }
        else if (source instanceof Map<?, ?>)
        {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) source).entrySet())
            {
                mergeTemplate(normalized, stringValue(entry.getKey()), entry.getValue());
            }
        }
        else if (source instanceof JSONArray)
        {
            for (Object item : (JSONArray) source)
            {
                mergeTemplate(normalized, templateKeyFromItem(item), item);
            }
        }
        else if (source instanceof Iterable<?>)
        {
            for (Object item : (Iterable<?>) source)
            {
                mergeTemplate(normalized, templateKeyFromItem(item), item);
            }
        }
        return normalized;
    }

    public static String getTemplateField(String settingsValue, String templateKey, String field)
    {
        String key = canonicalKey(templateKey);
        if (StringUtils.isBlank(key))
        {
            return "";
        }
        JSONObject template = normalize(settingsValue).getJSONObject(key);
        return template != null ? StringUtils.defaultString(template.getString(field)) : "";
    }

    public static RenderedEmail render(
            String settingsValue,
            String templateKey,
            Map<String, ?> variables,
            String fallbackSubject,
            String fallbackBody)
    {
        String key = canonicalKey(templateKey);
        if (StringUtils.isBlank(key))
        {
            key = templateKey;
        }
        JSONObject template = StringUtils.isNotBlank(key) ? normalize(settingsValue).getJSONObject(canonicalKey(key)) : null;
        String subject = template != null ? template.getString("subject") : "";
        String body = template != null ? template.getString("body") : "";
        subject = StringUtils.isNotBlank(subject) ? subject : StringUtils.defaultString(fallbackSubject);
        body = StringUtils.isNotBlank(body) ? body : StringUtils.defaultString(fallbackBody);
        return new RenderedEmail(renderText(subject, variables), renderText(body, variables), canonicalKey(key));
    }

    public static String renderText(String template, Map<String, ?> variables)
    {
        if (template == null)
        {
            return "";
        }
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find())
        {
            String key = matcher.group(1) != null ? matcher.group(1).trim() : "";
            Object value = variables != null ? variables.get(key) : null;
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(value != null ? String.valueOf(value) : ""));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static void mergeTemplate(JSONObject normalized, String rawKey, Object rawTemplate)
    {
        String key = canonicalKey(rawKey);
        if (StringUtils.isBlank(key))
        {
            return;
        }
        JSONObject fallback = normalized.getJSONObject(key);
        normalized.put(key, normalizeTemplate(rawTemplate, fallback));
    }

    private static JSONObject normalizeTemplate(Object rawTemplate, JSONObject fallback)
    {
        JSONObject template = new JSONObject();
        if (rawTemplate instanceof String)
        {
            template.put("subject", fallback.getString("subject"));
            template.put("body", cleanTemplateText((String) rawTemplate, fallback.getString("body")));
            return template;
        }
        JSONObject source = toJsonObject(rawTemplate);
        if (source == null)
        {
            template.put("subject", fallback.getString("subject"));
            template.put("body", fallback.getString("body"));
            return template;
        }
        template.put("subject", cleanTemplateText(firstString(source, "subject", "title", "name"),
                fallback.getString("subject")));
        template.put("body", cleanTemplateText(firstString(source, "body", "content", "templateBody", "text", "html"),
                fallback.getString("body")));
        return template;
    }

    private static JSONObject defaultTemplates()
    {
        JSONObject object = new JSONObject();
        for (Map.Entry<String, TemplateDefinition> entry : DEFAULTS.entrySet())
        {
            JSONObject template = new JSONObject();
            template.put("subject", entry.getValue().subject);
            template.put("body", entry.getValue().body);
            object.put(entry.getKey(), template);
        }
        return object;
    }

    private static Object parseSource(Object value)
    {
        if (!(value instanceof String))
        {
            return value;
        }
        String text = ((String) value).trim();
        if (text.isEmpty() || (!text.startsWith("{") && !text.startsWith("[")))
        {
            return null;
        }
        try
        {
            return JSON.parse(text);
        }
        catch (Exception ignored)
        {
            return null;
        }
    }

    private static String templateKeyFromItem(Object item)
    {
        JSONObject object = toJsonObject(item);
        if (object == null)
        {
            return "";
        }
        String key = firstString(object, "key", "templateKey", "type", "id", "name");
        return key != null ? key : "";
    }

    private static JSONObject toJsonObject(Object value)
    {
        if (value instanceof JSONObject)
        {
            return (JSONObject) value;
        }
        if (value instanceof Map<?, ?>)
        {
            JSONObject object = new JSONObject();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet())
            {
                if (entry.getKey() != null)
                {
                    object.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return object;
        }
        return null;
    }

    private static String firstString(JSONObject object, String... fields)
    {
        for (String field : fields)
        {
            Object value = object.get(field);
            if (value instanceof String)
            {
                return (String) value;
            }
        }
        return null;
    }

    private static String cleanTemplateText(String value, String fallback)
    {
        if (value == null)
        {
            return fallback;
        }
        String text = value.replace("\r\n", "\n").trim();
        return StringUtils.isNotBlank(text) ? text : fallback;
    }

    private static String stringValue(Object value)
    {
        return value != null ? String.valueOf(value) : "";
    }

    private static void register(String key, String label, String subject, String body)
    {
        DEFAULTS.put(key, new TemplateDefinition(label, subject, body));
    }

    private static void alias(String alias, String key)
    {
        ALIASES.put(alias, key);
    }

    private static final class TemplateDefinition
    {
        private final String label;
        private final String subject;
        private final String body;

        private TemplateDefinition(String label, String subject, String body)
        {
            this.label = label;
            this.subject = subject;
            this.body = body;
        }
    }

    public static final class RenderedEmail
    {
        private final String subject;
        private final String body;
        private final String templateKey;

        private RenderedEmail(String subject, String body, String templateKey)
        {
            this.subject = subject;
            this.body = body;
            this.templateKey = templateKey;
        }

        public String getSubject()
        {
            return subject;
        }

        public String getBody()
        {
            return body;
        }

        public String getTemplateKey()
        {
            return templateKey;
        }
    }
}
