package com.ruoyi.hospital.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

/**
 * OTCM informed consent template.
 */
public final class ConsentDocumentTemplate
{
    public static final String VERSION = "otcm-consent-2026-04";
    private static final String DEFAULT_TITLE = "OTCM Informed Consent / OTCM 知情同意书";

    private static final List<ConsentSection> SECTIONS = Collections.unmodifiableList(Arrays.asList(
            new ConsentSection(
                    "patient_consent",
                    "Patient Consent / 患者同意",
                    Arrays.asList(
                            "I, the undersigned, consent to Traditional Chinese Medicine (TCM) diagnosis and treatments including acupuncture, Chinese herbal medicine in pill form, auricular acupuncture, cupping, and other associated modalities by the members of the OTCM Staff.",
                            "I understand that methods of treatment may include, but are not limited to, acupuncture, electrical stimulation, moxibustion, cupping, skin scraping, bloodletting, Tui-Na (Chinese massage), Chinese herbal medicine in pill form, medicated diet, and other related TCM therapies.")),
            new ConsentSection(
                    "risks_and_side_effects",
                    "Risks and Side Effects / 风险与副作用",
                    Arrays.asList(
                            "I have been informed that acupuncture is a safe method of treatment, but it may have side effects such as bruising, bleeding, numbness or tingling, dizziness or fainting, and temporary skin marks from cupping or skin scraping.",
                            "I understand that unusual risks of acupuncture include spontaneous miscarriage, tissue damage, organ puncture including pneumothorax, infection, and burns or scarring from moxibustion.",
                            "I understand that while this document describes the major risks of treatment, other side effects and risks may occur.")),
            new ConsentSection(
                    "herbal_medicine_and_pregnancy",
                    "Herbal Medicine and Pregnancy / 草药与怀孕",
                    Arrays.asList(
                            "I understand that some herbs may be inappropriate during pregnancy and that herbal formulas and acupuncture points may affect pregnancy.",
                            "Possible side effects of taking herbs include nausea, gas, stomach ache, vomiting, headache, diarrhea, rashes, hives, and tingling of the tongue.",
                            "I agree to immediately notify the clinic if I experience unanticipated or unpleasant effects, or if I am or become pregnant.")),
            new ConsentSection(
                    "medical_history_disclosure",
                    "Medical History Disclosure / 病史披露",
                    Arrays.asList(
                            "I acknowledge that I have informed my TCM practitioners about my relevant health history, including allergies, metal implants, major bleeding disorders, pacemaker, and infectious diseases.",
                            "I understand that the medical staff cannot guarantee treatment results and that treatment decisions are based on the information I provide.")),
            new ConsentSection(
                    "confidentiality",
                    "Confidentiality / 隐私保密",
                    Arrays.asList(
                            "I understand that clinical medical and administrative staff may review my medical records and lab reports, but all records will be kept confidential and will not be released without my written consent.")),
            new ConsentSection(
                    "consent_statement",
                    "Consent Statement / 同意声明",
                    Arrays.asList(
                            "By submitting this consent, I confirm that I have read, or been informed and discussed this consent, to be diagnosed, consulted, and treated.",
                            "I have been told about the benefits and risks of acupuncture and all other TCM procedures or treatments, and I have had an opportunity to ask questions.",
                            "I intend this consent form to cover the entire course of treatment for my present condition and any future condition for which I seek treatment from OTCM Acupuncture Clinic, and I understand that I may withdraw consent at any time through clear communication.")),
            new ConsentSection(
                    "financial_obligations",
                    "Financial Obligations / 费用责任",
                    Arrays.asList(
                            "I acknowledge that the fees and charges for diagnosis, consultation, treatments, and purchases have been explained to me clearly.",
                            "I understand that the fees are not covered under OHIP and must be paid fully by myself.",
                            "I am responsible for full and prompt payment after services have been rendered, and I may claim reimbursement from third-party insurance if applicable.",
                            "There will be no refund for services rendered, and herbs or other goods and services are non-refundable after purchase.")),
            new ConsentSection(
                    "cancellation_policy",
                    "Cancellation, No-Show, and Purchase Policy / 取消与缺席政策",
                    Arrays.asList(
                            "I understand that appointments are reserved specifically for me and that the clinic requests 24 hours notice when cancelling or rescheduling appointments.",
                            "I understand that shorter notice or no-show may result in a full service charge of $100.",
                            "I understand that treatment fees do not include the cost of pills and that all herbs and other goods or services are non-refundable after purchase.")),
            new ConsentSection(
                    "liability_clause",
                    "Exemption of Liability Clause / 责任豁免条款",
                    Arrays.asList(
                            "I request and consent to receive traditional Chinese medicine treatments including acupuncture, herbal medicine, Tuina massage, and other related treatments from practitioners and supervisors at the OTCM Acupuncture Clinic.",
                            "I acknowledge that the above treatments and their ramifications have been explained to me, and I agree not to commence legal action against OTCM staff or related clinic parties solely because of unexpected effects or treatment results."))));

    private ConsentDocumentTemplate()
    {
    }

    public static String getVersion()
    {
        return VERSION;
    }

    public static String getTitle(Object configuredTemplate)
    {
        Map<String, Object> template = normalizeTemplate(configuredTemplate);
        Object title = template.get("title");
        return title != null && !String.valueOf(title).trim().isEmpty()
                ? String.valueOf(title).trim()
                : DEFAULT_TITLE;
    }

    public static String getVersion(Object configuredTemplate)
    {
        Map<String, Object> template = normalizeTemplate(configuredTemplate);
        Object version = template.get("version");
        return version != null && !String.valueOf(version).trim().isEmpty()
                ? String.valueOf(version).trim()
                : VERSION;
    }

    public static List<ConsentSection> getSections()
    {
        return SECTIONS;
    }

    public static List<String> getSectionKeys()
    {
        List<String> keys = new ArrayList<>();
        for (ConsentSection section : SECTIONS)
        {
            keys.add(section.getKey());
        }
        return keys;
    }

    public static List<String> getSectionKeys(Object configuredTemplate)
    {
        List<String> keys = new ArrayList<>();
        for (Map<String, Object> section : toResponseSections(configuredTemplate))
        {
            Object key = section.get("key");
            if (key != null && !String.valueOf(key).trim().isEmpty())
            {
                keys.add(String.valueOf(key).trim());
            }
        }
        return keys.isEmpty() ? getSectionKeys() : keys;
    }

    public static List<Map<String, Object>> toResponseSections()
    {
        List<Map<String, Object>> sections = new ArrayList<>();
        for (ConsentSection section : SECTIONS)
        {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", section.getKey());
            item.put("title", section.getTitle());
            item.put("paragraphs", section.getParagraphs());
            sections.add(item);
        }
        return sections;
    }

    public static List<Map<String, Object>> toResponseSections(Object configuredTemplate)
    {
        Map<String, Object> template = normalizeTemplate(configuredTemplate);
        Object sectionsObj = template.get("sections");
        if (!(sectionsObj instanceof Collection<?>))
        {
            return toResponseSections();
        }

        List<Map<String, Object>> sections = new ArrayList<>();
        int index = 1;
        for (Object rawSection : (Collection<?>) sectionsObj)
        {
            Map<String, Object> sectionMap = toMap(rawSection);
            String title = clean(sectionMap.get("title"));
            List<String> paragraphs = normalizeParagraphs(sectionMap.get("paragraphs"));
            if (title == null && paragraphs.isEmpty())
            {
                continue;
            }
            String key = clean(sectionMap.get("key"));
            if (key == null)
            {
                key = "section_" + index;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", key);
            item.put("title", title != null ? title : ("Section " + index));
            item.put("paragraphs", paragraphs);
            sections.add(item);
            index++;
        }
        return sections.isEmpty() ? toResponseSections() : sections;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> normalizeTemplate(Object value)
    {
        if (value instanceof String)
        {
            String text = String.valueOf(value).trim();
            if (!text.isEmpty())
            {
                try
                {
                    JSONObject parsed = JSON.parseObject(text);
                    if (parsed != null)
                    {
                        return new LinkedHashMap<>(parsed);
                    }
                }
                catch (Exception ignored)
                {
                }
            }
        }
        if (value instanceof JSONObject)
        {
            return new LinkedHashMap<>((JSONObject) value);
        }
        if (value instanceof Map<?, ?>)
        {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet())
            {
                if (entry.getKey() != null)
                {
                    result.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return result;
        }
        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("title", DEFAULT_TITLE);
        fallback.put("version", VERSION);
        fallback.put("sections", toResponseSections());
        return fallback;
    }

    private static Map<String, Object> toMap(Object value)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value instanceof JSONObject)
        {
            result.putAll((JSONObject) value);
            return result;
        }
        if (value instanceof Map<?, ?>)
        {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet())
            {
                if (entry.getKey() != null)
                {
                    result.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
        }
        return result;
    }

    private static List<String> normalizeParagraphs(Object value)
    {
        List<String> paragraphs = new ArrayList<>();
        if (value instanceof Collection<?>)
        {
            for (Object item : (Collection<?>) value)
            {
                String text = clean(item);
                if (text != null)
                {
                    paragraphs.add(text);
                }
            }
        }
        else
        {
            String text = clean(value);
            if (text != null)
            {
                paragraphs.add(text);
            }
        }
        return paragraphs;
    }

    private static String clean(Object value)
    {
        if (value == null)
        {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    public static final class ConsentSection
    {
        private final String key;
        private final String title;
        private final List<String> paragraphs;

        public ConsentSection(String key, String title, List<String> paragraphs)
        {
            this.key = key;
            this.title = title;
            this.paragraphs = paragraphs != null
                    ? Collections.unmodifiableList(new ArrayList<>(paragraphs))
                    : Collections.emptyList();
        }

        public String getKey()
        {
            return key;
        }

        public String getTitle()
        {
            return title;
        }

        public List<String> getParagraphs()
        {
            return paragraphs;
        }
    }
}
