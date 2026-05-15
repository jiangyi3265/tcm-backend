package com.ruoyi.hospital.controller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.hospital.service.ITcmPatientService;
import com.ruoyi.hospital.util.HospitalFileStorage;
import com.ruoyi.hospital.util.SignedFileUrlService;
import com.ruoyi.system.service.ISysRoleService;
import com.ruoyi.system.service.ISysUserService;

@RestController
@RequestMapping("/api/users")
public class TcmUserController
{
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Shanghai");
    private static final List<String> PROFILE_KEYS = Arrays.asList(
            "prescriptionPreference", "regulatoryBody", "title",
            "registrationNumber", "organization", "organizationNumber",
            "homeAddress", "workingHours",
            "practitionerSortOrder", "serviceKeys", "internshipDates",
            "internshipSessions", "color", "overlap1", "overlap2", "dripEnabled", "signature");
    private static final List<String> SELF_EDITABLE_PROFILE_KEYS = Arrays.asList(
            "prescriptionPreference", "regulatoryBody", "title",
            "registrationNumber", "organization", "organizationNumber",
            "homeAddress", "workingHours",
            "color", "dripEnabled", "signature");

    @Autowired
    private ISysUserService userService;

    @Autowired
    private ISysRoleService roleService;

    @Autowired
    private ITcmPatientService patientService;

    @Autowired
    private HospitalFileStorage hospitalFileStorage;

    @Autowired
    private SignedFileUrlService signedFileUrlService;

    @PreAuthorize("@ss.hasRole('admin')")
    @GetMapping("")
    public List<Map<String, Object>> list()
    {
        SysUser query = new SysUser();
        List<SysUser> users = userService.selectUserList(query);
        List<Map<String, Object>> result = new ArrayList<>();
        for (SysUser user : users)
        {
            if (user.getUserId() >= 100L)
            {
                result.add(toMap(user));
            }
        }
        return result;
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @PostMapping("")
    public Map<String, Object> create(@RequestBody Map<String, Object> body)
    {
        SysUser user = new SysUser();
        String email = validateOptionalLength((String) body.get("email"), "邮箱", 50);
        if (email == null || email.trim().isEmpty())
        {
            throw new ServiceException("邮箱不能为空");
        }
        SysUser existingUser = userService.selectUserByUserName(email);
        if (existingUser != null)
        {
            throw new ServiceException("该邮箱已被注册: " + email);
        }

        user.setUserName(email);
        user.setNickName(validateRequiredLength((String) body.get("name"), "姓名", 30));
        user.setEmail(email);
        user.setPhonenumber(validateOptionalLength((String) body.get("phone"), "电话", 11));
        user.setPassword(SecurityUtils.encryptPassword(requirePassword(body.get("password"))));
        user.setCreateBy(String.valueOf(SecurityUtils.getUserId()));
        user.setRoleIds(resolveRoleIdsFromBody(body));
        applyProfileUpdates(user, body, false);
        userService.insertUser(user);
        SysUser created = userService.selectUserById(user.getUserId());
        syncStaffPatient(created);
        return toMap(created);
    }

    @PreAuthorize("@ss.hasAnyRoles('admin,practitioner,apprentice,cashier,pharmacist')")
    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody Map<String, Object> body)
    {
        boolean isAdmin = SecurityUtils.hasRole("admin");
        boolean isSelf = id.equals(SecurityUtils.getUserId());
        if (!isAdmin && !isSelf)
        {
            throw new ServiceException("无权修改其他用户");
        }

        if (!isAdmin)
        {
            ensureOnlyProfileUpdate(body, SELF_EDITABLE_PROFILE_KEYS);
        }

        SysUser user = userService.selectUserById(id);
        if (user == null)
        {
            throw new ServiceException("用户不存在");
        }
        boolean willOverrideRoles = isAdmin && (body.containsKey("roles") || body.containsKey("role"));

        if (body.containsKey("name"))
        {
            user.setNickName(validateRequiredLength((String) body.get("name"), "姓名", 30));
        }
        if (body.containsKey("phone"))
        {
            user.setPhonenumber(validateOptionalLength((String) body.get("phone"), "电话", 11));
        }
        if (body.containsKey("email"))
        {
            String email = validateOptionalLength((String) body.get("email"), "邮箱", 50);
            if (email == null || email.trim().isEmpty())
            {
                throw new ServiceException("邮箱不能为空");
            }
            SysUser existingUser = userService.selectUserByUserName(email);
            if (existingUser != null && !id.equals(existingUser.getUserId()))
            {
                throw new ServiceException("该邮箱已被注册: " + email);
            }
            user.setEmail(email);
            user.setUserName(email);
        }
        if (willOverrideRoles)
        {
            user.setRoleIds(resolveRoleIdsFromBody(body));
        }

        boolean hasProfileUpdate = applyProfileUpdates(user, body, true);

        user.setUpdateBy(String.valueOf(SecurityUtils.getUserId()));
        if (willOverrideRoles)
        {
            userService.updateUser(user);
        }
        else
        {
            userService.updateUserProfile(user);
        }
        SysUser updated = userService.selectUserById(id);
        syncStaffPatient(updated);
        return toMap(updated);
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @PostMapping("/{id}/internship-today")
    public Map<String, Object> addTodayInternship(@PathVariable Long id)
    {
        SysUser user = userService.selectUserById(id);
        if (user == null)
        {
            throw new ServiceException("用户不存在");
        }
        JSONObject profile = parseProfileJson(user.getRemark());
        String legacyRemark = extractLegacyRemark(user.getRemark());
        if (legacyRemark != null && !profile.containsKey("legacyRemark"))
        {
            profile.put("legacyRemark", legacyRemark);
        }
        List<String> internshipDates = sanitizeDateList(profile.get("internshipDates"));
        String today = LocalDate.now(CLINIC_ZONE).toString();
        internshipDates.add(today);
        profile.put("internshipDates", new ArrayList<>(new TreeSet<>(internshipDates)));
        List<Map<String, Object>> sessions = normalizeInternshipSessions(profile.get("internshipSessions"));
        boolean exists = sessions.stream().anyMatch(item -> today.equals(String.valueOf(item.get("date"))));
        if (!exists)
        {
            Map<String, Object> session = new LinkedHashMap<>();
            session.put("date", today);
            session.put("loginTime", "");
            session.put("logoutTime", "");
            session.put("teacher", "");
            sessions.add(session);
            profile.put("internshipSessions", sessions);
        }
        user.setRemark(profile.toJSONString());
        user.setUpdateBy(String.valueOf(SecurityUtils.getUserId()));
        userService.updateUserProfile(user);
        SysUser updated = userService.selectUserById(id);
        syncStaffPatient(updated);
        return toMap(updated);
    }

    @PreAuthorize("@ss.hasRole('apprentice')")
    @PostMapping("/me/apprentice-session")
    public Map<String, Object> recordApprenticeSession(@RequestBody Map<String, Object> body)
    {
        Long userId = SecurityUtils.getUserId();
        SysUser user = userService.selectUserById(userId);
        if (user == null)
        {
            throw new ServiceException("用户不存在");
        }
        String action = body != null && body.get("action") != null
                ? String.valueOf(body.get("action")).trim().toLowerCase()
                : "login";
        if (!"login".equals(action) && !"logout".equals(action))
        {
            throw new ServiceException("无效跟诊记录动作");
        }

        JSONObject profile = parseProfileJson(user.getRemark());
        String legacyRemark = extractLegacyRemark(user.getRemark());
        if (legacyRemark != null && !profile.containsKey("legacyRemark"))
        {
            profile.put("legacyRemark", legacyRemark);
        }
        String today = LocalDate.now(CLINIC_ZONE).toString();
        String now = LocalTime.now(CLINIC_ZONE).format(DateTimeFormatter.ofPattern("HH:mm"));
        List<String> internshipDates = sanitizeDateList(profile.get("internshipDates"));
        internshipDates.add(today);
        profile.put("internshipDates", new ArrayList<>(new TreeSet<>(internshipDates)));

        List<Map<String, Object>> sessions = normalizeInternshipSessions(profile.get("internshipSessions"));
        Map<String, Object> target = null;
        for (int i = sessions.size() - 1; i >= 0; i--)
        {
            Map<String, Object> session = sessions.get(i);
            if (today.equals(String.valueOf(session.get("date")))
                    && isBlank(String.valueOf(session.get("logoutTime"))))
            {
                target = session;
                break;
            }
        }
        if (target == null)
        {
            target = new LinkedHashMap<>();
            target.put("date", today);
            target.put("loginTime", "");
            target.put("logoutTime", "");
            target.put("teacher", "");
            sessions.add(target);
        }
        if ("login".equals(action) && isBlank(String.valueOf(target.get("loginTime"))))
        {
            target.put("loginTime", now);
        }
        if ("logout".equals(action))
        {
            target.put("logoutTime", now);
        }
        profile.put("internshipSessions", sessions);
        user.setRemark(profile.toJSONString());
        user.setUpdateBy(String.valueOf(userId));
        userService.updateUserProfile(user);
        return toMap(userService.selectUserById(userId));
    }

    @PreAuthorize("@ss.hasAnyRoles('admin,practitioner,doctor')")
    @PostMapping("/{id}/signature/upload")
    public Map<String, Object> uploadPractitionerSignature(@PathVariable Long id,
            @RequestParam("file") MultipartFile file)
    {
        boolean isAdmin = SecurityUtils.hasRole("admin");
        boolean isSelf = id.equals(SecurityUtils.getUserId());
        if (!isAdmin && !isSelf)
        {
            throw new ServiceException("无权修改其他用户签名");
        }
        SysUser user = userService.selectUserById(id);
        if (user == null)
        {
            throw new ServiceException("用户不存在");
        }
        if (file == null || file.isEmpty())
        {
            throw new ServiceException("签名文件不能为空");
        }
        String contentType = file.getContentType();
        String filename = file.getOriginalFilename();
        boolean png = (contentType != null && "image/png".equalsIgnoreCase(contentType))
                || (filename != null && filename.toLowerCase().endsWith(".png"));
        if (!png)
        {
            throw new ServiceException("只支持上传 PNG 签名");
        }
        try
        {
            String resource = hospitalFileStorage.store(file, "practitioner_signature");
            Map<String, Object> signature = new LinkedHashMap<>();
            signature.put("path", resource);
            signature.put("url", signedFileUrlService.buildAccessUrl(resource));
            signature.put("uploadedAt", LocalDateTime.now().toString());

            JSONObject profile = parseProfileJson(user.getRemark());
            String legacyRemark = extractLegacyRemark(user.getRemark());
            if (legacyRemark != null && !profile.containsKey("legacyRemark"))
            {
                profile.put("legacyRemark", legacyRemark);
            }
            profile.put("signature", signature);
            user.setRemark(profile.toJSONString());
            user.setUpdateBy(String.valueOf(SecurityUtils.getUserId()));
            userService.updateUserProfile(user);
            SysUser updated = userService.selectUserById(id);
            syncStaffPatient(updated);
            return toMap(updated);
        }
        catch (Exception e)
        {
            throw new ServiceException("签名上传失败: " + e.getMessage());
        }
    }

    private void ensureOnlyProfileUpdate(Map<String, Object> body, List<String> profileKeys)
    {
        for (String key : body.keySet())
        {
            if (!profileKeys.contains(key))
            {
                throw new ServiceException("当前账号只能修改个人资料字段");
            }
        }
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @DeleteMapping("/{id}")
    public Map<String, Object> remove(@PathVariable Long id)
    {
        if (id.equals(SecurityUtils.getUserId()))
        {
            throw new ServiceException("不能删除当前登录账号");
        }
        userService.deleteUserById(id);
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        return result;
    }

    private Long[] resolveRoleIds(String roleKey)
    {
        List<SysRole> allRoles = roleService.selectRoleAll();
        for (SysRole role : allRoles)
        {
            if (role.getRoleKey().equals(roleKey))
            {
                return new Long[] { role.getRoleId() };
            }
        }
        throw new ServiceException("无效角色: " + roleKey);
    }

    @SuppressWarnings("unchecked")
    private Long[] resolveRoleIdsFromBody(Map<String, Object> body)
    {
        Object rolesObj = body.get("roles");
        if (rolesObj instanceof List)
        {
            List<String> roleKeys = (List<String>) rolesObj;
            if (!roleKeys.isEmpty())
            {
                List<Long> ids = new ArrayList<>();
                for (String key : roleKeys)
                {
                    Long[] resolved = resolveRoleIds(key);
                    for (Long roleId : resolved)
                    {
                        if (!ids.contains(roleId))
                        {
                            ids.add(roleId);
                        }
                    }
                }
                return ids.toArray(new Long[0]);
            }
        }

        String roleKey = body.get("role") != null
                ? String.valueOf(body.get("role")).trim()
                : "practitioner";
        return resolveRoleIds(roleKey);
    }

    private String requirePassword(Object rawPassword)
    {
        String password = rawPassword != null ? String.valueOf(rawPassword).trim() : "";
        if (password.length() < 8)
        {
            throw new ServiceException("密码不能少于8位");
        }
        return password;
    }

    private String validateRequiredLength(String value, String fieldName, int maxLength)
    {
        String trimmed = validateOptionalLength(value, fieldName, maxLength);
        if (trimmed == null || trimmed.isEmpty())
        {
            throw new ServiceException(fieldName + "不能为空");
        }
        return trimmed;
    }

    private String validateOptionalLength(String value, String fieldName, int maxLength)
    {
        if (value == null)
        {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength)
        {
            throw new ServiceException(fieldName + "长度不能超过" + maxLength + "位");
        }
        return trimmed;
    }

    private JSONObject parseProfileJson(String remark)
    {
        if (remark == null || remark.trim().isEmpty())
        {
            return new JSONObject();
        }
        String trimmed = remark.trim();
        if (!trimmed.startsWith("{"))
        {
            return new JSONObject();
        }
        try
        {
            JSONObject profile = JSON.parseObject(trimmed);
            return profile != null ? profile : new JSONObject();
        }
        catch (Exception e)
        {
            return new JSONObject();
        }
    }

    private String extractLegacyRemark(String remark)
    {
        if (remark == null || remark.trim().isEmpty())
        {
            return null;
        }
        String trimmed = remark.trim();
        if (!trimmed.startsWith("{"))
        {
            return trimmed;
        }
        try
        {
            JSON.parseObject(trimmed);
            return null;
        }
        catch (Exception e)
        {
            return trimmed;
        }
    }

    private void applyProfileField(JSONObject profile, String key, Object value)
    {
        if ("workingHours".equals(key))
        {
            Map<String, Object> workingHours = normalizeWorkingHours(value);
            if (workingHours.isEmpty())
            {
                profile.remove(key);
            }
            else
            {
                profile.put(key, workingHours);
            }
            return;
        }
        if ("practitionerSortOrder".equals(key))
        {
            Integer sortOrder = sanitizeInteger(value);
            if (sortOrder == null)
            {
                profile.remove(key);
            }
            else
            {
                profile.put(key, sortOrder);
            }
            return;
        }
        if ("serviceKeys".equals(key))
        {
            List<String> serviceKeys = sanitizeStringList(value);
            if (serviceKeys.isEmpty())
            {
                profile.remove(key);
            }
            else
            {
                profile.put(key, serviceKeys);
            }
            return;
        }
        if ("internshipDates".equals(key))
        {
            List<String> internshipDates = sanitizeDateList(value);
            if (internshipDates.isEmpty())
            {
                profile.remove(key);
            }
            else
            {
                profile.put(key, internshipDates);
            }
            return;
        }
        if ("overlap1".equals(key) || "overlap2".equals(key))
        {
            Integer minutes = sanitizeInteger(value);
            if (minutes == null || minutes <= 0)
            {
                profile.remove(key);
            }
            else
            {
                profile.put(key, minutes);
            }
            return;
        }
        if ("color".equals(key))
        {
            String color = value == null ? "" : String.valueOf(value).trim();
            if (color.isEmpty())
            {
                profile.remove(key);
            }
            else
            {
                if (!color.matches("^#[0-9a-fA-F]{3,8}$"))
                {
                    throw new ServiceException("无效颜色值: " + color);
                }
                profile.put(key, color);
            }
            return;
        }
        if ("prescriptionPreference".equals(key))
        {
            String preference = sanitizePrescriptionPreference(value);
            String rawValue = value == null ? "" : String.valueOf(value).trim();
            if (!rawValue.isEmpty() && preference == null)
            {
                throw new ServiceException("无效处方偏好");
            }
            if (preference == null)
            {
                profile.remove(key);
            }
            else
            {
                profile.put(key, preference);
            }
            return;
        }
        if (value == null)
        {
            profile.remove(key);
            return;
        }
        if (value instanceof String && String.valueOf(value).trim().isEmpty())
        {
            profile.remove(key);
            return;
        }
        profile.put(key, value);
    }

    private String sanitizePrescriptionPreference(Object value)
    {
        if (value == null)
        {
            return null;
        }
        String preference = String.valueOf(value).trim();
        if (preference.isEmpty())
        {
            return null;
        }
        if ("powder".equals(preference) || "raw_herbs".equals(preference) || "pills".equals(preference))
        {
            return preference;
        }
        return null;
    }

    private Map<String, Object> toMap(SysUser user)
    {
        Map<String, Object> result = new HashMap<>();
        result.put("id", String.valueOf(user.getUserId()));
        result.put("name", user.getNickName());
        result.put("email", user.getEmail());
        result.put("phone", user.getPhonenumber());

        List<String> roleKeys = resolveRoleKeys(user);
        result.put("role", roleKeys.isEmpty() ? null : roleKeys.get(0));
        result.put("roles", roleKeys);
        result.put("isActive", "0".equals(user.getStatus()));
        result.put("createdAt", user.getCreateTime());

        JSONObject profile = parseProfileJson(user.getRemark());
        result.put("prescriptionPreference", sanitizePrescriptionPreference(profile.get("prescriptionPreference")));
        result.put("regulatoryBody", profile.getString("regulatoryBody"));
        result.put("title", profile.getString("title"));
        result.put("registrationNumber", profile.getString("registrationNumber"));
        result.put("organization", profile.getString("organization"));
        result.put("organizationNumber", profile.getString("organizationNumber"));
        result.put("homeAddress", profile.get("homeAddress"));
        result.put("workingHours", normalizeWorkingHours(profile.get("workingHours")));
        result.put("practitionerSortOrder", sanitizeInteger(profile.get("practitionerSortOrder")));
        result.put("serviceKeys", sanitizeStringList(profile.get("serviceKeys")));
        result.put("internshipDates", sanitizeDateList(profile.get("internshipDates")));
        result.put("internshipSessions", normalizeInternshipSessions(profile.get("internshipSessions")));
        result.put("color", profile.getString("color"));
        result.put("overlap1", sanitizeInteger(profile.get("overlap1")));
        result.put("overlap2", sanitizeInteger(profile.get("overlap2")));
        result.put("dripEnabled", profile.getBooleanValue("dripEnabled", true));
        result.put("signature", profile.get("signature"));
        return result;
    }

    private List<String> resolveRoleKeys(SysUser user)
    {
        List<String> roleKeys = new ArrayList<>();
        List<SysRole> roles = user.getRoles();
        boolean usingEmbeddedRoles = roles != null && !roles.isEmpty();
        if (!usingEmbeddedRoles)
        {
            roles = roleService.selectRolesByUserId(user.getUserId());
        }
        if (roles == null)
        {
            return roleKeys;
        }
        for (SysRole role : roles)
        {
            if (role == null || role.getRoleKey() == null || role.getRoleKey().trim().isEmpty())
            {
                continue;
            }
            if (usingEmbeddedRoles || role.isFlag())
            {
                roleKeys.add(role.getRoleKey());
            }
        }
        return roleKeys;
    }

    private boolean applyProfileUpdates(SysUser user, Map<String, Object> body, boolean preserveLegacyRemark)
    {
        boolean hasProfileUpdate = false;
        for (String key : PROFILE_KEYS)
        {
            if (body.containsKey(key))
            {
                hasProfileUpdate = true;
                break;
            }
        }
        if (!hasProfileUpdate)
        {
            return false;
        }

        JSONObject profile = parseProfileJson(user.getRemark());
        if (preserveLegacyRemark)
        {
            String legacyRemark = extractLegacyRemark(user.getRemark());
            if (legacyRemark != null && !profile.containsKey("legacyRemark"))
            {
                profile.put("legacyRemark", legacyRemark);
            }
        }
        for (String key : PROFILE_KEYS)
        {
            if (body.containsKey(key))
            {
                applyProfileField(profile, key, body.get(key));
            }
        }
        user.setRemark(profile.toJSONString());
        return true;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeWorkingHours(Object value)
    {
        Map<String, Object> normalized = new LinkedHashMap<>();
        if (value == null)
        {
            return normalized;
        }
        Map<String, Object> rawWorkingHours = coerceWorkingHoursMap(value);
        for (Map.Entry<String, Object> entry : rawWorkingHours.entrySet())
        {
            List<Map<String, String>> normalizedRanges = normalizeWorkingHourRanges(entry.getKey(), entry.getValue());
            if (!normalizedRanges.isEmpty())
            {
                normalized.put(entry.getKey(), normalizedRanges);
            }
        }
        return normalized;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> coerceWorkingHoursMap(Object value)
    {
        if (value instanceof Map)
        {
            return (Map<String, Object>) value;
        }
        if (value instanceof String)
        {
            String raw = String.valueOf(value).trim();
            if (raw.isEmpty())
            {
                return new LinkedHashMap<>();
            }
            try
            {
                JSONObject parsed = JSON.parseObject(raw);
                if (parsed == null)
                {
                    throw new ServiceException("工作时间格式无效");
                }
                return parsed;
            }
            catch (ServiceException e)
            {
                throw e;
            }
            catch (Exception e)
            {
                throw new ServiceException("工作时间格式无效");
            }
        }
        throw new ServiceException("工作时间格式无效");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> normalizeWorkingHourRanges(String dayKey, Object value)
    {
        List<Map<String, String>> normalized = new ArrayList<>();
        if (value == null)
        {
            return normalized;
        }
        if (value instanceof Map)
        {
            Map<String, Object> singleRange = (Map<String, Object>) value;
            normalized.add(normalizeWorkingHourRange(dayKey, singleRange));
            validateWorkingHourRanges(dayKey, normalized);
            return normalized;
        }
        if (value instanceof String)
        {
            String raw = String.valueOf(value).trim();
            if (raw.isEmpty())
            {
                return normalized;
            }
            try
            {
                Object parsed = JSON.parse(raw);
                return normalizeWorkingHourRanges(dayKey, parsed);
            }
            catch (ServiceException e)
            {
                throw e;
            }
            catch (Exception e)
            {
                throw new ServiceException("工作时间格式无效");
            }
        }
        if (!(value instanceof List))
        {
            throw new ServiceException("工作时间格式无效");
        }
        for (Object item : (List<?>) value)
        {
            if (item == null)
            {
                throw new ServiceException("工作时间格式无效");
            }
            if (item instanceof Map)
            {
                normalized.add(normalizeWorkingHourRange(dayKey, (Map<String, Object>) item));
                continue;
            }
            if (item instanceof String)
            {
                String raw = String.valueOf(item).trim();
                if (raw.isEmpty())
                {
                    throw new ServiceException("工作时间格式无效");
                }
                try
                {
                    Object parsed = JSON.parse(raw);
                    if (!(parsed instanceof Map))
                    {
                        throw new ServiceException("工作时间格式无效");
                    }
                    normalized.add(normalizeWorkingHourRange(dayKey, (Map<String, Object>) parsed));
                    continue;
                }
                catch (ServiceException e)
                {
                    throw e;
                }
                catch (Exception e)
                {
                    throw new ServiceException("工作时间格式无效");
                }
            }
            throw new ServiceException("工作时间格式无效");
        }
        validateWorkingHourRanges(dayKey, normalized);
        return normalized;
    }

    private void validateWorkingHourRanges(String dayKey, List<Map<String, String>> ranges)
    {
        if (ranges.size() <= 1)
        {
            return;
        }
        ranges.sort((left, right) -> left.get("start").compareTo(right.get("start")));
        String previousEnd = null;
        for (Map<String, String> range : ranges)
        {
            String start = range.get("start");
            String end = range.get("end");
            if (previousEnd != null && start.compareTo(previousEnd) < 0)
            {
                throw new ServiceException("工作时间区间不能重叠: " + dayKey);
            }
            previousEnd = end;
        }
    }

    private Map<String, String> normalizeWorkingHourRange(String dayKey, Map<String, Object> rawRange)
    {
        Map<String, String> range = new LinkedHashMap<>();
        if (rawRange == null)
        {
            throw new ServiceException("工作时间格式无效: " + dayKey);
        }
        String start = normalizeTimeValue(rawRange.get("start"));
        String end = normalizeTimeValue(rawRange.get("end"));
        if (start == null || end == null)
        {
            throw new ServiceException("工作时间格式无效: " + dayKey);
        }
        if (start.compareTo(end) >= 0)
        {
            throw new ServiceException("工作时间开始时间必须早于结束时间: " + dayKey);
        }
        range.put("start", start);
        range.put("end", end);
        return range;
    }

    private String normalizeTimeValue(Object value)
    {
        if (value == null)
        {
            throw new ServiceException("工作时间时间不能为空");
        }
        String time = String.valueOf(value).trim();
        if (time.isEmpty())
        {
            throw new ServiceException("工作时间时间不能为空");
        }
        String normalized = time;
        if (time.matches("^\\d{2}:\\d{2}:\\d{2}$"))
        {
            normalized = time.substring(0, 5);
        }
        else if (!time.matches("^\\d{2}:\\d{2}$"))
        {
            throw new ServiceException("工作时间时间格式无效: " + time);
        }
        try
        {
            LocalTime parsed = LocalTime.parse(normalized, DateTimeFormatter.ofPattern("HH:mm"));
            if (time.matches("^\\d{2}:\\d{2}:\\d{2}$") && !"00".equals(time.substring(6, 8)))
            {
                throw new ServiceException("工作时间必须按10分钟粒度设置: " + normalized);
            }
            int minute = parsed.getMinute();
            if (minute % 10 != 0)
            {
                throw new ServiceException("工作时间必须按10分钟粒度设置: " + normalized);
            }
            return parsed.format(DateTimeFormatter.ofPattern("HH:mm"));
        }
        catch (ServiceException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new ServiceException("工作时间时间格式无效: " + time);
        }
    }

    private Integer sanitizeInteger(Object value)
    {
        if (value == null)
        {
            return null;
        }
        String raw = String.valueOf(value).trim();
        if (raw.isEmpty())
        {
            return null;
        }
        try
        {
            return Integer.parseInt(raw);
        }
        catch (NumberFormatException e)
        {
            throw new ServiceException("无效数字: " + raw);
        }
    }

    private List<String> sanitizeStringList(Object value)
    {
        List<String> items = new ArrayList<>();
        if (!(value instanceof List))
        {
            return items;
        }
        for (Object item : (List<?>) value)
        {
            if (item == null)
            {
                continue;
            }
            String normalized = String.valueOf(item).trim();
            if (!normalized.isEmpty() && !items.contains(normalized))
            {
                items.add(normalized);
            }
        }
        return items;
    }

    private List<String> sanitizeDateList(Object value)
    {
        TreeSet<String> dates = new TreeSet<>();
        if (!(value instanceof List))
        {
            return new ArrayList<>();
        }
        for (Object item : (List<?>) value)
        {
            if (item == null)
            {
                continue;
            }
            String raw = String.valueOf(item).trim();
            if (raw.isEmpty())
            {
                continue;
            }
            try
            {
                dates.add(LocalDate.parse(raw).toString());
            }
            catch (Exception e)
            {
                throw new ServiceException("无效实习日期: " + raw);
            }
        }
        return new ArrayList<>(dates);
    }

    private List<Map<String, Object>> normalizeInternshipSessions(Object value)
    {
        List<Map<String, Object>> sessions = new ArrayList<>();
        if (!(value instanceof List))
        {
            return sessions;
        }
        for (Object item : (List<?>) value)
        {
            Map<String, Object> session = readSession(item);
            String date = String.valueOf(session.getOrDefault("date", "")).trim();
            if (date.isEmpty())
            {
                continue;
            }
            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("date", date);
            normalized.put("loginTime", String.valueOf(session.getOrDefault("loginTime", "")).trim());
            normalized.put("logoutTime", String.valueOf(session.getOrDefault("logoutTime", "")).trim());
            normalized.put("teacher", String.valueOf(session.getOrDefault("teacher", "")).trim());
            sessions.add(normalized);
        }
        sessions.sort((a, b) -> String.valueOf(a.get("date")).compareTo(String.valueOf(b.get("date"))));
        return sessions;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readSession(Object item)
    {
        if (item instanceof JSONObject)
        {
            return (JSONObject) item;
        }
        if (item instanceof Map)
        {
            return (Map<String, Object>) item;
        }
        return new LinkedHashMap<>();
    }

    private boolean isBlank(String value)
    {
        return value == null || value.trim().isEmpty() || "null".equalsIgnoreCase(value.trim());
    }

    private void syncStaffPatient(SysUser user)
    {
        if (user == null || user.getUserId() == null)
        {
            return;
        }
        patientService.ensureStaffPatientProfile(
                user.getUserId(),
                user.getNickName(),
                user.getEmail(),
                user.getPhonenumber());
    }
}
