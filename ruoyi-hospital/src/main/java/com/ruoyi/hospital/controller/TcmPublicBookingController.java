package com.ruoyi.hospital.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.hospital.domain.TcmAppointment;
import com.ruoyi.hospital.domain.TcmPatient;
import com.ruoyi.hospital.domain.TcmRoom;
import com.ruoyi.hospital.domain.TcmServiceType;
import com.ruoyi.hospital.service.ITcmAppointmentService;
import com.ruoyi.hospital.service.ITcmPatientService;
import com.ruoyi.hospital.service.ITcmRoomService;
import com.ruoyi.hospital.service.ITcmServiceTypeService;
import com.ruoyi.hospital.utils.PayloadUtils;
import com.ruoyi.system.service.ISysUserService;

@RestController
@RequestMapping("/api/public-booking")
public class TcmPublicBookingController
{
    @Autowired
    private ITcmAppointmentService appointmentService;

    @Autowired
    private ITcmPatientService patientService;

    @Autowired
    private ITcmRoomService roomService;

    @Autowired
    private ITcmServiceTypeService serviceTypeService;

    @Autowired
    private ISysUserService userService;

    @GetMapping("/options")
    public Map<String, Object> options()
    {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> services = new ArrayList<>();
        for (TcmServiceType serviceType : serviceTypeService.selectAll())
        {
            services.add(PayloadUtils.flattenServiceType(serviceType));
        }
        result.put("serviceTypes", services);
        result.put("rooms", buildRooms());
        result.put("practitioners", buildPractitioners());
        return result;
    }

    @GetMapping("/availability")
    public Map<String, Object> availability(
            @RequestParam String date,
            @RequestParam String serviceType,
            @RequestParam(required = false) String practitionerId,
            @RequestParam(required = false) String roomId)
    {
        return appointmentService.getAvailability(date, serviceType, practitionerId, roomId, null);
    }

    @PostMapping("")
    public Map<String, Object> create(@RequestBody Map<String, Object> body)
    {
        String patientName = requireText(body.get("patientName"), "patientName");
        String phone = trim(body.get("phone"));
        String email = trim(body.get("email"));

        TcmPatient patient = findOrCreatePatient(patientName, phone, email);
        TcmAppointment appointment = PayloadUtils.toAppointment(body);
        appointment.setPatientId(patient.getId());
        appointment.setStatus("booked");
        appointmentService.insertTcmAppointment(appointment);

        TcmAppointment created = appointmentService.selectTcmAppointmentById(appointment.getId());
        if (patient.getPractitionerId() == null || patient.getPractitionerId().trim().isEmpty())
        {
            patient.setPractitionerId(created.getPractitionerId());
            patientService.updateTcmPatient(patient);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("appointment", PayloadUtils.flatten(created));
        result.put("patientId", patient.getId());
        return result;
    }

    private List<Map<String, Object>> buildPractitioners()
    {
        List<Map<String, Object>> practitioners = new ArrayList<>();
        for (SysUser basicUser : userService.selectUserList(new SysUser()))
        {
            if (basicUser == null || basicUser.getUserId() == null || basicUser.getUserId() < 1L)
            {
                continue;
            }
            SysUser user = userService.selectUserById(basicUser.getUserId());
            if (user == null || !"0".equals(user.getStatus()) || !hasPractitionerRole(user))
            {
                continue;
            }
            JSONObject profile = parseProfile(user.getRemark());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", String.valueOf(user.getUserId()));
            item.put("name", user.getNickName());
            item.put("serviceKeys", sanitizeStringList(profile.get("serviceKeys")));
            item.put("practitionerSortOrder", sanitizeInteger(profile.get("practitionerSortOrder")));
            item.put("workingHours", profile.get("workingHours"));
            practitioners.add(item);
        }
        practitioners.sort((left, right) -> {
            Integer leftOrder = (Integer) left.get("practitionerSortOrder");
            Integer rightOrder = (Integer) right.get("practitionerSortOrder");
            int resolvedLeft = leftOrder != null ? leftOrder : Integer.MAX_VALUE;
            int resolvedRight = rightOrder != null ? rightOrder : Integer.MAX_VALUE;
            if (resolvedLeft != resolvedRight)
            {
                return Integer.compare(resolvedLeft, resolvedRight);
            }
            return String.valueOf(left.get("name")).compareTo(String.valueOf(right.get("name")));
        });
        return practitioners;
    }

    private List<Map<String, Object>> buildRooms()
    {
        List<Map<String, Object>> rooms = new ArrayList<>();
        for (TcmRoom room : roomService.selectTcmRoomList(new TcmRoom()))
        {
            if (room == null || room.getId() == null || room.getId().trim().isEmpty())
            {
                continue;
            }
            if (room.getIsActive() != null && room.getIsActive() != 1)
            {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", room.getId());
            item.put("name", room.getName());
            item.put("branchId", room.getBranchId());
            item.put("isActive", true);
            rooms.add(item);
        }
        rooms.sort((left, right) -> String.valueOf(left.get("name")).compareTo(String.valueOf(right.get("name"))));
        return rooms;
    }

    private TcmPatient findOrCreatePatient(String patientName, String phone, String email)
    {
        for (TcmPatient patient : patientService.selectTcmPatientList(new TcmPatient()))
        {
            if (email != null && !email.isEmpty() && email.equalsIgnoreCase(trim(patient.getEmail())))
            {
                return mergePatientContact(patient, patientName, phone, email);
            }
            if (phone != null && !phone.isEmpty()
                    && phone.equals(trim(patient.getPhone()))
                    && patientName.equals(trim(patient.getName())))
            {
                return mergePatientContact(patient, patientName, phone, email);
            }
        }

        TcmPatient patient = new TcmPatient();
        patient.setId(java.util.UUID.randomUUID().toString());
        patient.setName(patientName);
        patient.setEmail(email);
        patient.setPhone(phone);
        patient.setIsActive(1);
        patient.setConsentSigned(0);
        JSONObject payload = new JSONObject();
        if (email != null && !email.isEmpty())
        {
            payload.put("emails", java.util.Collections.singletonList(email));
        }
        patient.setPayload(payload.toJSONString());
        patientService.insertTcmPatient(patient);
        return patientService.selectTcmPatientById(patient.getId());
    }

    private TcmPatient mergePatientContact(TcmPatient patient, String patientName, String phone, String email)
    {
        boolean changed = false;
        if (patientName != null && !patientName.equals(trim(patient.getName())))
        {
            patient.setName(patientName);
            changed = true;
        }
        if (phone != null && !phone.isEmpty() && !phone.equals(trim(patient.getPhone())))
        {
            patient.setPhone(phone);
            changed = true;
        }
        if (email != null && !email.isEmpty() && !email.equalsIgnoreCase(trim(patient.getEmail())))
        {
            patient.setEmail(email);
            changed = true;
        }
        JSONObject payload = parseProfile(patient.getPayload());
        List<String> emails = sanitizeStringList(payload.get("emails"));
        if (email != null && !email.isEmpty() && !emails.contains(email))
        {
            emails.add(email);
            payload.put("emails", emails);
            patient.setPayload(payload.toJSONString());
            changed = true;
        }
        if (changed)
        {
            patientService.updateTcmPatient(patient);
            return patientService.selectTcmPatientById(patient.getId());
        }
        return patient;
    }

    private boolean hasPractitionerRole(SysUser user)
    {
        if (user == null || user.getRoles() == null)
        {
            return false;
        }
        for (SysRole role : user.getRoles())
        {
            if (role == null || role.getRoleKey() == null)
            {
                continue;
            }
            String roleKey = role.getRoleKey().trim().toLowerCase();
            if ("practitioner".equals(roleKey) || "doctor".equals(roleKey))
            {
                return true;
            }
        }
        return false;
    }

    private JSONObject parseProfile(String remark)
    {
        try
        {
            return remark == null || remark.trim().isEmpty() ? new JSONObject() : JSON.parseObject(remark);
        }
        catch (Exception e)
        {
            return new JSONObject();
        }
    }

    private List<String> sanitizeStringList(Object value)
    {
        List<String> items = new ArrayList<>();
        if (!(value instanceof List<?>))
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

    private Integer sanitizeInteger(Object value)
    {
        if (value == null)
        {
            return null;
        }
        try
        {
            return Integer.parseInt(String.valueOf(value).trim());
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private String requireText(Object value, String fieldName)
    {
        String text = trim(value);
        if (text == null || text.isEmpty())
        {
            throw new ServiceException(fieldName + " is required");
        }
        return text;
    }

    private String trim(Object value)
    {
        return value == null ? null : String.valueOf(value).trim();
    }
}
