package com.ruoyi.hospital.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.hospital.domain.*;
import com.ruoyi.hospital.mapper.*;
import com.ruoyi.hospital.service.impl.TcmAppointmentServiceImpl;
import com.ruoyi.system.mapper.SysUserMapper;

/** Real scheduling service -> public controller; only persistence is stubbed. */
class TcmPublicBookingDripBlockTest
{
    private final LocalDate day = LocalDate.now(ZoneId.of("America/Toronto")).plusDays(1);
    private final TcmAppointmentMapper appointments = mock(TcmAppointmentMapper.class);
    private final TcmClinicSettingMapper settings = mock(TcmClinicSettingMapper.class);
    private final TcmRoomMapper rooms = mock(TcmRoomMapper.class);
    private final TcmServiceTypeMapper services = mock(TcmServiceTypeMapper.class);
    private final SysUserMapper users = mock(SysUserMapper.class);
    private final TcmAppointmentServiceImpl service = new TcmAppointmentServiceImpl();
    private final TcmPublicBookingController controller = new TcmPublicBookingController();
    private final List<TcmAppointment> existing = new ArrayList<>();
    private JSONObject profile;

    @BeforeEach void setup()
    {
        ReflectionTestUtils.setField(service, "appointmentMapper", appointments);
        ReflectionTestUtils.setField(service, "settingMapper", settings);
        ReflectionTestUtils.setField(service, "roomMapper", rooms);
        ReflectionTestUtils.setField(service, "serviceTypeMapper", services);
        ReflectionTestUtils.setField(service, "userMapper", users);
        ReflectionTestUtils.setField(controller, "appointmentService", service);
        ReflectionTestUtils.setField(controller, "clinicSettingMapper", settings);
        ReflectionTestUtils.setField(controller, "userMapper", users);
        when(rooms.selectTcmRoomList(any())).thenReturn(Collections.emptyList());
        when(appointments.selectAppointmentsInRange(any(), any(), anyString(), anyString(), any())).thenAnswer(i -> existing);
        when(users.selectActiveUserIds()).thenReturn(List.of(42L));
        profile = new JSONObject().fluentPut("serviceKeys", List.of("short", "long")).fluentPut("dripEnabled", true);
        when(users.selectUserById(42L)).thenAnswer(i -> {
            SysRole role = new SysRole(); role.setRoleKey("practitioner");
            SysUser user = new SysUser(); user.setUserId(42L); user.setStatus("0"); user.setNickName("Synthetic doctor");
            user.setRoles(List.of(role)); user.setRemark(profile.toJSONString()); return user;
        });
        for (String key : List.of("short", "long"))
        {
            TcmServiceType type = new TcmServiceType(); type.setServiceKey(key);
            type.setDuration("short".equals(key) ? 30 : 60); type.setPractitionerTime("30"); type.setRoomRequired(0);
            when(services.selectTcmServiceTypeByKey(key)).thenReturn(type);
        }
        setting("publicBookingAdvanceDays", "30"); setting("publicBookingDripWindowDays", "14"); setting("publicBookingDripMinutes", "60");
    }

    @Test void emptySplitDayReleasesBottomOfEveryBlock()
    {
        work("09:00", "12:00", "14:00", "18:00");
        assertEquals(List.of("11:30", "17:30"), starts("short"));
        assertEquals(List.of("11:00", "17:00"), starts("long"));
    }

    @Test void adjacentButDistinctBlocksRetainIndependentReleases()
    {
        work("09:00", "10:00", "10:00", "10:30");
        assertEquals(List.of("09:30", "10:00"), starts("short"));
        assertEquals(List.of("09:00"), starts("long"));
    }

    @Test void busyFirstBlockDoesNotSuppressEmptySecondBlock()
    {
        work("09:00", "12:00", "14:00", "18:00"); booked("10:00", "11:00");
        assertEquals(List.of("09:30", "11:00", "17:30"), starts("short"));
        assertEquals(List.of("09:00", "11:00", "17:00"), starts("long"));
    }

    @Test void occupiedClusterReleasesOnlyOneChoiceAtEachEdge()
    {
        work("09:00", "16:00"); booked("11:30", "13:00");
        assertEquals(List.of("11:00", "13:00"), starts("short"));
        assertEquals(List.of("10:30", "13:00"), starts("long"));
    }

    @Test void oneHourInteriorGapTakesPriorityAndShortServiceUsesItsHead()
    {
        work("09:00", "16:00"); booked("10:00", "11:00"); booked("12:00", "13:00");
        assertEquals(List.of("11:00"), starts("short"));
        assertEquals(List.of("11:00"), starts("long"));
    }

    @Test void twoHourInteriorGapOffersBothEdges()
    {
        work("09:00", "16:00"); booked("10:00", "11:00"); booked("13:00", "14:00");
        assertEquals(List.of("11:00", "12:30"), starts("short"));
        assertEquals(List.of("11:00", "12:00"), starts("long"));
    }

    @Test void disconnectedHalfHoursInScreenshotRemainVisible()
    {
        work("09:00", "15:00"); booked("09:00", "10:00"); booked("10:30", "11:30"); booked("12:00", "15:00");
        assertEquals(List.of("10:00", "11:30"), starts("short"));
        assertEquals(Collections.emptyList(), starts("long"));
    }

    @Test void shortInteriorGapCanBeCompletedByNonAdjacentOuterHalfHour()
    {
        work("10:00", "15:00"); booked("10:30", "11:30"); booked("12:00", "14:00");
        assertEquals(List.of("10:00", "11:30"), starts("short"));
        assertEquals(List.of("14:00"), starts("long"));
    }

    @Test void cancellationDoesNotAnchorReleaseAndOverlappingBookingsAreMerged()
    {
        work("09:00", "16:00"); booked("11:00", "12:00"); booked("11:30", "13:00");
        TcmAppointment cancelled = booked("14:00", "15:00"); cancelled.setStatus("cancelled");
        assertEquals(List.of("10:30", "13:00"), starts("short"));
    }

    @Test void roomConflictsRemainUnavailable()
    {
        work("09:00", "12:00");
        TcmServiceType type = services.selectTcmServiceTypeByKey("short"); type.setRoomRequired(1);
        TcmRoom room = new TcmRoom(); room.setId("room-1"); room.setIsActive(1); room.setSupportTags("[]");
        when(rooms.selectTcmRoomList(any())).thenReturn(List.of(room));
        TcmAppointment other = booked("11:00", "12:00"); other.setPractitionerId("99"); other.setRoomId("room-1");
        assertEquals(List.of("10:30"), starts("short"));
    }

    @Test void roomBlockedPartOfInteriorGapDoesNotCountTowardReleaseBudget()
    {
        work("09:00", "16:00"); booked("10:00", "11:00"); booked("13:00", "14:00");
        TcmServiceType type = services.selectTcmServiceTypeByKey("short"); type.setRoomRequired(1);
        TcmRoom room = new TcmRoom(); room.setId("room-1"); room.setIsActive(1); room.setSupportTags("[]");
        when(rooms.selectTcmRoomList(any())).thenReturn(List.of(room));
        TcmAppointment other = booked("11:30", "13:00"); other.setPractitionerId("99"); other.setRoomId("room-1");
        assertEquals(List.of("09:30", "11:00"), starts("short"));
    }

    @Test void disabledDripRetainsAllAvailableSlotsAndDoesNotExposeMetadata()
    {
        work("09:00", "11:00"); profile.put("dripEnabled", false);
        assertEquals(List.of("09:00", "09:30", "10:00", "10:30"), starts("short"));
        Map<String, Object> result = controller.schedule(day.toString(), null, "short", "42", null);
        String serialized = JSONObject.toJSONString(result);
        assertFalse(serialized.contains("bookingRanges")); assertFalse(serialized.contains("workingBlocks"));
        assertFalse(serialized.contains("synthetic-patient"));
    }

    @Test void aggregateDripMatchesSingleDoctorWithoutExposingAppointmentMetadata()
    {
        work("09:00", "16:00"); booked("11:30", "13:00");
        Map<String, Object> result = controller.schedule(day.toString(), null, "short", null, null);
        assertEquals(List.of("11:00", "13:00"), startsFrom(result));
        String serialized = JSONObject.toJSONString(result);
        assertFalse(serialized.contains("bookingRanges")); assertFalse(serialized.contains("workingBlocks"));
        assertFalse(serialized.contains("synthetic-patient")); assertFalse(serialized.contains("synthetic-0"));
    }

    @Test void outsideDripWindowRetainsAllAvailableSlots()
    {
        work("09:00", "11:00"); setting("publicBookingDripWindowDays", "1");
        assertEquals(List.of("09:00", "09:30", "10:00", "10:30"), starts("short"));
    }

    @Test void beyondAdvanceWindowRemainsEmpty()
    {
        work("09:00", "11:00"); setting("publicBookingAdvanceDays", "1");
        assertEquals(Collections.emptyList(), starts("short"));
    }

    @Test void serviceMetadataUsesActualFullAppointmentBoundaries()
    {
        work("09:00", "16:00"); booked("11:30", "13:00");
        Map<String, Object> raw = service.getWeeklySchedule(day.toString(), "long", "42", null);
        Map<String, Object> rawDay = days(raw).stream().filter(d -> day.toString().equals(d.get("date"))).findFirst().orElseThrow();
        assertEquals(List.of(Map.of("startTime", day+" 11:30:00", "endTime", day+" 13:00:00")), rawDay.get("bookingRanges"));
    }

    private void setting(String key, String value)
    {
        TcmClinicSetting setting = new TcmClinicSetting(); setting.setSettingKey(key); setting.setSettingValue(value);
        when(settings.selectSettingByKey(key)).thenReturn(setting);
    }
    private void work(String... edges)
    {
        List<Map<String, String>> blocks = new ArrayList<>();
        for (int i = 0; i < edges.length; i += 2) blocks.add(Map.of("start", edges[i], "end", edges[i + 1]));
        profile.put("workingHours", Map.of(day.getDayOfWeek().name().toLowerCase(Locale.ROOT), blocks));
    }
    private TcmAppointment booked(String start, String end)
    {
        TcmAppointment appointment = new TcmAppointment(); appointment.setId("synthetic-"+existing.size());
        appointment.setPatientId("synthetic-patient"); appointment.setPractitionerId("42"); appointment.setStatus("booked");
        appointment.setStartTime(day+" "+start+":00"); appointment.setEndTime(day+" "+end+":00"); existing.add(appointment);
        return appointment;
    }
    @SuppressWarnings("unchecked") private List<Map<String,Object>> days(Map<String,Object> schedule)
    {
        return (List<Map<String,Object>>) schedule.get("days");
    }
    @SuppressWarnings("unchecked") private List<String> starts(String key)
    {
        return startsFrom(controller.schedule(day.toString(), null, key, "42", null));
    }
    @SuppressWarnings("unchecked") private List<String> startsFrom(Map<String,Object> schedule)
    {
        Map<String,Object> result = days(schedule).stream().filter(d -> day.toString().equals(d.get("date"))).findFirst().orElseThrow();
        return ((List<Map<String,Object>>) result.get("slots")).stream().map(slot -> slot.get("startTime").toString().substring(11,16)).collect(Collectors.toList());
    }
}
