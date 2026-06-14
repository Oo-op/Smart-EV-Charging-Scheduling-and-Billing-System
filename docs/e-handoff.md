# E 模块交接文档：前端集成与管理端

> 负责人范围：**用户端页面**、**管理员端页面**、**Axios 接口封装**、**前后端联调**；后端 Admin 三个接口（`/api/admin/**`）。
> 本文记录 E 模块现状与对接点，接口定义以 `docs/api-spec.md` 为准。

## 1. 当前结论

E 模块采用**只读聚合 + 复用既有服务**的方式实现，不重复编写调度、故障处理等业务规则：

- 统一返回格式：`{ code, message, data }`
- 后端入口：`AdminController`（`/api/admin/**`）
- 业务层：`AdminService`，聚合 B/C/D 模块产生的数据
- 队列数据：直接调用 C 模块的 `SchedulerService.getQueue(mode)`
- 故障操作：管理端前端调用 C 模块已有的 `POST /api/piles/{pileId}/fault` 与 `/recover`，不在 Admin 接口中重复封装

第一版**未实现管理员登录鉴权**，所有 Admin 接口对本地开发环境开放；后续可由 A 统一接入 JWT / 角色校验。

## 2. 已实现接口

### 2.1 运营概览

`GET /api/admin/dashboard`

返回字段：

| 字段 | 说明 | 数据来源 |
|------|------|----------|
| `totalUsers` | 注册用户总数 | `UserRepository.count()` |
| `totalVehicles` | 绑定车辆总数 | `VehicleRepository.count()` |
| `totalRequests` | 充电请求总数 | `ChargingRequestRepository.count()` |
| `activeSessions` | 进行中会话数 | `ChargingSession` 且 `status = CHARGING` |
| `todayRevenue` | 今日收入（元） | 当天 `Bill.status = PAID` 的 `totalFee` 之和 |
| `pileSummary.idle` | 空闲桩数量 | `ChargingPile.status = IDLE` |
| `pileSummary.charging` | 使用中桩数量 | `CHARGING + RESERVED`（已分配待插枪视为占用） |
| `pileSummary.fault` | 故障桩数量 | `FAULT` |
| `pileSummary.offline` | 离线桩数量 | `OFFLINE` |

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "totalUsers": 10,
    "totalVehicles": 15,
    "totalRequests": 30,
    "activeSessions": 2,
    "todayRevenue": 360.5,
    "pileSummary": {
      "idle": 2,
      "charging": 2,
      "fault": 1,
      "offline": 0
    }
  }
}
```

说明：

- 仅执行 `POST /api/init`、尚无用户注册时，用户数 / 请求数等为 0 属于正常情况。
- `todayRevenue` 按服务器本地日期 `[00:00, 24:00)` 统计，只计入已支付账单。

### 2.2 充电桩监控

`GET /api/admin/piles`

在 C 模块 `GET /api/piles` 基础上，为**充电中**的桩附加 `currentSession`：

| 字段 | 说明 |
|------|------|
| `pileId` / `code` / `mode` / `power` / `status` | 与 `PileDTO` 一致 |
| `currentSession` | 桩为 `CHARGING` 时返回，否则为 `null` |
| `currentSession.sessionId` | 会话 ID |
| `currentSession.requestId` | 关联请求 ID |
| `currentSession.chargedAmount` | 已充电量（kWh） |
| `currentSession.targetAmount` | 本次会话目标电量 |
| `currentSession.startTime` | 开始时间 |

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "pileId": 1,
      "code": "F01",
      "mode": "FAST",
      "power": 60.0,
      "status": "CHARGING",
      "currentSession": {
        "sessionId": 5001,
        "requestId": 1001,
        "chargedAmount": 10.0,
        "targetAmount": 30.0,
        "startTime": "2026-06-13T10:10:00"
      }
    }
  ]
}
```

### 2.3 队列总览

`GET /api/admin/queue`

一次返回快充、慢充两条队列，内部调用 `SchedulerService.getQueue(FAST/SLOW)`，字段比 C 的 `GET /api/scheduler/queue/{mode}` 精简（不含 `algorithm`、`availablePileCount` 等调度细节）。

响应结构：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "fastQueue": {
      "mode": "FAST",
      "queueLength": 2,
      "waitingList": [
        {
          "requestId": 1001,
          "plateNumber": "京A12345",
          "targetAmount": 30.0,
          "queueNumber": 1,
          "status": "WAITING"
        }
      ]
    },
    "slowQueue": {
      "mode": "SLOW",
      "queueLength": 1,
      "waitingList": []
    }
  }
}
```

队列排序与 C 模块 SJF 调度一致：故障恢复优先 → 预计时长最短 → `createdAt` 更早。

## 3. 已实现前端

### 3.1 全局导航与 Axios

| 文件 | 说明 |
|------|------|
| `frontend/src/api/request.js` | Axios 实例 + `{code,message,data}` 统一拦截 |
| `frontend/src/api/index.js` | **全量业务 API 封装**（B/C/D/E 主流程接口） |
| `frontend/src/api/enums.js` | 状态枚举中文映射 |
| `frontend/src/App.vue` | 顶栏导航：首页 / 用户端 / 管理端 / 充电站 |

`api/index.js` 已封装接口清单：

```text
B: registerUser, loginUser, bindVehicle, getUserVehicles
   submitChargingRequest, getChargingRequest, getUserChargingRequests
   cancelChargingRequest, modifyChargingRequest
C: getSchedulerQueue, dispatchScheduler, getPiles, markPileFault, recoverPile
   startSession, stopSession
D: getUserBills, getBillDetail, payBill
E: getAdminDashboard, getAdminPiles, getAdminQueue
公共: getHealth, initSystem, getStations, getEnums, getPrices, calcFee
```

### 3.2 用户端页面

| 路径 | 文件 | 说明 |
|------|------|------|
| `/user` | `frontend/src/views/User.vue` | 用户充电全流程 |

用户端能力（对应 `api-spec.md` 10.1）：

1. 注册 / 登录（登录态存 `localStorage`）
2. 绑定车辆、选择车辆
3. 提交 FAST/SLOW 充电请求
4. 查看队列、触发调度
5. 开始充电、结束充电（输入电量）
6. 账单列表、模拟微信支付
7. 取消 / 修改等待中请求
8. **我的订单**：历史充电请求列表（`GET /api/users/{userId}/charging-requests`），可继续进行中的订单

### 3.3 管理端页面

| 路径 | 文件 | 说明 |
|------|------|------|
| `/admin` | `frontend/src/views/Admin.vue` | 管理后台主页 |
| — | `frontend/src/components/QueueList.vue` | 队列列表子组件 |
| `/` | `frontend/src/views/Home.vue` | 首页入口与初始化 |

管理页能力（对应 `api-spec.md` 10.2）：

- 运营概览卡片（用户数、车辆、请求、会话、今日收入、桩状态分布）
- 充电桩表格（状态中文、当前会话进度）
- **标记故障** → `POST /api/piles/{pileId}/fault`
- **恢复** → `POST /api/piles/{pileId}/recover`
- 快充 / 慢充等待队列
- 「刷新全部」并行拉取三个 Admin 接口

状态文案通过 `getStatusDesc(...)` 统一映射，不硬编码中文。

## 4. 代码结构

```text
backend/src/main/java/com/example/charging/
├── controller/AdminController.java      # GET /admin/dashboard|piles|queue
├── service/AdminService.java            # 聚合统计与 DTO 转换
└── dto/
    ├── AdminDashboardDTO.java
    ├── AdminPileDTO.java
    ├── AdminPileSessionDTO.java
    ├── AdminQueueDTO.java
    ├── AdminQueueModeDTO.java
    ├── AdminQueueItemDTO.java
    └── PileSummaryDTO.java

frontend/src/
├── App.vue                              # 全局导航
├── views/
│   ├── Home.vue                         # 首页 / 初始化
│   ├── User.vue                         # 用户端主流程
│   ├── Admin.vue                        # 管理端
│   └── Stations.vue
├── components/QueueList.vue
└── api/
    ├── request.js                       # Axios 拦截器
    ├── index.js                         # 全量 API 封装
    └── enums.js
```

新增 Repository 方法（供统计使用）：

- `BillRepository.findByStatusAndCreatedAtBetween`
- `ChargingSessionRepository.countByStatus`
- `ChargingPileRepository.countByStatus`

## 5. 对其他成员的依赖

### 5.1 依赖 B 模块

- 用户、车辆、充电请求数据决定 Dashboard 中的 `totalUsers`、`totalVehicles`、`totalRequests`
- 队列 `waitingList` 中的 `plateNumber` 来自 B 绑定的 `Vehicle`

### 5.2 依赖 C 模块

- 队列视图完全复用 `SchedulerService`
- 桩状态、会话、故障/恢复操作依赖 C 的 `PileService` / `SessionService`
- C 若调整队列字段或故障恢复优先级（如替换 `queueNumber = 0` 临时方案），E 的队列展示会自动跟随，无需改 Admin 接口

### 5.3 依赖 D 模块

- `todayRevenue` 依赖 D 实现的支付流程：用户支付后 `Bill.status = PAID`
- 若 D 改用 `FeeService` 分时计费，只要 `Bill.totalFee` 与 `PAID` 状态正确，Dashboard 收入统计无需改动

### 5.4 与 A 的协作点

- 公共规范（`ApiResponse`、枚举、CORS）由 A 维护；E 未新增枚举值
- 管理员鉴权、角色 `ADMIN` 校验建议由 A 在第二版统一加在 Filter 或拦截器上
- 集成联调时，管理员侧流程见 `api-spec.md` 10.2 节

## 6. 浏览器联调验收（推荐答辩演示顺序）

1. 启动后端 + 前端，打开 `http://localhost:5173`
2. **首页** → 健康检查 → **初始化系统**
3. **用户端** `/user`：
   - 注册 → 登录 → 绑车 → 提交 FAST 30kWh 请求
   - 刷新队列 → **执行调度** → **开始充电** → **结束充电**
   - 在账单区 **微信支付**
   - 在 **我的订单** 查看历史请求与进行中订单
4. **管理端** `/admin` → 刷新全部：
   - `totalUsers`、`totalRequests`、`todayRevenue` 应更新
   - 桩列表、队列与刚才操作一致
5. （可选）管理端对空闲桩 **标记故障 → 恢复**

与 curl 等价的 Admin 验证：

```bash
# 1. 运营概览
curl http://localhost:8080/api/admin/dashboard

# 2. 充电桩监控
curl http://localhost:8080/api/admin/piles

# 3. 队列总览
curl http://localhost:8080/api/admin/queue

# 4. 标记故障（示例 pileId=1）
curl -X POST http://localhost:8080/api/piles/1/fault \
  -H "Content-Type: application/json" \
  -d '{"faultReason":"设备离线"}'

# 5. 故障恢复
curl -X POST http://localhost:8080/api/piles/1/recover
```

完整演示（含用户侧主流程 + 管理端）：

1. 控制台初始化系统 → `POST /api/init`
2. 用户侧：注册 → 绑车 → 提交请求 → 调度 → 开始/结束充电 → 支付（B/C/D 联调）
3. 打开 `http://localhost:5173/admin`，确认概览数字、桩状态、队列与 curl 一致
4. 在管理页对空闲桩标记故障，再恢复，观察队列与桩状态变化

## 7. 已完成与待办

### 已完成

**后端 Admin**
- [x] `GET /api/admin/dashboard`
- [x] `GET /api/admin/piles`（含 `currentSession`）
- [x] `GET /api/admin/queue`（FAST + SLOW）

**前端集成（E 全部任务）**
- [x] Axios 全量 API 封装（`api/index.js`，覆盖 B/C/D/E 主流程）
- [x] 用户端页面 `/user`（注册→绑车→请求→调度→充电→账单→支付、我的订单）
- [x] 管理端页面 `/admin`
- [x] 全局导航、`Home` 入口
- [x] 浏览器内前后端主流程 + 管理端联调

### 待办（后续迭代）

- [ ] 管理员登录与 `ADMIN` 角色鉴权
- [ ] 营收报表扩展（按日/周/月、分时收入曲线）
- [ ] 平均等待时间、车流分析等设计说明中的高级监控指标
- [ ] 计费规则维护界面（依赖正式 `billing_tariff` 表）
- [ ] 故障记录 / 维护记录查询（依赖 `fault_report` / `maintenance_record` 表）
- [ ] 管理端分页列表：全部请求、全部账单、全部用户

## 8. 本地运行注意

- 后端默认 H2 内存库，重启后需重新 `POST /api/init` 并跑一遍主流程才有完整演示数据
- 建议使用 JDK 17 编译后端（见 `docs/c-handoff.md` 第 5 节）
- 前端开发地址：`http://localhost:5173`（用户端 `/user`，管理端 `/admin`），Vite 已将 `/api` 代理到 `8080`
