# B 模块交接文档：用户、车辆、充电请求

> 负责人范围：用户注册/登录、车辆绑定/查询、充电请求的提交/查询/取消/修改。
> 本文记录 B 模块现状与对接点，接口定义以 `docs/api-spec.md` 为准。

## 1. 当前结论

以下内容已经在仓库内统一：

- 统一返回格式：`{ code, message, data }`
- 状态枚举：`ChargeMode`、`ChargingRequestStatus`、`ChargingPileStatus`、`ChargingSessionStatus`、`BillStatus`
- 核心实体：`User`、`Vehicle`、`ChargingRequest`
- Repository 基础 CRUD 已补充按用户查询请求的方法

B 模块已实现完整的充电请求创建与管理能力，但调度、会话和账单依赖 C 和 D 模块。

## 2. 已实现接口

### 2.1 用户模块

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/users/register` | POST | 注册普通用户，返回用户信息 |
| `/api/users/login` | POST | 登录，返回 userId、角色和 mock token |

### 2.2 车辆模块

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/vehicles` | POST | 绑定车辆，需提供 userId、车牌、型号、电池容量 |
| `/api/users/{userId}/vehicles` | GET | 查询某用户的所有车辆 |

### 2.3 充电请求模块

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/charging-requests` | POST | 提交充电请求，入参 userId、vehicleId、mode、targetAmount |
| `/api/charging-requests/{requestId}` | GET | 查询请求详情，需带 userId 参数校验权限 |
| `/api/charging-requests/{requestId}/cancel` | POST | 取消等待中或已分配的请求 |
| `/api/charging-requests/{requestId}/modify` | POST | 修改等待中请求的目标电量（第一版不支持改 mode） |

**业务规则**：
- 提交请求时，`status = WAITING`，`chargedAmount = 0`，`queueNumber` 按同 mode 下 WAITING 请求数 +1 生成。
- 取消：仅 `WAITING` 或 `ASSIGNED` 状态可取消。
- 修改：仅 `WAITING` 状态可修改，修改后 `queueNumber` 重新计算（当前修改未触发重新排队，建议后续由调度模块负责刷新队列位置）。
- 权限校验：所有操作都需要传入 `userId` 或通过 token 解析，第一版简单使用请求参数。

## 3. 对其他成员的依赖

### 3.1 依赖 C 模块（调度与充电桩）

B 创建的 `ChargingRequest` 是 C 模块调度的数据源。C 需要从 B 提供的接口中获取 `WAITING` 状态的请求，并执行 SJF 调度。  

C 在调度成功后需要更新：
- `request.status = ASSIGNED`
- `request.assignedPileId = pileId`

B 的修改请求如果更改了 `mode` 或 `targetAmount`，C 的调度队列需要重新计算预计时长。当前第一版 B 暂不支持 mode 修改，只允许修改 targetAmount，C 无需特殊处理。

### 3.2 依赖 D 模块（账单）

B 不直接依赖 D，但充电请求的最终状态 `COMPLETED` 由 C 在结束充电时设置，随后 D 生成账单。B 无需参与。

## 4. 数据库表说明

- `app_user`：存储用户账号、密码（明文，后续需加密）、手机号、角色。
- `vehicle`：存储车辆信息，`user_id` 关联 `app_user.id`。
- `charging_request`：存储充电请求，关键字段：
  - `mode`：FAST / SLOW
  - `status`：WAITING / ASSIGNED / CHARGING / CANCELLED / COMPLETED
  - `queue_number`：排队序号，故障恢复时临时设置为 0 表示优先
  - `assigned_pile_id`：调度分配的充电桩 ID

## 5. 已完成与待办

### 已完成
- [x] 用户注册/登录接口
- [x] 车辆绑定/查询接口
- [x] 充电请求提交/查询/取消/修改接口
- [x] 基础权限校验（userId 参数）
- [x] 排队号生成（简单计数）
- [x] 统一异常处理和返回格式

### 待办（后续迭代）
- [ ] 密码加密（BCrypt）
- [ ] JWT 认证替换 mock token
- [ ] 修改请求时重新计算排队位置（需与 C 模块协商）
- [ ] 支持修改充电模式（需复杂队列调整）
- [ ] 充电请求列表接口（支持分页，管理端/用户端需要）

## 6. 本地验证方式

启动后端后，可通过以下 curl 命令验证：

```bash
# 注册
curl -X POST http://localhost:8080/api/users/register \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"123","phone":"13800138000"}'

# 登录
curl -X POST http://localhost:8080/api/users/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"123"}'

# 绑定车辆（替换 userId）
curl -X POST http://localhost:8080/api/vehicles \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"plateNumber":"京A12345","model":"Tesla","batteryCapacity":60}'

# 提交充电请求
curl -X POST http://localhost:8080/api/charging-requests \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"vehicleId":1,"mode":"FAST","targetAmount":30}'

# 查询请求详情
curl "http://localhost:8080/api/charging-requests/1?userId=1"

# 取消请求
curl -X POST "http://localhost:8080/api/charging-requests/1/cancel?userId=1"

# 修改请求
curl -X POST "http://localhost:8080/api/charging-requests/1/modify?userId=1" \
  -H "Content-Type: application/json" \
  -d '{"targetAmount":40}'