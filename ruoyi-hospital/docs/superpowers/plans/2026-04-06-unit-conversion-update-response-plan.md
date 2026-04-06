# Unit Conversion Update Response Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 `/api/unit-conversions/{id}` PUT 在更新完成后返回数据库中实际的 `fromUnit`/`toUnit`/`factor`/`notes`，并为这段行为加上单元测试。

**Architecture:** Controller 仍负责参数校验与请求分发，Service 负责通用业务逻辑与 MyBatis 交互；Controller 在更新后通过 Service 查询最新实体并构建响应；Mapper 提供按 id 查询。

**Tech Stack:** Java 11+/Spring Boot/MyBatis/Mockito

---

### Task 1: 增加 `selectTcmUnitConversionById` 支撑

**Files:**
- Modify: `src/main/java/com/ruoyi/hospital/mapper/TcmUnitConversionMapper.java`
- Modify: `src/main/resources/mapper/hospital/TcmUnitConversionMapper.xml`
- Modify: `src/main/java/com/ruoyi/hospital/service/ITcmUnitConversionService.java`
- Modify: `src/main/java/com/ruoyi/hospital/service/impl/TcmUnitConversionServiceImpl.java`

- [ ] **Step 1: 在 Mapper 接口声明方法**

```java
public interface TcmUnitConversionMapper
{
    List<TcmUnitConversion> selectAll();

    TcmUnitConversion selectByPair(String fromUnit, String toUnit);

    TcmUnitConversion selectTcmUnitConversionById(Long id);

    int insertTcmUnitConversion(TcmUnitConversion conversion);

    int updateTcmUnitConversion(TcmUnitConversion conversion);

    int deleteTcmUnitConversionById(Long id);
}
```

- [ ] **Step 2: 在 XML 里添加 select**

```xml
    <select id="selectTcmUnitConversionById" resultMap="TcmUnitConversionResult">
        select id, from_unit, to_unit, factor, notes, create_time
        from tcm_unit_conversion
        where id = #{id}
    </select>
```

- [ ] **Step 3: 在 service 接口新增方法声明**

```java
public interface ITcmUnitConversionService
{
    ...
    TcmUnitConversion selectTcmUnitConversionById(Long id);
    ...
}
```

- [ ] **Step 4: 在 service 实现中委托 mapper**

```java
@Service
public class TcmUnitConversionServiceImpl implements ITcmUnitConversionService
{
    @Autowired
    private TcmUnitConversionMapper conversionMapper;

    @Override
    public TcmUnitConversion selectTcmUnitConversionById(Long id)
    {
        return conversionMapper.selectTcmUnitConversionById(id);
    }
}
```

### Task 2: 写出确保响应使用数据库实体的控制器单元测试

**Files:**
- Create: `src/test/java/com/ruoyi/hospital/controller/TcmUnitConversionControllerTest.java`

- [ ] **Step 1: 编写测试，mock service，让 controller update 调用后返回来自 mock 实体**

```java
@ExtendWith(MockitoExtension.class)
class TcmUnitConversionControllerTest
{
    @Mock
    private ITcmUnitConversionService conversionService;

    private TcmUnitConversionController controller;

    @BeforeEach
    void setUp()
    {
        controller = new TcmUnitConversionController();
        ReflectionTestUtils.setField(controller, "conversionService", conversionService);
    }

    @Test
    void update_shouldReturnDatabaseValuesEvenIfRequestOmitsUnits()
    {
        long id = 42L;
        Map<String, Object> body = Map.of("factor", "1.5");

        TcmUnitConversion persisted = new TcmUnitConversion();
        persisted.setId(id);
        persisted.setFromUnit("克");
        persisted.setToUnit("毫克");
        persisted.setFactor(new BigDecimal("1.5"));
        persisted.setNotes("db note");

        when(conversionService.selectTcmUnitConversionById(id)).thenReturn(persisted);

        Map<String, Object> response = controller.update(id, body);

        assertEquals("克", response.get("fromUnit"));
        assertEquals("毫克", response.get("toUnit"));
        assertEquals(new BigDecimal("1.5"), response.get("factor"));
        assertEquals("db note", response.get("notes"));
    }
}
```

- [ ] **Step 2: 运行这个测试，确认现有 controller 代码因为直接返回请求数据而失败**

```
mvn -pl ruoyi-hospital test -Dtest=TcmUnitConversionControllerTest
# 预计失败：response.get("fromUnit") 仍然是 null
```

### Task 3: 修改控制器 update 方法

**Files:**
- Modify: `src/main/java/com/ruoyi/hospital/controller/TcmUnitConversionController.java`

- [ ] **Step 1: 将 update 改成先查记录、再打补丁、更新、最后用最新实体响应**

```java
@PutMapping("/{id}")
public Map<String, Object> update(@PathVariable Long id, @RequestBody Map<String, Object> body)
{
    TcmUnitConversion existing = conversionService.selectTcmUnitConversionById(id);
    if (existing == null)
    {
        throw new ServiceException("单位换算不存在");
    }

    TcmUnitConversion patch = new TcmUnitConversion();
    patch.setId(id);
    if (body.containsKey("fromUnit"))
    {
        patch.setFromUnit(validateRequiredLength(body.get("fromUnit"), "fromUnit", 20));
    }
    if (body.containsKey("toUnit"))
    {
        patch.setToUnit(validateRequiredLength(body.get("toUnit"), "toUnit", 20));
    }
    if (body.containsKey("factor"))
    {
        patch.setFactor(new BigDecimal(body.get("factor").toString()));
    }
    if (body.containsKey("notes"))
    {
        patch.setNotes(validateOptionalLength(body.get("notes"), "notes", 200));
    }

    conversionService.updateTcmUnitConversion(patch);

    TcmUnitConversion refreshed = conversionService.selectTcmUnitConversionById(id);
    if (refreshed == null)
    {
        throw new ServiceException("单位换算不存在");
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", refreshed.getId());
    result.put("fromUnit", refreshed.getFromUnit());
    result.put("toUnit", refreshed.getToUnit());
    result.put("factor", refreshed.getFactor());
    result.put("notes", refreshed.getNotes());
    return result;
}
```

- [ ] **Step 2: 重新运行 controller 测试，确认现在通过**

```
mvn -pl ruoyi-hospital test -Dtest=TcmUnitConversionControllerTest
# 预计通过
```

### Task 4: 验证并提交

**Files:**
- Modify: all files touched above, plus docs

- [ ] **Step 1: 运行 ruoyi-hospital 模块的所有测试**

```
mvn -pl ruoyi-hospital test
# 期待全量通过
```

- [ ] **Step 2: 提交变更**

```
git add src/main/java/com/ruoyi/hospital/mapper/TcmUnitConversionMapper.java \
    src/main/resources/mapper/hospital/TcmUnitConversionMapper.xml \
    src/main/java/com/ruoyi/hospital/service/ITcmUnitConversionService.java \
    src/main/java/com/ruoyi/hospital/service/impl/TcmUnitConversionServiceImpl.java \
    src/main/java/com/ruoyi/hospital/controller/TcmUnitConversionController.java \
    src/test/java/com/ruoyi/hospital/controller/TcmUnitConversionControllerTest.java \
    docs/superpowers/specs/2026-04-06-unit-conversion-update-response-design.md \
    docs/superpowers/plans/2026-04-06-unit-conversion-update-response-plan.md
git commit -m "fix: align unit conversion update response"
```
