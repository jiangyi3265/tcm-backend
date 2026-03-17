package com.ruoyi.hospital.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
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
        for (SysUser u : users)
        {
            if (u.getUserId() >= 100L)
            {
                result.add(toMap(u));
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
        // 检查 email 是否已存在（userName = email）
        SysUser existingUser = userService.selectUserByUserName(email);
        if (existingUser != null)
        {
            throw new ServiceException("该邮箱已被注册: " + email);
        }
        user.setUserName(email);
        user.setNickName((String) body.get("name"));
        user.setEmail(email);
        user.setPhonenumber((String) body.get("phone"));
        String password = requirePassword(body.get("password"));
        user.setPassword(SecurityUtils.encryptPassword(password));
        user.setCreateBy(String.valueOf(SecurityUtils.getUserId()));
        String roleKey = requireRoleKey(body.getOrDefault("role", "practitioner"));
        Long[] roleIds = resolveRoleIds(roleKey);
        user.setRoleIds(roleIds);
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
        if (body.containsKey("role"))
        {
            Long[] roleIds = resolveRoleIds(requireRoleKey(body.get("role")));
            user.setRoleIds(roleIds);
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
        Map<String, Object> r = new HashMap<>();
        r.put("ok", true);
        return r;
    }

    private Long[] resolveRoleIds(String roleKey)
    {
        List<SysRole> roles = roleService.selectRoleAll();
        for (SysRole r : roles)
        {
            if (r.getRoleKey().equals(roleKey))
            {
                return new Long[] { r.getRoleId() };
            }
        }
        throw new ServiceException("无效角色: " + roleKey);
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

    private String requireRoleKey(Object rawRoleKey)
    {
        String roleKey = rawRoleKey != null ? String.valueOf(rawRoleKey).trim() : "";
        if (roleKey.isEmpty())
        {
            throw new ServiceException("角色不能为空");
        }
        return roleKey;
    }

    private Map<String, Object> toMap(SysUser u)
    {
        Map<String, Object> m = new HashMap<>();
        m.put("id", String.valueOf(u.getUserId()));
        m.put("name", u.getNickName());
        m.put("email", u.getEmail());
        m.put("phone", u.getPhonenumber());
        String role = "admin";
        if (u.getRoles() != null && !u.getRoles().isEmpty())
        {
            role = u.getRoles().get(0).getRoleKey();
        }
        m.put("role", role);
        m.put("isActive", "0".equals(u.getStatus()));
        m.put("createdAt", u.getCreateTime());
        return m;
    }
}
