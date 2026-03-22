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
        String email = (String) body.get("email");
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
        user.setNickName((String) body.get("name"));
        user.setEmail(email);
        user.setPhonenumber((String) body.get("phone"));
        user.setPassword(SecurityUtils.encryptPassword(requirePassword(body.get("password"))));
        user.setCreateBy(String.valueOf(SecurityUtils.getUserId()));
        user.setRoleIds(resolveRoleIdsFromBody(body));
        userService.insertUser(user);
        return toMap(userService.selectUserById(user.getUserId()));
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody Map<String, Object> body)
    {
        SysUser user = userService.selectUserById(id);
        if (user == null)
        {
            throw new ServiceException("用户不存在");
        }

        if (body.containsKey("name"))
        {
            user.setNickName((String) body.get("name"));
        }
        if (body.containsKey("phone"))
        {
            user.setPhonenumber((String) body.get("phone"));
        }
        if (body.containsKey("email"))
        {
            user.setEmail((String) body.get("email"));
        }
        if (body.containsKey("roles") || body.containsKey("role"))
        {
            user.setRoleIds(resolveRoleIdsFromBody(body));
        }

        String[] profileKeys = {
            "prescriptionPreference", "regulatoryBody", "title",
            "registrationNumber", "homeAddress", "workingHours"
        };
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
            for (String key : profileKeys)
            {
                if (body.containsKey(key))
                {
                    profile.put(key, body.get(key));
                }
            }
            user.setRemark(profile.toJSONString());
        }

        user.setUpdateBy(String.valueOf(SecurityUtils.getUserId()));
        userService.updateUser(user);
        return toMap(userService.selectUserById(id));
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

    private JSONObject parseProfileJson(String remark)
    {
        if (remark == null || remark.trim().isEmpty())
        {
            return new JSONObject();
        }
        String trimmed = remark.trim();
        if (trimmed.startsWith("{"))
        {
            try
            {
                return JSON.parseObject(trimmed);
            }
            catch (Exception e)
            {
                return new JSONObject();
            }
        }
        JSONObject profile = new JSONObject();
        profile.put("prescriptionPreference", trimmed);
        return profile;
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
        result.put("prescriptionPreference", profile.getString("prescriptionPreference"));
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
