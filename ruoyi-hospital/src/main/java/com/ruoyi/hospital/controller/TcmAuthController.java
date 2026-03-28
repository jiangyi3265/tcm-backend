package com.ruoyi.hospital.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.core.domain.entity.SysRole;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.framework.security.context.AuthenticationContextHolder;
import com.ruoyi.framework.web.service.TokenService;
import com.ruoyi.hospital.service.ITcmAuditLogService;
import com.ruoyi.system.service.ISysUserService;

@RestController
@RequestMapping("/api/auth")
public class TcmAuthController
{
    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ITcmAuditLogService auditLogService;

    @Autowired
    private ISysUserService userService;

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, Object> body)
    {
        String email = (String) body.get("email");
        String password = (String) body.get("password");
        if (email == null || password == null)
        {
            throw new ServiceException("邮箱和密码不能为空");
        }
        // user_name = email in our demo users
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(email, password);
        Authentication authentication;
        try
        {
            authentication = authenticate(authenticationToken);
        }
        catch (AuthenticationException ex)
        {
            throw new ServiceException("邮箱或密码错误");
        }
        LoginUser loginUser = (LoginUser) authentication.getPrincipal();

        String token = tokenService.createToken(loginUser);
        SysUser sysUser = loginUser.getUser();
        auditLogService.log("user", String.valueOf(sysUser.getUserId()),
                sysUser.getNickName() != null ? sysUser.getNickName() : sysUser.getUserName(),
                "LOGIN", String.valueOf(sysUser.getUserId()), "用户登录成功");

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("user", buildUserMap(sysUser));
        return result;
    }

    @GetMapping("/me")
    public Map<String, Object> me()
    {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        Map<String, Object> result = new HashMap<>();
        result.put("user", buildUserMap(loginUser.getUser()));
        return result;
    }

    @PostMapping("/change-password")
    public Map<String, Object> changePassword(@RequestBody Map<String, Object> body)
    {
        String oldPassword = (String) body.get("oldPassword");
        String newPassword = (String) body.get("newPassword");
        if (oldPassword == null || newPassword == null)
        {
            throw new ServiceException("旧密码和新密码不能为空");
        }
        if (newPassword.length() < 8)
        {
            throw new ServiceException("新密码长度不能少于8位");
        }
        LoginUser loginUser = SecurityUtils.getLoginUser();
        SysUser currentUser = userService.selectUserById(loginUser.getUserId());
        if (currentUser == null)
        {
            throw new ServiceException("用户不存在");
        }
        if (!SecurityUtils.matchesPassword(oldPassword, currentUser.getPassword()))
        {
            throw new ServiceException("旧密码错误");
        }
        currentUser.setPassword(SecurityUtils.encryptPassword(newPassword));
        userService.resetPwd(currentUser);
        auditLogService.log("user", String.valueOf(currentUser.getUserId()),
                currentUser.getNickName() != null ? currentUser.getNickName() : currentUser.getUserName(),
                "PASSWORD_CHANGE", String.valueOf(currentUser.getUserId()), "用户修改密码");
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return result;
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @PostMapping("/reset-password")
    public Map<String, Object> resetPassword(@RequestBody Map<String, Object> body)
    {
        Object userIdObj = body.get("userId");
        String newPassword = (String) body.get("newPassword");
        if (userIdObj == null || newPassword == null)
        {
            throw new ServiceException("用户ID和新密码不能为空");
        }
        if (newPassword.length() < 8)
        {
            throw new ServiceException("新密码长度不能少于8位");
        }
        Long userId = Long.valueOf(String.valueOf(userIdObj));
        SysUser targetUser = userService.selectUserById(userId);
        if (targetUser == null)
        {
            throw new ServiceException("用户不存在");
        }
        targetUser.setPassword(SecurityUtils.encryptPassword(newPassword));
        userService.resetPwd(targetUser);
        auditLogService.log("user", String.valueOf(userId),
                targetUser.getNickName() != null ? targetUser.getNickName() : targetUser.getUserName(),
                "PASSWORD_RESET", String.valueOf(SecurityUtils.getUserId()), "管理员重置密码");
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return result;
    }

    private Map<String, Object> buildUserMap(SysUser sysUser)
    {
        Map<String, Object> m = new HashMap<>();
        m.put("id", String.valueOf(sysUser.getUserId()));
        m.put("name", sysUser.getNickName());
        m.put("email", sysUser.getEmail());
        m.put("phone", sysUser.getPhonenumber());
        // 多角色支持：返回 roles 数组 + role 单值（向下兼容）
        List<String> roleKeys = new ArrayList<>();
        if (sysUser.getRoles() != null && !sysUser.getRoles().isEmpty())
        {
            for (SysRole r : sysUser.getRoles())
            {
                roleKeys.add(r.getRoleKey());
            }
        }
        if (roleKeys.isEmpty()) roleKeys.add("admin");
        m.put("role", roleKeys.get(0));  // 向下兼容
        m.put("roles", roleKeys);        // 多角色数组
        m.put("isActive", true);
        m.put("createdAt", sysUser.getCreateTime());
        JSONObject profile = parseProfileJson(sysUser.getRemark());
        m.put("prescriptionPreference", sanitizePrescriptionPreference(profile.get("prescriptionPreference")));
        m.put("regulatoryBody", profile.getString("regulatoryBody"));
        m.put("title", profile.getString("title"));
        m.put("registrationNumber", profile.getString("registrationNumber"));
        m.put("homeAddress", profile.get("homeAddress"));
        m.put("workingHours", profile.get("workingHours"));
        return m;
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

    private String sanitizePrescriptionPreference(Object value)
    {
        if (value == null)
        {
            return null;
        }
        String preference = String.valueOf(value).trim();
        if ("powder".equals(preference) || "raw_herbs".equals(preference) || "pills".equals(preference))
        {
            return preference;
        }
        return null;
    }

    private Authentication authenticate(UsernamePasswordAuthenticationToken authenticationToken)
    {
        try
        {
            // 若依的 SysPasswordService.validate 需要从 AuthenticationContextHolder 获取凭证
            AuthenticationContextHolder.setContext(authenticationToken);
            return authenticationManager.authenticate(authenticationToken);
        }
        finally
        {
            AuthenticationContextHolder.clearContext();
        }
    }
}
