# C 模块交接文档：调度与充电桩

> 负责人范围：充电桩管理、队列查询、SJF 调度、开始充电、结束充电、故障恢复。
> 本文只记录 C 模块现状与对接点，接口定义以 `docs/api-spec.md` 为准。

## 1. 当前结论

以下内容已经在仓库内统一：

- 统一返回格式：`{ code, message, data }`
- 状态枚举：`ChargeMode`、`ChargingRequestStatus`、`ChargingPileStatus`、`ChargingSessionStatus`、`BillStatus`
- 核心实体：`ChargingPile`、`ChargingRequest`、`ChargingSession`、`Bill`
- Repository 基础 CRUD

但是 C 的完整正向链路依赖 B 先提供 `WAITING` 状态的 `ChargingRequest` 数据，依赖 D 后续接管账单查询与支付接口。

## 2. 已实现接口

### 2.1 充电桩

`GET /api/piles`

返回所有充电桩状态，字段：

- `pileId`
- `code`
- `mode`
- `power`
- `status`
- `serviceFee`

`POST /api/piles/{pileId}/fault`

请求体：

```json
{
  "faultReason": "设备离线"
}
```

当前规则：

- 非充电中的桩直接置为 `FAULT`
- `CHARGING` 状态的桩会中断当前会话：`session.status = INTERRUPTED`
- 会记录本次故障前已充电量，更新 `request.chargedAmount`
- 若还有剩余电量，将请求放回 `WAITING`，并临时设置 `queueNumber = 0` 表示故障恢复优先
- `faultReason`、硬件电表读数和故障记录当前为临时模拟字段，因为项目尚无 `fault_report / maintenance_record` 表

可选请求体：

```json
{
  "faultReason": "设备离线",
  "chargedAmount": 8.5
}
```

如果 `chargedAmount` 为空，会使用会话中已有的 `chargedAmount`，当前本地模拟通常为 0。

`POST /api/piles/{pileId}/recover`

当前规则：

- 只有 `FAULT` 状态的桩可以恢复
- 恢复后 `pile.status = IDLE`
- 如果同类型队列中存在 `WAITING` 请求，会立即触发一次 SJF 调度
- 故障恢复请求会优先于普通请求；这是临时通过 `queueNumber = 0` 实现的

### 2.2 调度

`POST /api/scheduler/dispatch`

请求体：

```json
{
  "mode": "FAST"
}
```

当前规则使用 SJF：

- 查询同 `mode` 下的 `WAITING` 请求
- 优先选择故障恢复请求
- 计算 `remainingAmount = targetAmount - chargedAmount`
- 计算 `estimatedDurationMinutes = remainingAmount / pile.power * 60`
- 选择预计时长最短的请求
- 预计时长相同则选择 `createdAt` 更早的请求，避免长期等待
- 查询同 `mode` 下第一个 `IDLE` 充电桩
- 更新 `request.status = ASSIGNED`
- 更新 `request.assignedPileId = pileId`
- 更新 `pile.status = RESERVED`

接口响应里会返回：

- `algorithm = SJF`
- `remainingAmount`
- `estimatedDurationMinutes`
- `temporarySimulationNote`，当请求来自故障恢复优先队列时说明临时实现方式

错误返回：

- 没有等待请求：`当前模式下没有等待请求`
- 没有空闲充电桩：`当前模式下没有空闲充电桩`

`GET /api/scheduler/queue/{mode}`

返回：

- `mode`
- `queueLength`
- `availablePileCount`
- `estimatedWaitTime`
- `waitingList`

当前 `estimatedWaitTime` 按第一版保守返回 `0`。

队列返回顺序已经按当前调度优先级排序：故障恢复优先，其次 SJF。

### 2.3 充电会话

`POST /api/sessions/start`

请求体：

```json
{
  "requestId": 1001,
  "pileId": 1
}
```

当前规则：

- `request.status` 必须是 `ASSIGNED`
- `pile.status` 必须是 `RESERVED` 或 `IDLE`
- `pileId` 必须等于 `request.assignedPileId`
- 创建 `ChargingSession`
- 会话 `targetAmount` 使用剩余电量 `request.targetAmount - request.chargedAmount`，用于支持故障后的断点续充
- 更新 `request.status = CHARGING`
- 更新 `pile.status = CHARGING`

`POST /api/sessions/{sessionId}/stop`

请求体：

```json
{
  "chargedAmount": 30.0
}
```

当前规则：

- `session.status` 必须是 `CHARGING`
- 更新 `session.status = COMPLETED`
- 更新 `request.status = COMPLETED`
- 更新 `pile.status = IDLE`
- 生成一条 `Bill`，状态为 `UNPAID`

计费暂按 `api-spec.md` 第一版规则：

```text
electricityFee = chargedAmount * 0.8
serviceFee = chargedAmount * pile.serviceFee
totalFee = electricityFee + serviceFee
```

## 3. 对其他成员的依赖

### 3.1 B 需要提供

C 的调度依赖 B 创建的 `ChargingRequest`：

- `mode` 必须是 `FAST` 或 `SLOW`
- `status` 初始必须是 `WAITING`
- `targetAmount` 必须有值
- `userId`、`vehicleId` 必须有值
- `queueNumber` 建议按同 mode 下等待队列顺序生成

如果 B 修改请求字段或状态名，请同步 C。故障恢复优先级当前临时复用 `queueNumber = 0`，B 如果正式实现队列表/优先级字段，需要和 C 一起替换。

### 3.2 D 需要接管

C 当前在结束充电时生成最小 `Bill`，用于保证主流程不断。

D 后续如果实现 `BillService.generateBill()`，建议将 `SessionService.stop()` 中创建账单的逻辑替换为调用 D 的服务方法，但保持返回结构不变。

D 还需要继续实现：

- `GET /api/bills/user/{userId}`
- `GET /api/bills/{billId}`
- `POST /api/bills/{billId}/pay`

## 4. 已验证

使用 JDK 17 运行：

```bash
cd backend
mvn test
```

结果：编译通过。

本地启动后已验证：

- `GET /api/health`
- `POST /api/init`
- `GET /api/piles`
- `GET /api/scheduler/queue/FAST`
- `POST /api/scheduler/dispatch` 在无等待请求时返回统一错误
- `POST /api/piles/{pileId}/fault`
- `POST /api/piles/{pileId}/recover`

由于 B 的 `POST /api/charging-requests` 尚未实现，目前无法只通过公开接口创建等待请求，因此 `dispatch -> start -> stop` 的正向全流程还需要等 B 接口完成后联调。

## 5. 本地运行注意

本项目建议用 JDK 17。当前机器如果 `JAVA_HOME` 指向 JDK 24，Lombok 编译会失败。请将后端运行环境切到 JDK 17 后再执行 Maven。
