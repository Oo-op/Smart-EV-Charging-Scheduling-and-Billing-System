# 智能充电系统 · 项目开发指南

> 本文档说明：**当前项目已有什么**、**后续成员如何进行开发**、**开发前需要检查和确认什么**。
> 请所有成员在写第一行代码前阅读。

相关文档：
- [api-spec.md](./api-spec.md) — 接口规范（前后端通用，包含完整的请求 / 响应定义与状态枚举）

---

## 0. 当前阶段说明

这个仓库现在是“第一阶段基础骨架”：

- 目标：先把前后端项目结构、统一响应、基础数据模型和少量核心接口跑通
- 现状：已经能本地启动并验证健康检查、初始化演示数据、充电站查询、枚举查询、计费查询
- 未完成：用户、车辆、充电请求、调度、会话、账单支付主流程仍需继续补

### 0.1 本地启动方式

后端默认使用 H2 内存数据库，不依赖本地 MySQL：

```bash
cd backend
mvn spring-boot:run
```

前端：

```bash
cd frontend
npm install
npm run dev
```

如果要接本地 MySQL，使用 `mysql` profile：

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

说明：

- `application.yml` 是默认本地开发配置，优先保证“开箱可跑”
- `application-mysql.yml` 保留 MySQL 接入配置
- `schema.sql` 当前作为 MySQL 结构参考，不再作为默认启动脚本执行

---

## 1. 项目已有什么

### 1.1 目录结构

```
smart-charging-system/
├── backend/
│   ├── pom.xml
│   └── src/main/java/com/example/charging/
│       ├── controller/             # REST API 入口（Enum / Fee / Health / Init / Station）
│       ├── service/                # 业务逻辑层（FeeService / HealthService）
│       ├── repository/             # JPA Repository（10 张表的 CRUD 接口）
│       ├── entity/                 # JPA 实体（User / Vehicle / ChargingStation / ChargingPile
│       │                           #   ChargingRequest / ChargingSession / Bill / Payment / ElectricityPrice）
│       ├── dto/                    # 数据传输对象（FeeCalcRequest / FeeCalcResult / StationDTO）
│       ├── enums/                  # 统一枚举（与 api-spec.md 1.3 保持一致；开发前务必阅读）
│       ├── common/                 # ApiResponse + IgnoreResponseWrap 注解
│       ├── config/                 # CorsConfig / GlobalResponseHandler / GlobalExceptionHandler
│       └── ChargingApplication.java
├── backend/src/main/resources/
│   ├── application.yml             # 默认本地开发配置（H2 内存库）
│   ├── application-mysql.yml       # 可选 MySQL profile
│   └── schema.sql                  # MySQL 结构参考 SQL
├── frontend/
│   ├── package.json                # Vue 3 + Vue Router + Axios + Vite
│   ├── vite.config.js              # dev 端口 5173，/api 代理到 http://localhost:8080
│   ├── index.html
│   └── src/
│       ├── main.js
│       ├── App.vue
│       ├── api/request.js          # 统一 axios 实例（自动拦截 ApiResponse）
│       ├── api/index.js            # 已封装：getHealth / initSystem / getPrices / calcFee / getStations / getEnums
│       ├── api/enums.js            # 前端状态 → 中文映射（与后端 enum 值一一对应）
│       ├── router/index.js         # 路由：/（Home）、/stations
│       ├── views/Home.vue
│       ├── views/Stations.vue
│       └── components/StationCard.vue
├── docs/
│   ├── api-spec.md                 # 📖 当前团队约定的接口规范（主流程 11 个模块）
│   └── development-guide.md        # 本文件
├── README.md
└── .gitignore
```

### 1.2 已实现的后端能力（开箱可用）

| 能力 | 入口 | 说明 |
|---|---|---|
| 统一响应 | `common/ApiResponse` + `config/GlobalResponseHandler` | Controller 直接返回业务对象，框架自动包成 `{code, message, data}` |
| 全局异常 | `config/GlobalExceptionHandler` | 未捕获异常统一转为 `{code, message}`，前端通过拦截器统一提示 |
| 计费服务 | `service/FeeService` | 已实现**按小时切分跨峰/平/谷**的分时计费；`electricityFee + serviceFee = totalFee` |
| 初始化接口 | `POST /api/init` | 一键生成 1 个充电站 + 2 快充 + 3 慢充 + 9 条 (period, mode) 默认电价 |
| 枚举查询 | `GET /api/enums` | 所有状态枚举的 `code / desc` 映射，前端页面展示状态文案时请走该接口 |
| 电价查询 | `GET /api/fees/prices` | 所有 `(period, mode)` 电价 |

### 1.3 统一后的状态枚举（已与 api-spec.md 对齐 ✅）

⚠️ **开发纪律**：新增或改动状态值时，必须**同时更新 4 处**，否则联调会出现“状态不匹配 / 前端显示空字符串”。

1. `backend/src/main/java/com/example/charging/enums/<你的枚举>.java`（code 字段即为数据库存储值）
2. `backend/src/main/java/com/example/charging/controller/EnumController.java`（若新增了一套状态集合，需加一条 `put(...)`）
3. `frontend/src/api/enums.js` 中对应对象
4. `docs/api-spec.md` 第 1.3 节的枚举清单

**当前统一值**：

| 模块 | 值 | 说明 | 影响文件 |
|---|---|---|---|
| ChargingRequestStatus | `WAITING / ASSIGNED / CHARGING / CANCELLED / COMPLETED` | 用户发起的充电请求；排队时使用 WAITING / ASSIGNED | `enums/ChargingRequestStatus.java`、`entity/ChargingRequest`、`charging-request` 接口 |
| ChargingPileStatus | `IDLE / RESERVED / CHARGING / FAULT / OFFLINE` | 充电桩硬件状态；`RESERVED` 表示"已被请求占用，等待车辆插枪" | `enums/ChargingPileStatus.java`、`entity/ChargingPile`、`/api/piles` 接口 |
| ChargingSessionStatus | `CHARGING / COMPLETED / INTERRUPTED` | 一次插枪→拔枪的会话；三态覆盖主流程 | `enums/ChargingSessionStatus.java`、`entity/ChargingSession` |
| BillStatus | `UNPAID / PAID / CANCELLED` | 订单维度的收费生命周期；退款等衍生状态放第二版 | `enums/BillStatus.java`、`entity/Bill` |
| PaymentStatus | `PENDING / SUCCESS / FAILED` | 支付流水三态 | `enums/PaymentStatus.java`、`entity/Payment` |
| ChargeMode | `FAST / SLOW`（第一版） | NORMAL / SMART / SCHEDULED 保留给第二版扩展 | `enums/ChargeMode.java`、请求 / 会话 / 计费入参 |
| ElectricityPrice | 每条按 `(period, mode)` 唯一 | 计费逻辑按 session.pile.mode + 当前 hour 对应 PricePeriod 查表 | `entity/ElectricityPrice.java`、`repository/ElectricityPriceRepository.java`、`service/FeeService.java` |
| PricePeriod | `PEAK / FLAT / VALLEY` | 与电价表配合使用 | `enums/PricePeriod.java` |
| StationStatus | `AVAILABLE / OCCUPIED / MAINTENANCE / OFFLINE` | 充电站维度状态 | `enums/StationStatus.java`、`entity/ChargingStation` |
| User.role | `USER / ADMIN` | 用户角色；默认值 OK | `entity/User.java` |

**前端同步**：`frontend/src/api/enums.js` 中的 `STATION_STATUS / CHARGING_REQUEST_STATUS / CHARGING_PILE_STATUS / CHARGING_SESSION_STATUS / BILL_STATUS / CHARGE_MODE / PAYMENT_STATUS / PRICE_PERIOD` 已与上述表完全一致，并在 `ENUM_MAP` 中暴露给 `getStatusDesc('chargingPileStatus', code)` 使用。

---

## 2. 后续成员如何进行开发

### 2.1 本地运行步骤（含重启注意事项）

> 🔴 **重启前必须停掉旧进程**。macOS 下可用 `lsof -i:8080` / `lsof -i:5173` 查看端口占用，若已存在则先 `kill`，再启动。

1. **安装依赖 / 环境**
   - JDK 17+（建议 `java -version` 先核对）
   - Maven 3.8+
   - Node 18+
   - MySQL 8+

2. **创建数据库**
   ```sql
   CREATE DATABASE IF NOT EXISTS smart_charging DEFAULT CHARSET utf8mb4;
   ```
   `application.yml` 默认账号 `root / root`。如需自定义，建议复制为 `application-local.yml` 并通过 `--spring.profiles.active=local` 启动，避免污染共享的 `application.yml`。

3. **执行建表脚本**
   - 直接在 MySQL 客户端里跑 `backend/src/main/resources/schema.sql`（DDL 按依赖顺序组织，直接粘贴即可）；
   - 或依赖 JPA `ddl-auto: update` 自动建表；**但默认电价和示例桩必须通过下一步的 `/api/init` 写入**。

4. **启动后端**
   ```bash
   cd backend
   mvn spring-boot:run
   # 或 IDEA 中直接运行 ChargingApplication.main()
   # 启动成功标志：能访问 http://localhost:8080/api/health
   ```
   - 由于 `server.servlet.context-path: /api`，所有后端接口统一以 `/api` 开头（见 application.yml 18–21 行）。

5. **启动前端**
   ```bash
   cd frontend
   npm install        # 首次拉取必跑
   npm run dev        # http://localhost:5173
   ```
   - Vite 已配置代理：`/api` → `http://localhost:8080`（见 `vite.config.js`）。业务代码里写 `request.get('/health')` 会被正确转发。

6. **初始化数据**（启动后必调一次）
   ```bash
   curl -X POST http://localhost:8080/api/init
   ```
   调用后会写入：1 个充电站、5 个充电桩（FAST 2 台、SLOW 3 台）、9 条 `(period, mode)` 默认电价。
   - 如需不同的桩数 / 功率 / 服务费，可传 body：
     ```json
     {"fastPiles": 4, "slowPiles": 6, "fastPower": 90, "forcePiles": true}
     ```

7. **确认健康检查**
   - `curl http://localhost:8080/api/health` 应返回 `{"code":0,"message":"success","data":{"status":"UP","service":"…"}}`
   - `curl http://localhost:8080/api/enums` 应返回包含 `chargingRequestStatus / chargingPileStatus / ...` 的字典。

### 2.2 三层架构分工约定

| 层 | 职责 | 必须做 | 禁止做 |
|---|---|---|---|
| `@RestController` | 接收 HTTP、校验参数、调用 Service、返回数据 | 方法返回业务 POJO（自动包装）或显式 `ApiResponse.success(...)`；必须声明 `@RequestMapping` 前缀 | 不要写 SQL、不要写多层业务 if-else（下沉到 Service） |
| `@Service` | 状态流转、业务规则、事务边界、调用 1~N 个 Repository | 方法名清晰（如 `createRequest`、`assignPile`、`stopSession`）；所有状态值从 `enums/*` 取 | 不要直接操作 Servlet / HTTP 响应 |
| Repository | 只做数据库读写 | JPA 命名查询（如 `findByModeAndStatusInOrderByCreatedAtAsc`） | 不要写业务分支 |

**最小提交粒度建议**：Entity（如必要） + Repository 方法 + Service 方法 + Controller 方法 + 对应前端 `api/index.js` 中一个函数 = 一个 PR；PR 标题建议引用 `api-spec.md` 小节号（例如 `feat: 4.1 提交充电请求`）。

### 2.3 前端新增接口的约定

1. 在 `frontend/src/api/index.js` 中新增一个命名函数（如 `submitChargingRequest(payload)`），返回 `request.post('/charging-requests', payload)`；
2. 请求/响应拦截器已在 `request.js` 中实现：当 `code === 0` 时 resolve `data`，否则 reject `message`。业务层直接拿数据即可；
3. 页面展示状态列时，**不要硬编码中文**，通过 `getStatusDesc('chargingPileStatus', code)` 查表取中文；
4. 所有接口路径必须与 `docs/api-spec.md` 完全一致，不私自发明路径。

### 2.4 统一返回格式——你只需要做一件事

**Controller 里直接返回业务对象或 `ApiResponse.success(...)` 即可**，其它交给 `GlobalResponseHandler`：

```java
// ✅ 推荐：返回业务 DTO，框架自动包裹
@GetMapping("/{id}")
public BillDTO getById(@PathVariable Long id) {
    return billService.findById(id);
}

// ✅ 需要自定义 message：显式返回 ApiResponse
@PostMapping("/{id}/pay")
public ApiResponse<PaymentDTO> pay(@PathVariable Long id,
                                    @RequestBody PayDTO payload) {
    PaymentDTO p = billService.pay(id, payload);
    return ApiResponse.success("支付成功", p);
}

// ✅ 错误处理：直接 throw IllegalArgumentException，由 GlobalExceptionHandler 转成 ApiResponse
@PostMapping("/{id}/cancel")
public void cancel(@PathVariable Long id) {
    if (!requestService.canCancel(id)) {
        throw new IllegalArgumentException("当前状态不允许取消");
    }
    requestService.cancel(id);
}

// ⚠️ 文件下载等不走统一响应的场景，标注 @IgnoreResponseWrap
@GetMapping("/export")
@IgnoreResponseWrap
public ResponseEntity<Resource> export() { ... }
```

---

## 3. 开发前一定要检查和确认的事情

这一节是本文件的**核心目的**。请在提交第一个 PR 前逐项打钩。

### 3.1 规范对齐

- [ ] 你负责的接口路径 / 方法是否与 `docs/api-spec.md` 完全一致？
- [ ] 你使用的状态值是否在 `docs/api-spec.md` 第 1.3 节的枚举清单里？
- [ ] 如果需要新增一个状态值，是否已按 1.3 节"必须同时更新 4 处"的纪律落地？
- [ ] Controller 是否遵循"只调 Service、不写 SQL / 不写深层业务 if-else"？

### 3.2 枚举与数据库

- [ ] 若你修改了枚举，请确认：
  - `backend/.../enums/*.java` 中的 `code` 与数据库 `status` 列存储的字符串一致；
  - `ElectricityPrice.mode` 与 `ChargingPile.mode`、`ChargingRequest.mode` 使用同一套 `ChargeMode`；
  - 前端 `ENUM_MAP`（`api/enums.js`）同步更新。
- [ ] 新增 / 修改表字段时，请同时：
  - 更新 `schema.sql`（确保新成员一键初始化可用）；
  - 更新对应 `entity` 与 DTO（若有）。

### 3.3 主流程联调接口清单（第一版）

参考 `docs/api-spec.md` 第 10 节，任何一个接口 404 都说明"有人跳过了实现"：

```
1. POST /api/users/register
2. POST /api/users/login
3. POST /api/vehicles
4. POST /api/charging-requests
5. GET  /api/scheduler/queue/{mode}
6. POST /api/scheduler/dispatch
7. POST /api/sessions/start
8. POST /api/sessions/{sessionId}/stop
9. GET  /api/bills/user/{userId}
10. GET  /api/bills/{billId}
11. POST /api/bills/{billId}/pay
```

若你只负责模块 B（例如用户 / 车辆），也请把上述链的"前 3 步 + 提交请求"跑通一次，并写一个最小可用的前端按钮来调通。

### 3.4 编码与工具约定

- [ ] IDE 统一 UTF-8；
- [ ] 日期统一用 `LocalDateTime`（后端）+ ISO-8601 字符串（前端）；禁止 `yyyy-MM-dd hh:mm:ss` 自定义格式；
- [ ] 金额统一 `BigDecimal`，计算时显式指定 `RoundingMode.HALF_UP`；
- [ ] 提交前本地跑一次 `mvn clean package`，确保无编译错误；
- [ ] Git 分支命名：`feat/{模块}-{接口}`、`fix/{模块}-{问题}`，合并到 `main` 前走 PR Review。

### 3.5 必须手动核对的默认参数

以下参数已写进代码，但与业务 / 运营商紧密相关。请在团队内确认一次：

| 参数 | 当前值 | 位置 |
|---|---|---|
| 充电桩数量（默认初始化） | 2 快 + 3 慢 | `controller/InitController.java` |
| 快充功率 / 服务费 | 60 kW / 0.70 元/度 | `InitController.java`、`entity/ElectricityPrice.java` |
| 慢充功率 / 服务费 | 7 kW / 0.40 元/度 | `InitController.java`、`entity/ElectricityPrice.java` |
| 高峰电费（默认） | 1.20 元/度 | `resources/schema.sql` INSERT |
| 平峰电费（默认） | 0.80 元/度 | `resources/schema.sql` INSERT |
| 低谷电费（默认） | 0.40 元/度 | `resources/schema.sql` INSERT |
| 时段归属规则 | `hour ∈ [8,11] ∪ [18,21] → PEAK；[12,17] → FLAT；其它 → VALLEY` | `enums/PricePeriod.java` |
| 计费公式 | `electricityFee = chargedAmount × period.chargingFee`<br>`serviceFee = chargedAmount × period.serviceFee`<br>`totalFee = electricityFee + serviceFee` | `service/FeeService.java` |

### 3.6 验收标准（对应 api-spec.md 第 12 节）

- [ ] 所有接口都返回 `{code, message, data}` 结构；
- [ ] 所有状态字段都使用统一枚举；
- [ ] 主流程（注册 → 登录 → 绑定车辆 → 提交请求 → 调度 → 开始充电 → 结束 → 账单 → 支付）可以在**浏览器 + 本地后端**一键跑通；
- [ ] 日志清晰、入参和状态变化可追溯。

---

## 4. 推荐的下一步推进顺序

按以下顺序推进，可最小化联调阻塞：

1. **B 先实现用户 / 车辆 / 充电请求模块**：这是主流程的起点；把 `ChargingRequest.status` 从 WAITING → ASSIGNED 的流转接口先搭起来。
2. **C 并行实现充电桩 / 调度 / 会话模块**：与 B 的请求模块有状态交集，先把 `request.status = ASSIGNED`、`pile.status = IDLE → RESERVED → CHARGING → IDLE` 以及 `session.status = CHARGING → COMPLETED / INTERRUPTED` 的状态流转写清楚。
3. **D 负责账单与支付**：依赖 C 的 session，在结束充电时通过 `FeeService.calculate` 生成 Bill，并实现 `/api/bills/{billId}/pay`。
4. **E 做管理端 Dashboard**：复用上述所有模块的查询接口。

对某个接口的语义 / 字段含义有疑问时，**先在 `docs/api-spec.md` 里找答案**；仍然不清的，在团队群里 @ A 确认后再开发，避免返工。
