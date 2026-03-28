package com.ruoyi.hospital.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.service.ISysRoleService;
import com.ruoyi.system.service.ISysUserService;

@RestController
@RequestMapping("/api/users")
public class TcmUserController
{
    @Autowired
    private ISysUserService userService;

    @Autowired
    private ISysRoleService roleService;

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
        userService.insertUser(user);
        return toMap(userService.selectUserById(user.getUserId()));
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

        String[] profileKeys = {
            "prescriptionPreference", "regulatoryBody", "title",
            "registrationNumber", "homeAddress", "workingHours"
        };
        if (!isAdmin)
        {
            ensureOnlyProfileUpdate(body, profileKeys);
        }

        SysUser user = userService.selectUserById(id);
        if (user == null)
        {
            throw new ServiceException("用户不存在");
        }

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
            user.setEmail(email);
        }
        if (isAdmin && (body.containsKey("roles") || body.containsKey("role")))
        {
            user.setRoleIds(resolveRoleIdsFromBody(body));
        }

        boolean hasProfileUpdate = false;
        for (String key : profileKeys)
        {
            if (body.containsKey(key))
            {
                hasProfileUpdate = true;
                break;
            }
        }
        if (hasProfileUpdate)
        {
            JSONObject profile = parseProfileJson(user.getRemark());
            String legacyRemark = extractLegacyRemark(user.getRemark());
            if (legacyRemark != null && !profile.containsKey("legacyRemark"))
            {
                profile.put("legacyRemark", legacyRemark);
            }
            for (String key : profileKeys)
            {
                if (body.containsKey(key))
                {
                    applyProfileField(profile, key, body.get(key));
                }
            }
            user.setRemark(profile.toJSONString());
        }

        user.setUpdateBy(String.valueOf(SecurityUtils.getUserId()));
        userService.updateUser(user);
        return toMap(userService.selectUserById(id));
    }

    private void ensureOnlyProfileUpdate(Map<String, Object> body, String[] profileKeys)
    {
        List<String> allowedKeys = new ArrayList<>();
        for (String key : profileKeys)
        {
            allowedKeys.add(key);
        }
        for (String key : body.keySet())
        {
            if (!allowedKeys.contains(key))
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
        result.put("homeAddress", profile.get("homeAddress"));
        result.put("workingHours", profile.get("workingHours"));
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
}
