# 2026-04-06 Unit Conversion Update Response 设计

## 背景与问题
- `/api/unit-conversions/{id}` PUT 直接把调用方提交的 map 拼回响应，因此当客户端只更新 `factor`/`notes`、不带 `fromUnit`/`toUnit` 时，响应里就会返回 `null`，与数据库中实际存储的换算信息不一致。
- 真实联调返回 200，但语义混乱，前端无法信任响应中的单位。

## 目标
1. 更新成功后，响应必须反映数据库中该记录的最新 `fromUnit`、`toUnit`、`factor`、`notes`。（请求未提交的字段不能变成 null）
2. 变更范围控制在 unit conversion 相关的 controller/service/mapper/资源/test 代码内。

## 选定方案（推荐）
1. Controller 在执行更新前先确认该 id 存在（调用 `selectTcmUnitConversionById`）；构造一个 patch 实体，只把请求里出现的字段交给 service 更新。
2. update 调用完成后，再一次调用 `selectTcmUnitConversionById(id)` 把数据库里的完整实体读出来，Response 里的所有字段都从这个实体拿。这样无论客户端提交哪些字段，返回的 always 反映真实记录。
3. Service/API 添加 `selectTcmUnitConversionById` 方法；Mapper 也新增对应 SQL，复用现有 `resultMap`。
4. 新增 Controller 单元测试（Mockito）：`conversionService.selectTcmUnitConversionById` 返回一个有 fromUnit/toUnit 的实体，调用 `update` 时只提交部分字段，断言返回值源自 mock 记录。

## 数据流与错误处理
- Controller: `@PutMapping` 接受 `id` 和请求 map；先用 `selectTcmUnitConversionById` 确认存在，构造 `TcmUnitConversion` 更新对象、调用 `conversionService.updateTcmUnitConversion`，再调用 `selectTcmUnitConversionById` 获取实体用于响应。若查询返回 null，抛 `ServiceException("单位换算不存在")`。
- Service: `selectTcmUnitConversionById` 由 mapper 执行 `select ... where id = #{id}`；update 仍然是现有的 `update` 自带 `<set>` 语法，null 字段不会触发 update。
- Mapper/资源: `TcmUnitConversionMapper.xml` 新增 select 语句。
- 测试: 主要验证响应中没有 null 字段，且 `fromUnit/toUnit` 来自 mock 实体而不是请求 map。

## 验证命令
- `mvn -pl ruoyi-hospital test -Dtest=TcmUnitConversionControllerTest`
- 若全量运行：`mvn -pl ruoyi-hospital test`

## 其他
- 已向用户询问是否按数据库实体返回响应，尚未收到明确回复，先暂定该方向。
