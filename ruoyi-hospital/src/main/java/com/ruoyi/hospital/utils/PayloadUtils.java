package com.ruoyi.hospital.utils;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.hospital.domain.*;
import java.util.Date;

/**
 * 前后端数据格式转换工具类
 *
 * 前端使用扁平化 JSON 对象（所有字段在顶层），后端使用 payload JSON 字段存储扩展数据。
 * 此工具类负责两个方向的转换：
 * - flatten: 将 domain 对象转为前端期望的扁平 Map
 * - toXxx: 将前端发送的扁平 Map 转为 domain 对象
 */
public class PayloadUtils
{
    // ==================== Patient ====================

    private static final Set<String> PATIENT_DB_FIELDS = new HashSet<>(Arrays.asList(
            "id", "name", "firstName", "lastName", "email", "phone",
            "practitionerId", "isActive", "consentSigned", "consentSignedAt",
            "mergedInto", "deletedAt", "createdAt"));

    public static Map<String, Object> flatten(TcmPatient p)
    {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("name", p.getName());
        m.put("firstName", p.getFirstName());
        m.put("lastName", p.getLastName());
        m.put("email", p.getEmail());
        m.put("phone", p.getPhone());
        m.put("practitionerId", p.getPractitionerId());
        m.put("isActive", intToBool(p.getIsActive(), true));
        m.put("consentSigned", intToBool(p.getConsentSigned(), false));
        m.put("consentSignedAt", p.getConsentSignedAt());
        m.put("mergedInto", p.getMergedInto());
        m.put("deletedAt", p.getDeletedAt());
        // createdAt from DB create_time, can be overridden by payload
        if (p.getCreateTime() != null)
        {
            m.put("createdAt", formatDate(p.getCreateTime()));
        }
        mergePayload(m, p.getPayload());
        return m;
    }

    public static List<Map<String, Object>> flattenPatients(List<TcmPatient> list)
    {
        List<Map<String, Object>> result = new ArrayList<>(list.size());
        for (TcmPatient p : list) { result.add(flatten(p)); }
        return result;
    }

    public static TcmPatient toPatient(Map<String, Object> m)
    {
        TcmPatient p = new TcmPatient();
        p.setId(str(m, "id"));
        p.setName(str(m, "name"));
        p.setFirstName(str(m, "firstName"));
        p.setLastName(str(m, "lastName"));
        p.setEmail(str(m, "email"));
        p.setPhone(str(m, "phone"));
        p.setPractitionerId(str(m, "practitionerId"));
        p.setIsActive(boolToInt(m.get("isActive"), 1));
        p.setConsentSigned(boolToInt(m.get("consentSigned"), 0));
        p.setConsentSignedAt(str(m, "consentSignedAt"));
        p.setMergedInto(str(m, "mergedInto"));
        p.setDeletedAt(str(m, "deletedAt"));
        // If no email column but emails array present, use first email
        if (p.getEmail() == null && m.get("emails") instanceof List)
        {
            List<?> emails = (List<?>) m.get("emails");
            if (!emails.isEmpty()) { p.setEmail(String.valueOf(emails.get(0))); }
        }
        p.setPayload(packExtra(m, PATIENT_DB_FIELDS));
        return p;
    }

    // ==================== Consultation ====================

    private static final Set<String> CONSULT_DB_FIELDS = new HashSet<>(Arrays.asList(
            "id", "consultationId", "patientId", "practitionerId",
            "consultDate", "status", "branchId", "lockedAt", "version", "deletedAt",
            "date")); // "date" is a frontend alias for consultDate

    public static Map<String, Object> flatten(TcmConsultation c)
    {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("consultationId", c.getConsultationId());
        m.put("patientId", c.getPatientId());
        m.put("practitionerId", c.getPractitionerId());
        m.put("date", c.getConsultDate());
        m.put("status", c.getStatus());
        m.put("branchId", c.getBranchId());
        m.put("lockedAt", c.getLockedAt());
        m.put("version", c.getVersion());
        m.put("deletedAt", c.getDeletedAt());
        if (c.getCreateTime() != null)
        {
            m.put("createdAt", formatDate(c.getCreateTime()));
        }
        mergePayload(m, c.getPayload());
        return m;
    }

    public static List<Map<String, Object>> flattenConsultations(List<TcmConsultation> list)
    {
        List<Map<String, Object>> result = new ArrayList<>(list.size());
        for (TcmConsultation c : list) { result.add(flatten(c)); }
        return result;
    }

    public static TcmConsultation toConsultation(Map<String, Object> m)
    {
        TcmConsultation c = new TcmConsultation();
        c.setId(str(m, "id"));
        c.setConsultationId(str(m, "consultationId"));
        c.setPatientId(str(m, "patientId"));
        c.setPractitionerId(str(m, "practitionerId"));
        // Accept both "date" and "consultDate"
        String date = str(m, "consultDate");
        if (date == null) { date = str(m, "date"); }
        c.setConsultDate(date);
        c.setStatus(str(m, "status"));
        c.setBranchId(str(m, "branchId"));
        c.setLockedAt(str(m, "lockedAt"));
        if (m.get("version") instanceof Number)
        {
            c.setVersion(((Number) m.get("version")).intValue());
        }
        c.setDeletedAt(str(m, "deletedAt"));
        c.setPayload(packExtra(m, CONSULT_DB_FIELDS));
        return c;
    }

    // ==================== Appointment ====================

    private static final Set<String> APPT_DB_FIELDS = new HashSet<>(Arrays.asList(
            "id", "patientId", "practitionerId", "roomId", "serviceType",
            "startTime", "endTime", "status", "branchId"));

    public static Map<String, Object> flatten(TcmAppointment a)
    {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("patientId", a.getPatientId());
        m.put("practitionerId", a.getPractitionerId());
        m.put("roomId", a.getRoomId());
        m.put("serviceType", a.getServiceType());
        m.put("startTime", a.getStartTime());
        m.put("endTime", a.getEndTime());
        m.put("status", a.getStatus());
        m.put("branchId", a.getBranchId());
        if (a.getCreateTime() != null)
        {
            m.put("createdAt", formatDate(a.getCreateTime()));
        }
        mergePayload(m, a.getPayload());
        return m;
    }

    public static List<Map<String, Object>> flattenAppointments(List<TcmAppointment> list)
    {
        List<Map<String, Object>> result = new ArrayList<>(list.size());
        for (TcmAppointment a : list) { result.add(flatten(a)); }
        return result;
    }

    public static TcmAppointment toAppointment(Map<String, Object> m)
    {
        TcmAppointment a = new TcmAppointment();
        a.setId(str(m, "id"));
        a.setPatientId(str(m, "patientId"));
        a.setPractitionerId(str(m, "practitionerId"));
        a.setRoomId(str(m, "roomId"));
        a.setServiceType(str(m, "serviceType"));
        a.setStartTime(isoToMysqlDatetime(str(m, "startTime")));
        a.setEndTime(isoToMysqlDatetime(str(m, "endTime")));
        a.setStatus(str(m, "status"));
        a.setBranchId(str(m, "branchId"));
        a.setPayload(packExtra(m, APPT_DB_FIELDS));
        return a;
    }

    /**
     * 将 ISO-8601 格式日期字符串转换为 MySQL datetime 格式。
     * 例如 "2026-03-08T09:00:00.000Z" -> "2026-03-08 09:00:00"
     */
    private static String isoToMysqlDatetime(String iso)
    {
        if (iso == null || iso.isEmpty()) return iso;
        try
        {
            java.time.ZonedDateTime zdt = java.time.ZonedDateTime.parse(iso);
            // 转换为东八区 (中国标准时间)
            java.time.LocalDateTime local = zdt.withZoneSameInstant(java.time.ZoneId.of("Asia/Shanghai")).toLocalDateTime();
            return local.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        catch (Exception e)
        {
            // 如果已经是 MySQL 格式或无法解析，直接返回
            return iso;
        }
    }

    // ==================== Branch ====================

    public static Map<String, Object> flatten(TcmBranch b)
    {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", b.getId());
        m.put("name", b.getName());
        m.put("code", b.getCode());
        m.put("address", b.getAddress());
        m.put("phone", b.getPhone());
        m.put("email", b.getEmail());
        m.put("managerId", b.getManagerId());
        m.put("isActive", intToBool(b.getIsActive(), true));
        m.put("isMain", intToBool(b.getIsMain(), false));
        // Parse roomIds JSON string to array
        m.put("roomIds", parseJsonArray(b.getRoomIds()));
        m.put("createdAt", b.getCreateTime());
        return m;
    }

    public static List<Map<String, Object>> flattenBranches(List<TcmBranch> list)
    {
        List<Map<String, Object>> result = new ArrayList<>(list.size());
        for (TcmBranch b : list) { result.add(flatten(b)); }
        return result;
    }

    public static TcmBranch toBranch(Map<String, Object> m)
    {
        TcmBranch b = new TcmBranch();
        b.setId(str(m, "id"));
        b.setName(str(m, "name"));
        b.setCode(str(m, "code"));
        b.setAddress(str(m, "address"));
        b.setPhone(str(m, "phone"));
        b.setEmail(str(m, "email"));
        b.setManagerId(str(m, "managerId"));
        b.setIsActive(boolToInt(m.get("isActive"), 1));
        b.setIsMain(boolToInt(m.get("isMain"), 0));
        // Convert roomIds array to JSON string
        Object roomIds = m.get("roomIds");
        if (roomIds instanceof List)
        {
            b.setRoomIds(JSON.toJSONString(roomIds));
        }
        else if (roomIds instanceof String)
        {
            b.setRoomIds((String) roomIds);
        }
        return b;
    }

    // ==================== InventoryItem ====================

    public static Map<String, Object> flatten(TcmInventoryItem i)
    {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", i.getId());
        m.put("name", i.getName());
        m.put("category", i.getCategory());
        m.put("unit", i.getUnit());
        m.put("quantity", i.getQuantity());
        m.put("pricePerUnit", i.getPricePerUnit());
        m.put("minStockLevel", i.getMinStockLevel());
        m.put("supplier", i.getSupplier());
        m.put("supplierId", i.getSupplierId());
        m.put("gramsPerPacket", i.getGramsPerPacket());
        m.put("branchId", i.getBranchId());
        m.put("isActive", intToBool(i.getIsActive(), true));
        m.put("deletedAt", i.getDeletedAt());
        return m;
    }

    public static List<Map<String, Object>> flattenInventory(List<TcmInventoryItem> list)
    {
        List<Map<String, Object>> result = new ArrayList<>(list.size());
        for (TcmInventoryItem i : list) { result.add(flatten(i)); }
        return result;
    }

    public static TcmInventoryItem toInventoryItem(Map<String, Object> m)
    {
        TcmInventoryItem i = new TcmInventoryItem();
        i.setId(str(m, "id"));
        i.setName(str(m, "name"));
        i.setCategory(str(m, "category"));
        i.setUnit(str(m, "unit"));
        i.setQuantity(toBigDecimal(m.get("quantity")));
        i.setPricePerUnit(toBigDecimal(m.get("pricePerUnit")));
        i.setMinStockLevel(toBigDecimal(m.get("minStockLevel")));
        i.setSupplier(str(m, "supplier"));
        i.setSupplierId(str(m, "supplierId"));
        i.setGramsPerPacket(toBigDecimal(m.get("gramsPerPacket")));
        i.setBranchId(str(m, "branchId"));
        i.setIsActive(boolToInt(m.get("isActive"), 1));
        i.setDeletedAt(str(m, "deletedAt"));
        return i;
    }

    // ==================== Supplier ====================

    public static Map<String, Object> flatten(TcmSupplier s)
    {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("name", s.getName());
        m.put("contactPerson", s.getContactPerson());
        m.put("phone", s.getPhone());
        m.put("email", s.getEmail());
        m.put("address", s.getAddress());
        m.put("notes", s.getNotes());
        m.put("isActive", intToBool(s.getIsActive(), true));
        m.put("deletedAt", s.getDeletedAt());
        if (s.getCreateTime() != null)
        {
            m.put("createdAt", formatDate(s.getCreateTime()));
        }
        return m;
    }

    public static List<Map<String, Object>> flattenSuppliers(List<TcmSupplier> list)
    {
        List<Map<String, Object>> result = new ArrayList<>(list.size());
        for (TcmSupplier s : list) { result.add(flatten(s)); }
        return result;
    }

    public static TcmSupplier toSupplier(Map<String, Object> m)
    {
        TcmSupplier s = new TcmSupplier();
        s.setId(str(m, "id"));
        s.setName(str(m, "name"));
        s.setContactPerson(str(m, "contactPerson"));
        s.setPhone(str(m, "phone"));
        s.setEmail(str(m, "email"));
        s.setAddress(str(m, "address"));
        s.setNotes(str(m, "notes"));
        s.setIsActive(boolToInt(m.get("isActive"), 1));
        s.setDeletedAt(str(m, "deletedAt"));
        return s;
    }

    // ==================== Formula ====================

    @SuppressWarnings("unchecked")
    public static Map<String, Object> flattenFormula(TcmFormula f)
    {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", f.getId());
        m.put("name", f.getName());
        m.put("category", f.getCategory());
        m.put("description", f.getDescription());
        m.put("source", f.getSource());
        m.put("isActive", intToBool(f.getIsActive(), true));
        m.put("deletedAt", f.getDeletedAt());
        if (f.getCreateTime() != null)
        {
            m.put("createdAt", formatDate(f.getCreateTime()));
        }
        // Flatten items
        List<Map<String, Object>> items = new ArrayList<>();
        if (f.getItems() != null)
        {
            for (TcmFormulaItem item : f.getItems())
            {
                Map<String, Object> im = new LinkedHashMap<>();
                im.put("id", item.getId());
                im.put("herbName", item.getHerbName());
                im.put("dosage", item.getDosage());
                im.put("unit", item.getUnit());
                im.put("sortOrder", item.getSortOrder());
                im.put("notes", item.getNotes());
                items.add(im);
            }
        }
        m.put("items", items);
        return m;
    }

    public static List<Map<String, Object>> flattenFormulas(List<TcmFormula> list)
    {
        List<Map<String, Object>> result = new ArrayList<>(list.size());
        for (TcmFormula f : list) { result.add(flattenFormula(f)); }
        return result;
    }

    @SuppressWarnings("unchecked")
    public static TcmFormula toFormula(Map<String, Object> m)
    {
        TcmFormula f = new TcmFormula();
        f.setId(str(m, "id"));
        f.setName(str(m, "name"));
        f.setCategory(str(m, "category"));
        f.setDescription(str(m, "description"));
        f.setSource(str(m, "source"));
        f.setIsActive(boolToInt(m.get("isActive"), 1));
        f.setDeletedAt(str(m, "deletedAt"));
        // Parse items
        Object itemsObj = m.get("items");
        if (itemsObj instanceof List)
        {
            List<Map<String, Object>> itemsList = (List<Map<String, Object>>) itemsObj;
            List<TcmFormulaItem> items = new ArrayList<>();
            int order = 1;
            for (Map<String, Object> im : itemsList)
            {
                TcmFormulaItem item = new TcmFormulaItem();
                item.setHerbName(str(im, "herbName"));
                item.setDosage(toBigDecimal(im.get("dosage")));
                item.setUnit(str(im, "unit") != null ? str(im, "unit") : "g");
                item.setSortOrder(im.get("sortOrder") instanceof Number
                        ? ((Number) im.get("sortOrder")).intValue() : order);
                item.setNotes(str(im, "notes"));
                items.add(item);
                order++;
            }
            f.setItems(items);
        }
        return f;
    }

    // ==================== Acupoint ====================

    public static Map<String, Object> flattenAcupoint(TcmAcupoint a)
    {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("name", a.getName());
        m.put("pinyin", a.getPinyin());
        m.put("englishName", a.getEnglishName());
        m.put("meridian", a.getMeridian());
        m.put("location", a.getLocation());
        m.put("indication", a.getIndication());
        m.put("method", a.getMethod());
        m.put("notes", a.getNotes());
        m.put("isActive", intToBool(a.getIsActive(), true));
        m.put("deletedAt", a.getDeletedAt());
        if (a.getCreateTime() != null)
        {
            m.put("createdAt", formatDate(a.getCreateTime()));
        }
        return m;
    }

    public static List<Map<String, Object>> flattenAcupoints(List<TcmAcupoint> list)
    {
        List<Map<String, Object>> result = new ArrayList<>(list.size());
        for (TcmAcupoint a : list) { result.add(flattenAcupoint(a)); }
        return result;
    }

    public static TcmAcupoint toAcupoint(Map<String, Object> m)
    {
        TcmAcupoint a = new TcmAcupoint();
        a.setId(str(m, "id"));
        a.setName(str(m, "name"));
        a.setPinyin(str(m, "pinyin"));
        a.setEnglishName(str(m, "englishName"));
        a.setMeridian(str(m, "meridian"));
        a.setLocation(str(m, "location"));
        a.setIndication(str(m, "indication"));
        a.setMethod(str(m, "method"));
        a.setNotes(str(m, "notes"));
        a.setIsActive(boolToInt(m.get("isActive"), 1));
        a.setDeletedAt(str(m, "deletedAt"));
        return a;
    }

    // ==================== ServiceType ====================

    public static Map<String, Object> flattenServiceType(TcmServiceType st)
    {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", st.getServiceKey());
        m.put("label", st.getLabel());
        m.put("duration", st.getDuration());
        m.put("practitionerTime", st.getPractitionerTime());
        m.put("roomRequired", intToBool(st.getRoomRequired(), false));
        m.put("defaultPrice", st.getDefaultPrice());
        return m;
    }

    // ==================== PriceList ====================

    public static Map<String, Object> flatten(TcmPriceList pl)
    {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", pl.getId());
        m.put("name", pl.getName());
        m.put("effectiveDate", pl.getEffectiveDate());
        m.put("isActive", intToBool(pl.getIsActive(), true));
        // Parse items JSON
        m.put("items", parseJsonArray(pl.getItemsJson()));
        m.put("createdAt", pl.getCreateTime());
        return m;
    }

    public static List<Map<String, Object>> flattenPriceLists(List<TcmPriceList> list)
    {
        List<Map<String, Object>> result = new ArrayList<>(list.size());
        for (TcmPriceList pl : list) { result.add(flatten(pl)); }
        return result;
    }

    // ==================== Helper Methods ====================

    /** Merge parsed payload JSON into the target map */
    private static void mergePayload(Map<String, Object> target, String payloadStr)
    {
        if (payloadStr != null && !payloadStr.isEmpty())
        {
            try
            {
                JSONObject payload = JSON.parseObject(payloadStr);
                if (payload != null)
                {
                    target.putAll(payload);
                }
            }
            catch (Exception e)
            {
                // Ignore parse errors; payload stays as-is in DB
            }
        }
    }

    /** Pack extra (non-DB) fields into a JSON string for the payload column */
    private static String packExtra(Map<String, Object> m, Set<String> dbFields)
    {
        Map<String, Object> extra = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : m.entrySet())
        {
            if (!dbFields.contains(entry.getKey()))
            {
                extra.put(entry.getKey(), entry.getValue());
            }
        }
        return extra.isEmpty() ? null : JSON.toJSONString(extra);
    }

    /** Convert Integer (1/0) to boolean, with default */
    private static boolean intToBool(Integer val, boolean defaultVal)
    {
        if (val == null) { return defaultVal; }
        return val == 1;
    }

    /** Convert boolean/Number/String to Integer 0 or 1 */
    private static Integer boolToInt(Object val, int defaultVal)
    {
        if (val == null) { return defaultVal; }
        if (val instanceof Boolean) { return ((Boolean) val) ? 1 : 0; }
        if (val instanceof Number) { return ((Number) val).intValue(); }
        if ("true".equalsIgnoreCase(String.valueOf(val))) { return 1; }
        if ("false".equalsIgnoreCase(String.valueOf(val))) { return 0; }
        return defaultVal;
    }

    /** Safely get String value from map */
    private static String str(Map<String, Object> m, String key)
    {
        Object v = m.get(key);
        return v != null ? String.valueOf(v) : null;
    }

    /** Convert Object to BigDecimal */
    private static BigDecimal toBigDecimal(Object val)
    {
        if (val == null) { return null; }
        if (val instanceof BigDecimal) { return (BigDecimal) val; }
        try { return new BigDecimal(String.valueOf(val)); }
        catch (Exception e) { return null; }
    }

    /** Format Date to ISO-like string for frontend consumption */
    private static String formatDate(Date date)
    {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        return sdf.format(date);
    }

    /** Parse JSON array string to List, or return empty list */
    private static List<?> parseJsonArray(String jsonStr)
    {
        if (jsonStr != null && !jsonStr.isEmpty())
        {
            try { return JSON.parseArray(jsonStr); }
            catch (Exception e) { /* ignore */ }
        }
        return new ArrayList<>();
    }
}
