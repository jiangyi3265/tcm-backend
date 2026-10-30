package com.ruoyi.hospital.util;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.ruoyi.hospital.domain.TcmPatientFile;

/** Removes only references to explicitly deleted files, never unindexed legacy attachments. */
public final class PatientFileReferences
{
    private static final Set<String> RESOURCE_FIELDS = Set.of("resource", "filePath", "path", "url",
            "tongueImage", "tongueImageResource", "invoicePdfPath", "invoicePdfUrl", "reportPdfPath",
            "reportPdfUrl", "consultationPdfPath", "consultationPdfUrl", "consentPdfPath", "consentPdfUrl");

    private PatientFileReferences() { }

    public static boolean removeDeleted(Map<String, Object> payload, Collection<TcmPatientFile> deleted)
    {
        if (payload == null || deleted == null || deleted.isEmpty()) return false;
        Set<String> paths = new HashSet<>();
        Set<String> ids = new HashSet<>();
        for (TcmPatientFile file : deleted)
        {
            if (file.getId() != null) ids.add(String.valueOf(file.getId()));
            String path = resource(file.getFilePath());
            if (path != null) paths.add(path);
        }
        return prune(payload, false, paths, ids);
    }

    @SuppressWarnings("unchecked")
    private static boolean prune(Object value, boolean documentCollection, Set<String> paths, Set<String> ids)
    {
        boolean changed = false;
        if (value instanceof Map<?, ?>)
        {
            Iterator<Map.Entry<String, Object>> entries = ((Map<String, Object>) value).entrySet().iterator();
            while (entries.hasNext())
            {
                Map.Entry<String, Object> entry = entries.next();
                if (RESOURCE_FIELDS.contains(entry.getKey()) && matchesResource(entry.getValue(), paths))
                {
                    entries.remove();
                    changed = true;
                }
                else
                {
                    changed |= prune(entry.getValue(), "documents".equals(entry.getKey()) || "attachments".equals(entry.getKey()), paths, ids);
                }
            }
        }
        else if (value instanceof List<?>)
        {
            Iterator<?> items = ((List<?>) value).iterator();
            while (items.hasNext())
            {
                Object item = items.next();
                if (documentCollection && item instanceof Map<?, ?> && matchesFile((Map<?, ?>) item, true, paths, ids))
                {
                    items.remove();
                    changed = true;
                }
                else if (documentCollection && matchesResource(item, paths))
                {
                    items.remove();
                    changed = true;
                }
                else changed |= prune(item, documentCollection, paths, ids);
            }
        }
        return changed;
    }

    private static boolean matchesFile(Map<?, ?> item, boolean documentCollection, Set<String> paths, Set<String> ids)
    {
        // Numeric IDs are meaningful only inside attachment collections, never in prescriptions.
        if (documentCollection && item.get("id") != null && ids.contains(String.valueOf(item.get("id")))) return true;
        for (String key : new String[] {"resource", "filePath", "path", "url"})
            if (matchesResource(item.get(key), paths)) return true;
        return false;
    }

    private static boolean matchesResource(Object value, Set<String> paths)
    {
        return value instanceof String && paths.contains(resource((String) value));
    }

    public static String resource(String raw)
    {
        if (raw == null) return null;
        String value = raw.trim().replace('\\', '/');
        int query = value.indexOf('?');
        if (query >= 0)
        {
            String found = null;
            for (String pair : value.substring(query + 1).split("&"))
            {
                if (pair.startsWith("resource="))
                {
                    try { found = URLDecoder.decode(pair.substring(9), StandardCharsets.UTF_8); }
                    catch (IllegalArgumentException invalid) { return null; }
                    break;
                }
            }
            if (found == null) return null;
            value = found;
        }
        if (value.startsWith("/")) value = value.substring(1);
        return value.startsWith("hospital-private/") || value.startsWith("profile/") ? value : null;
    }
}
