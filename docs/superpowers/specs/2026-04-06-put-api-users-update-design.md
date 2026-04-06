# PUT /api/users/{id} 更新响应语义与邮箱同步 设计

## 背景
1. `PUT /api/users/{id}` 目前会调用 `SysUserService.updateUser`，该方法在更新前无条件清空 `sys_user_role` 再插入 `roleIds`。
2. 当接口请求中未包含 `roles/role` 字段时，控制器不会设置 `roleIds`，导致 `updateUser` 删除原有的角色关联。
3. 更新通过后，前端得到的 `role`/`roles` 都为空，用户失去权限视图；同时更新邮箱时只同步 `email`，而登录端支持 `user_name/email/phonenumber`，因此应该保持 `user_name` 与邮箱一致。
4. 当前代码缺乏覆盖 `TcmUserController.update` 的测试，无法确保后续改动不再回归。

## 目标
- 保证在没有显式传入角色信息时，`PUT /api/users/{id}` 不会删除已有角色。
- 每次更新邮箱字段时同步修改 `userName`，让登录凭证仍然有效。
- 补充回归测试覆盖以上行为。

## 根因分析
- `SysUserService.updateUser` 会先调用 `userRoleMapper.deleteUserRoleByUserId`，再通过 `insertUserRole` 重建角色关联；如果 `SysUser.roleIds` 为 `null`（因为请求里没有 `roles`/`role`），就不会插入任何角色，导致用户丢失权限。
- 控制器只在 `body` 带 `roles`/`role` 且调用者拥有 `admin` 权限时才设置 `roleIds`；因此非管理员修改昵称、电话、邮箱等场景会触发上述删除逻辑。
- 更新邮箱未同步 `SysUser.userName`，而登录接口用 `user_name/email/phonenumber` 进行查找，导致邮件修改后无法使用新邮箱登录。

## 方案对比
1. **在控制器内保留当前 `roleIds`**（推荐）
   - 逻辑： `userService.selectUserById` 返回当前角色列表，将其转换为 `roleIds`，只有请求中明确修改角色时才调用 `resolveRoleIdsFromBody`。
   - 优点：仅在用户显式变更角色时才重写关联，改动局限于用户上下文，不影响 system 层，多用于已知代码路径。
   - 缺点：需要在 controller 中处理角色 ID 的转换逻辑，稍微增加了耦合。
2. **修改 `SysUserService.updateUser` 使其在 `roleIds == null` 时恢复旧角色**（全局方式）
   - 逻辑：在 service 内缓存旧角色、或者跳过 `delete/insert`，只在非 `null` 时重写。
   - 优点：一次性解决全部调用点，不需要 controller 层修改。
   - 缺点：触及 system 层、影响多个模块，回归风险更高，牵涉到权限数据清理语义。
3. **在 controller 调用 `userRoleMapper.selectByUserId` 强制再注入角色**（较弱）
   - 逻辑：重新查询角色并通过 `user.setRoleIds` 值赋；与方案 1 类似但更依赖半公开接口。
   - 缺点：与方案 1 重复且更复杂。

## 推荐方案
采纳方案 1：在控制器中捕获当前 `SysUser.roles`，将其转为 `roleIds` 赋回 `SysUser`；仅当调用者是 admin 且 `roles`/`role` 字段出现时才覆盖这个数组。顺便在 `email` 变更逻辑中同步设置 `userName`，保持登录唯一标识。

### 关键改动点
1. 新增工具方法 `extractRoleIds(List<SysRole>)`，用于提取当前角色 ID 列表，并在缺省情况下将其赋给 `SysUser.roleIds`。
2. 将 email 更新 flow 改为先校验、再同时 `setEmail` + `setUserName`。
3. 添加 Mockito 单元测试覆盖：不传角色时 `SysUser.roleIds` 仍包含原值；邮箱变更时 `userName` 一致。

## 任务清单
1. 编写本设计并提交到 `docs/superpowers/specs/2026-04-06-put-api-users-update-design.md`，便于复审。
2. 修改 `TcmUserController` 的 `update` 方法，补充角色保底逻辑与 `userName` 同步。
3. 新增 `TcmUserControllerTest` 验证角色保留与邮箱同步。
4. 运行 `mvn -pl ruoyi-hospital test -Dtest=TcmUserControllerTest` 或等效命令。
5. 提交设计与实现（含测试）并反馈给用户，等待审阅。

## 实施计划
1. 先实现 `TcmUserController` 的实逻辑，顺便保证现有 helper 可复用。
2. 在 `src/test/java/com/ruoyi/hospital/controller` 下添加测试类，调用 Mockito + `SecurityContextHolder` 构造场景；测试 `SecurityUtils` 静态依赖。
3. 清理测试状态，运行单元测试确认覆盖。
4. 确保 `docs/superpowers/specs/...` 记录好设计、任务、验证内容，便于后续审阅。

## 验证计划
- 单元：`TcmUserControllerTest` 覆盖角色恢复与 email -> userName 同步。
- mvn 相关模块测试：`mvn -pl ruoyi-hospital test -Dtest=TcmUserControllerTest` 。
- 手动回归：上线前可再 hit PUT 接口确认 `roles` 列表不再为空。

## 思考
- 设计保留了 `SysUserService` 的角色重建语义（仍删除再插入），避免大量重构。
- 如果后续需要全局修复，可以考虑让 `updateUser` 接受 `Optional` 或在服务层判断 `roleIds` null 时候从数据库填充。
- 本次改动没有修改 system 层接口，字段同步仅在 hospital 控制器内处理，保持职责清晰。

请先审阅以上设计，确认可以继续开发；如有疑问我会调整并再次提交。
