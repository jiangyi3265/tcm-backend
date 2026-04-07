package com.ruoyi.hospital.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.hospital.domain.TcmFormula;
import com.ruoyi.hospital.domain.TcmHerbDict;
import com.ruoyi.hospital.domain.TcmInventoryItem;
import com.ruoyi.hospital.domain.TcmMeridian;
import com.ruoyi.hospital.service.ITcmAuditLogService;
import com.ruoyi.hospital.service.ITcmConsultationModService;
import com.ruoyi.hospital.service.ITcmFormulaService;
import com.ruoyi.hospital.service.ITcmHerbDictService;
import com.ruoyi.hospital.service.ITcmInventoryService;
import com.ruoyi.hospital.service.ITcmMeridianService;
import com.ruoyi.hospital.service.ITcmPriceListService;
import com.ruoyi.hospital.service.ITcmRoomService;
import com.ruoyi.hospital.service.ITcmServiceTypeService;
import com.ruoyi.hospital.service.ITcmTreatmentTemplateService;
import com.ruoyi.system.service.ISysUserService;

@ExtendWith(MockitoExtension.class)
class TcmControllerValidationTest
{
    @Mock
    private ITcmInventoryService inventoryService;

    @Mock
    private ITcmFormulaService formulaService;

    @Mock
    private ITcmAuditLogService auditLogService;

    @Mock
    private ITcmConsultationModService consultationModService;

    @Mock
    private ISysUserService userService;

    @Mock
    private ITcmTreatmentTemplateService templateService;

    @Mock
    private ITcmHerbDictService herbDictService;

    @Mock
    private ITcmMeridianService meridianService;

    @Mock
    private ITcmServiceTypeService serviceTypeService;

    @Mock
    private ITcmRoomService roomService;

    @Mock
    private ITcmPriceListService priceListService;

    private TcmInventoryController inventoryController;
    private TcmFormulaController formulaController;
    private TcmTreatmentTemplateController templateController;
    private TcmHerbDictController herbDictController;
    private TcmMeridianController meridianController;
    private TcmSettingsController settingsController;

    @BeforeEach
    void setUp()
    {
        inventoryController = new TcmInventoryController();
        ReflectionTestUtils.setField(inventoryController, "inventoryService", inventoryService);
        ReflectionTestUtils.setField(inventoryController, "auditLogService", auditLogService);
        ReflectionTestUtils.setField(inventoryController, "consultationModService", consultationModService);
        ReflectionTestUtils.setField(inventoryController, "userService", userService);
        ReflectionTestUtils.setField(inventoryController, "herbDictService", herbDictService);

        formulaController = new TcmFormulaController();
        ReflectionTestUtils.setField(formulaController, "formulaService", formulaService);

        templateController = new TcmTreatmentTemplateController();
        ReflectionTestUtils.setField(templateController, "templateService", templateService);
        ReflectionTestUtils.setField(templateController, "auditLogService", auditLogService);

        herbDictController = new TcmHerbDictController();
        ReflectionTestUtils.setField(herbDictController, "herbDictService", herbDictService);

        meridianController = new TcmMeridianController();
        ReflectionTestUtils.setField(meridianController, "meridianService", meridianService);

        settingsController = new TcmSettingsController();
        ReflectionTestUtils.setField(settingsController, "serviceTypeService", serviceTypeService);
        ReflectionTestUtils.setField(settingsController, "roomService", roomService);
        ReflectionTestUtils.setField(settingsController, "priceListService", priceListService);
        ReflectionTestUtils.setField(settingsController, "auditLogService", auditLogService);
    }

    @Test
    void getTemplate_shouldThrowServiceExceptionWhenTemplateMissing()
    {
        when(templateService.selectTcmTreatmentTemplateById("missing")).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> templateController.get("missing"));

        assertEquals("模板不存在", ex.getMessage());
    }

    @Test
    void getHerb_shouldThrowServiceExceptionWhenHerbMissing()
    {
        when(herbDictService.selectTcmHerbDictById("missing")).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> herbDictController.get("missing"));

        assertEquals("药材不存在", ex.getMessage());
    }

    @Test
    void getMeridian_shouldThrowServiceExceptionWhenMeridianMissing()
    {
        when(meridianService.selectTcmMeridianById("missing")).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> meridianController.get("missing"));

        assertEquals("经络不存在", ex.getMessage());
    }

    @Test
    void updateServiceType_shouldThrowServiceExceptionWhenKeyMissing()
    {
        when(serviceTypeService.selectByKey("missing")).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> settingsController.updateServiceType("missing", Collections.singletonMap("label", "x")));

        assertEquals("服务类型不存在", ex.getMessage());
    }

    @Test
    void deductPrescription_shouldRejectNonArrayHerbals()
    {
        Map<String, Object> body = new HashMap<>();
        body.put("herbals", "oops");
        body.put("prescriptionType", "raw_herbs");

        ServiceException ex = assertThrows(ServiceException.class,
                () -> inventoryController.deductPrescription(body));

        assertEquals("herbals must be an array", ex.getMessage());
    }

    @Test
    void restorePrescription_shouldRejectNonStringPrescriptionType()
    {
        Map<String, Object> body = new HashMap<>();
        body.put("herbals", Collections.emptyList());
        body.put("prescriptionType", 123);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> inventoryController.restorePrescription(body));

        assertEquals("prescriptionType must be a string", ex.getMessage());
    }

    @Test
    void batchImport_shouldRejectInvalidQuantityForExistingItem()
    {
        TcmInventoryItem existing = new TcmInventoryItem();
        existing.setId("inv-1");
        existing.setBranchId("branch-main");
        existing.setCategory("raw_herbs");
        existing.setName("黄芪");
        existing.setHerbDictId("herb-1");
        existing.setQuantity(BigDecimal.ONE);
        existing.setIsActive(1);

        when(inventoryService.selectTcmInventoryItemList(org.mockito.ArgumentMatchers.any(TcmInventoryItem.class)))
                .thenReturn(Collections.singletonList(existing));
        when(herbDictService.selectTcmHerbDictList(any(TcmHerbDict.class)))
                .thenReturn(Collections.singletonList(activeHerb("herb-1", "黄芪")));

        Map<String, Object> item = new HashMap<>();
        item.put("branchId", "branch-main");
        item.put("category", "raw_herbs");
        item.put("name", "黄芪");
        item.put("quantity", "bad-number");

        Map<String, Object> body = new HashMap<>();
        body.put("items", Collections.singletonList(item));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> inventoryController.batchImport(body));

        assertEquals("黄芪 quantity must be a valid number", ex.getMessage());
    }

    @Test
    void createInventory_shouldRejectRawHerbWithoutHerbDictId()
    {
        doAnswer(invocation -> {
            TcmInventoryItem item = invocation.getArgument(0);
            if ("raw_herbs".equals(item.getCategory()) && item.getHerbDictId() == null)
            {
                throw new ServiceException("raw herb herbDictId is required");
            }
            return 1;
        }).when(inventoryService).insertTcmInventoryItem(any(TcmInventoryItem.class));

        Map<String, Object> body = new HashMap<>();
        body.put("name", "黄芪");
        body.put("category", "raw_herbs");

        ServiceException ex = assertThrows(ServiceException.class,
                () -> inventoryController.create(body));

        assertEquals("raw herb herbDictId is required", ex.getMessage());
    }

    @Test
    void createFormula_shouldRejectNonArrayItems()
    {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "四君子汤");
        body.put("items", "bad-items");

        ServiceException ex = assertThrows(ServiceException.class,
                () -> formulaController.create(body));

        assertEquals("items must be an array", ex.getMessage());
    }

    @Test
    void createFormula_shouldRejectItemWithoutHerbDictId()
    {
        doAnswer(invocation -> {
            TcmFormula formula = invocation.getArgument(0);
            if (formula.getItems() != null && !formula.getItems().isEmpty()
                    && formula.getItems().get(0).getHerbDictId() == null)
            {
                throw new ServiceException("formula item herbDictId is required");
            }
            return 1;
        }).when(formulaService).insertTcmFormula(any(TcmFormula.class));

        Map<String, Object> item = new HashMap<>();
        item.put("herbName", "党参");
        item.put("dosage", "10");

        Map<String, Object> body = new HashMap<>();
        body.put("name", "四君子汤");
        body.put("items", Collections.singletonList(item));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> formulaController.create(body));

        assertEquals("formula item herbDictId is required", ex.getMessage());
    }

    @Test
    void batchImport_shouldSkipRawHerbWhenDictionaryMatchIsMissingOrAmbiguous()
    {
        when(inventoryService.selectTcmInventoryItemList(any(TcmInventoryItem.class)))
                .thenReturn(Collections.emptyList());

        TcmHerbDict duplicateA = activeHerb("dup-1", "黄芪");
        TcmHerbDict duplicateB = activeHerb("dup-2", "黄芪");
        when(herbDictService.selectTcmHerbDictList(any(TcmHerbDict.class)))
                .thenReturn(Arrays.asList(duplicateA, duplicateB));

        Map<String, Object> missing = new HashMap<>();
        missing.put("branchId", "branch-main");
        missing.put("category", "raw_herbs");
        missing.put("name", "党参");
        missing.put("quantity", "2");

        Map<String, Object> ambiguous = new HashMap<>();
        ambiguous.put("branchId", "branch-main");
        ambiguous.put("category", "raw_herbs");
        ambiguous.put("name", "黄芪");
        ambiguous.put("quantity", "3");

        Map<String, Object> body = new HashMap<>();
        body.put("items", Arrays.asList(missing, ambiguous));

        Map<String, Object> result = inventoryController.batchImport(body);

        assertEquals(0, result.get("created"));
        assertEquals(0, result.get("updated"));
        assertEquals(2, ((List<?>) result.get("errors")).size());
        verify(inventoryService, never()).insertTcmInventoryItem(any(TcmInventoryItem.class));
        verify(inventoryService, never()).updateTcmInventoryItem(any(TcmInventoryItem.class));
    }

    @Test
    void batchImport_shouldNotUpdateWrongInventoryWhenRelaxedSupplierFallbackIsAmbiguous()
    {
        TcmInventoryItem first = new TcmInventoryItem();
        first.setId("inv-a");
        first.setBranchId("branch-main");
        first.setCategory("raw_herbs");
        first.setName("黄芪");
        first.setHerbDictId("herb-1");
        first.setSupplier("同名供应商");
        first.setSupplierId("supplier-a");
        first.setQuantity(BigDecimal.ONE);
        first.setIsActive(1);

        TcmInventoryItem second = new TcmInventoryItem();
        second.setId("inv-b");
        second.setBranchId("branch-main");
        second.setCategory("raw_herbs");
        second.setName("黄芪");
        second.setHerbDictId("herb-1");
        second.setSupplier("同名供应商");
        second.setSupplierId("supplier-b");
        second.setQuantity(BigDecimal.TEN);
        second.setIsActive(1);

        when(inventoryService.selectTcmInventoryItemList(any(TcmInventoryItem.class)))
                .thenReturn(Arrays.asList(first, second));
        when(herbDictService.selectTcmHerbDictList(any(TcmHerbDict.class)))
                .thenReturn(Collections.singletonList(activeHerb("herb-1", "黄芪")));

        Map<String, Object> item = new HashMap<>();
        item.put("branchId", "branch-main");
        item.put("category", "raw_herbs");
        item.put("name", "黄芪");
        item.put("supplier", "同名供应商");
        item.put("quantity", "2");

        Map<String, Object> body = new HashMap<>();
        body.put("items", Collections.singletonList(item));

        Map<String, Object> result = inventoryController.batchImport(body);

        assertEquals(0, result.get("created"));
        assertEquals(0, result.get("updated"));
        assertEquals(1, ((List<?>) result.get("errors")).size());
        verify(inventoryService, never()).updateTcmInventoryItem(any(TcmInventoryItem.class));
    }

    private TcmHerbDict activeHerb(String id, String name)
    {
        TcmHerbDict herb = new TcmHerbDict();
        herb.setId(id);
        herb.setName(name);
        herb.setIsActive(1);
        herb.setDeletedAt(null);
        return herb;
    }
}
