# 智能充电系统 — 架构与数据库设计文档

> 本文档为项目架构总览，包含技术栈、模块划分、数据库设计、配置说明等核心内容。

---

## 1. 技术栈

### 1.1 后端（Java）

| 组件 | 版本 | 说明 |
|---|---|---|
| Spring Boot | 3.2.0 | 框架核心 |
| Spring Data JPA | 3.2.x | 数据访问层 |
| MySQL Connector | 8.0.x | MySQL 驱动（运行时）|
| H2 Database | 2.2.x | 内存数据库（开发/测试）|
| Lombok | 1.18.46 | 简化 POJO 代码 |
| Java | 17 | 语言版本 |

### 1.2 前端（JavaScript）

| 组件 | 版本 | 说明 |
|---|---|---|
| Vue | 3.4.x | 前端框架 |
| Vue Router | 4.2.x | 路由管理 |
| Axios | 1.6.x | HTTP 请求 |
| Vite | 4.5.x | 构建工具 |

### 1.3 数据库

| 类型 | 用途 |
|---|---|
| MySQL 8.x | 生产环境 |
| H2 内存库 | 开发/测试（当前默认配置）|

---

## 2. 项目结构

```
EV/                                    # Git 仓库根目录
├── backend/                           # 后端（Spring Boot）
│   ├── pom.xml                        # Maven 依赖配置
│   └── src/main/java/com/example/charging/
│       ├── ChargingApplication.java   # 启动类
│       ├── controller/                # REST API 入口（12个控制器）
│       ├── service/                   # 业务逻辑层（11个服务）
│       ├── repository/                # JPA 数据访问层（9个仓库）
│       ├── entity/                    # JPA 实体（9个）
│       ├── dto/                       # 数据传输对象（请求/响应）
│       ├── enums/                     # 统一枚举（8个）
│       ├── common/                    # 通用组件（ApiResponse）
│       └── config/                    # 配置类（CORS/全局响应/异常）
│   └── src/main/resources/
│       ├── application.yml            # 应用配置
│       └── schema.sql                 # 建表 DDL + 默认电价
├── frontend/                          # 前端（Vue 3）
│   ├── package.json                   # 依赖配置
│   ├── vite.config.js                 # Vite 配置（代理）
│   ├── index.html
│   └── src/
│       ├── main.js                    # 入口文件
│       ├── App.vue                    # 根组件
│       ├── router/index.js            # 路由配置
│       ├── api/request.js             # Axios 实例（拦截器）
│       ├── api/index.js               # API 方法封装
│       ├── api/enums.js               # 前端状态映射
│       ├── views/                     # 页面视图
│       └── components/                # 可复用组件
├── docs/                              # 文档
│   ├── api-spec.md                    # 接口规范
│   └── development-guide.md           # 开发指南
└── README.md
```

---

## 3. 后端架构

### 3.1 三层架构

```
Controller（接收HTTP请求，参数校验）
    ↓ 调用 Service
Service（业务逻辑，事务管理，状态流转）
    ↓ 调用 Repository
Repository（数据访问，JPA 命名查询）
    ↓ 操作
Database（MySQL/H2）
```

### 3.2 Controller 层（12个）

| 控制器 | 路径前缀 | 职责 |
|---|---|---|
| `UserController` | `/users` | 用户注册、登录、信息 |
| `VehicleController` | `/vehicles` | 车辆绑定、查询、删除 |
| `StationController` | `/stations` | 充电站查询 |
| `PileController` | `/piles` | 充电桩查询、状态管理 |
| `ChargingRequestController` | `/charging-requests` | 用户充电请求 |
| `SchedulerController` | `/scheduler` | 调度、队列管理 |
| `SessionController` | `/sessions` | 充电会话（开始/停止）|
| `BillController` | `/bills` | 账单查询、支付 |
| `FeeController` | `/fees` | 计费计算、电价查询 |
| `AdminController` | `/admin` | 管理端仪表盘 |
| `EnumController` | `/enums` | 枚举值查询（状态映射）|
| `HealthController` | `/health` | 健康检查 |
| `InitController` | `/init` | 初始化演示数据 |

### 3.3 Service 层（11个）

| 服务 | 职责 |
|---|---|
| `UserService` | 用户注册、登录认证 |
| `VehicleService` | 车辆管理 |
| `PileService` | 充电桩状态管理 |
| `ChargingRequestService` | 充电请求生命周期 |
| `SchedulerService` | 调度算法、队列管理 |
| `SessionService` | 充电会话管理 |
| `BillService` | 账单生成、支付 |
| `FeeService` | 分时计费计算（峰/平/谷）|
| `AdminService` | 管理端数据聚合 |
| `HealthService` | 健康检查 |

### 3.4 统一响应与异常处理

**统一响应格式**（`common/ApiResponse.java`）：
```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

- `code = 0`：成功
- `code ≠ 0`：失败，`message` 包含错误信息
- `GlobalResponseHandler` 自动包装所有 Controller 返回值
- `GlobalExceptionHandler` 捕获未处理异常并统一响应

### 3.5 枚举系统（8个）

| 枚举 | 值 | 用途 |
|---|---|---|
| `ChargeMode` | FAST / SLOW / NORMAL / SMART / SCHEDULED | 充电模式 |
| `ChargingRequestStatus` | WAITING / ASSIGNED / CHARGING / CANCELLED / COMPLETED | 请求状态 |
| `ChargingPileStatus` | IDLE / RESERVED / CHARGING / FAULT / OFFLINE | 桩状态 |
| `ChargingSessionStatus` | CHARGING / COMPLETED / INTERRUPTED | 会话状态 |
| `BillStatus` | UNPAID / PAID / CANCELLED | 账单状态 |
| `PaymentStatus` | PENDING / SUCCESS / FAILED | 支付状态 |
| `PricePeriod` | PEAK / FLAT / VALLEY | 电价时段 |
| `StationStatus` | AVAILABLE / OCCUPIED / MAINTENANCE / OFFLINE | 站点状态 |

---

## 4. 前端架构

### 4.1 路由配置

| 路径 | 组件 | 说明 |
|---|---|---|
| `/` | `Home.vue` | 首页（登录/注册）|
| `/stations` | `Stations.vue` | 充电站/桩列表 |
| `/admin` | `Admin.vue` | 管理端仪表盘 |

### 4.2 API 封装

**请求拦截器**（`api/request.js`）：
- 自动携带 `Authorization` token（如有）
- 响应拦截器自动解析 `ApiResponse`：
  - `code === 0` → resolve `data`
  - `code !== 0` → reject `message`

**API 方法**（`api/index.js`）：
- 已封装：`login`、`register`、`getStations`、`getPiles`、`submitRequest`、`calcFee`、`getEnums`、`initSystem` 等

**状态映射**（`api/enums.js`）：
- 提供 `getStatusDesc(type, code)` 获取中文描述
- 与后端枚举值一一对应

---

## 5. 数据库设计

### 5.1 表关系图

```
app_user 1:n vehicle
app_user 1:n charging_request
app_user 1:n bill
app_user 1:n payment

vehicle 1:n charging_request
vehicle 1:n charging_session

charging_station 1:n charging_pile

charging_pile 1:n charging_session
charging_pile 1:n charging_request (assigned_pile_id)

charging_request 1:1 charging_session

charging_session 1:1 bill

bill 1:n payment

electricity_price (period, mode) → 计费查询
```

### 5.2 表结构详情

#### 表 1：app_user（用户表）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PRIMARY KEY AUTO_INCREMENT | 用户ID |
| username | VARCHAR(50) | NOT NULL UNIQUE | 用户名 |
| password | VARCHAR(100) | NOT NULL | 密码（加密）|
| phone | VARCHAR(20) | - | 手机号 |
| role | VARCHAR(20) | DEFAULT 'USER' | 角色（USER/ADMIN）|
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | DEFAULT CURRENT_TIMESTAMP ON UPDATE | 更新时间 |

#### 表 2：vehicle（车辆表）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PRIMARY KEY AUTO_INCREMENT | 车辆ID |
| user_id | BIGINT | NOT NULL | 所属用户 |
| plate_number | VARCHAR(20) | NOT NULL | 车牌号 |
| model | VARCHAR(50) | - | 车型 |
| battery_capacity | DECIMAL(10,2) | - | 电池容量（kWh）|

#### 表 3：charging_station（充电站）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PRIMARY KEY AUTO_INCREMENT | 站点ID |
| name | VARCHAR(128) | NOT NULL | 站点名称 |
| address | VARCHAR(256) | NOT NULL | 地址 |
| status | VARCHAR(32) | DEFAULT 'AVAILABLE' | 状态 |

#### 表 4：charging_pile（充电桩）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PRIMARY KEY AUTO_INCREMENT | 桩ID |
| station_id | BIGINT | - | 所属站点 |
| code | VARCHAR(50) | NOT NULL UNIQUE | 桩编码 |
| mode | VARCHAR(20) | NOT NULL | 模式（FAST/SLOW）|
| power | DECIMAL(10,2) | NOT NULL | 功率（kW）|
| status | VARCHAR(20) | DEFAULT 'IDLE' | 状态 |
| service_fee | DECIMAL(10,2) | DEFAULT 0 | 服务费（元/度）|
| enabled | BOOLEAN | DEFAULT TRUE | 是否启用 |
| open_queue_slots | INT | DEFAULT 3 | 当前开放排队位数 |
| max_queue_slots | INT | DEFAULT 3 | 最大排队位数 |

#### 表 5：charging_request（充电请求）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PRIMARY KEY AUTO_INCREMENT | 请求ID |
| user_id | BIGINT | NOT NULL | 用户ID |
| vehicle_id | BIGINT | NOT NULL | 车辆ID |
| mode | VARCHAR(20) | NOT NULL | 充电模式 |
| target_amount | DECIMAL(10,2) | NOT NULL | 目标充电量（度）|
| charged_amount | DECIMAL(15,8) | DEFAULT 0 | 已充量 |
| status | VARCHAR(20) | DEFAULT 'WAITING' | 状态 |
| queue_number | INT | - | 排队号 |
| assigned_pile_id | BIGINT | - | 分配的桩ID |

#### 表 6：charging_session（充电会话）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PRIMARY KEY AUTO_INCREMENT | 会话ID |
| request_id | BIGINT | - | 关联请求 |
| user_id | BIGINT | NOT NULL | 用户ID |
| vehicle_id | BIGINT | NOT NULL | 车辆ID |
| pile_id | BIGINT | NOT NULL | 充电桩ID |
| start_time | DATETIME | - | 开始时间 |
| end_time | DATETIME | - | 结束时间 |
| target_amount | DECIMAL(10,2) | - | 目标量 |
| charged_amount | DECIMAL(15,8) | DEFAULT 0 | 已充量 |
| status | VARCHAR(20) | DEFAULT 'CHARGING' | 状态 |

#### 表 7：bill（账单）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PRIMARY KEY AUTO_INCREMENT | 账单ID |
| user_id | BIGINT | NOT NULL | 用户ID |
| session_id | BIGINT | NOT NULL | 关联会话 |
| electricity_fee | DECIMAL(10,2) | DEFAULT 0 | 电费 |
| service_fee | DECIMAL(10,2) | DEFAULT 0 | 服务费 |
| total_fee | DECIMAL(10,2) | DEFAULT 0 | 总金额 |
| breakdown | VARCHAR(512) | - | 计费明细（JSON）|
| status | VARCHAR(20) | DEFAULT 'UNPAID' | 状态 |

#### 表 8：payment（支付记录）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PRIMARY KEY AUTO_INCREMENT | 支付ID |
| bill_id | BIGINT | NOT NULL | 关联账单 |
| user_id | BIGINT | NOT NULL | 用户ID |
| payment_method | VARCHAR(30) | - | 支付方式 |
| amount | DECIMAL(10,2) | - | 支付金额 |
| status | VARCHAR(20) | - | 状态 |
| paid_at | DATETIME | - | 支付时间 |

#### 表 9：electricity_price（电价策略）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PRIMARY KEY AUTO_INCREMENT | ID |
| period | VARCHAR(20) | NOT NULL | 时段（PEAK/FLAT/VALLEY）|
| mode | VARCHAR(20) | NOT NULL | 模式（FAST/SLOW/NORMAL）|
| charging_fee | DECIMAL(10,4) | NOT NULL | 电费（元/度）|
| service_fee | DECIMAL(10,4) | NOT NULL | 服务费（元/度）|
| description | VARCHAR(128) | - | 描述（时段说明）|

**联合唯一键**：`uk_period_mode (period, mode)`

### 5.3 默认数据

#### 5.3.1 电价（已在 schema.sql 中插入）

| period | mode | charging_fee | service_fee | 时段 |
|---|---|---|---|---|
| PEAK | FAST | 1.20 | 0.70 | 08:00-11:00, 18:00-21:00 |
| FLAT | FAST | 0.80 | 0.70 | 12:00-17:00 |
| VALLEY | FAST | 0.40 | 0.70 | 22:00-07:00 |
| PEAK | SLOW | 1.20 | 0.40 | 08:00-11:00, 18:00-21:00 |
| FLAT | SLOW | 0.80 | 0.40 | 12:00-17:00 |
| VALLEY | SLOW | 0.40 | 0.40 | 22:00-07:00 |

#### 5.3.2 初始化接口（POST /api/init）

调用后自动生成：
- 1 个充电站
- 2 个快充桩（60kW）
- 3 个慢充桩（7kW）
- 全部默认电价

---

## 6. 配置与部署

### 6.1 后端配置（application.yml）

```yaml
server:
  port: 8080
  servlet:
    context-path: /api

spring:
  datasource:
    url: jdbc:h2:mem:smart_charging;MODE=MySQL
    username: sa
    password:
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
  h2:
    console:
      enabled: true
      path: /h2-console
```

**切换到 MySQL**：创建 `application-mysql.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/smart_charging?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
    username: root
    password: root
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
  h2:
    console:
      enabled: false
```

启动命令：`mvn spring-boot:run -Dspring.profiles.active=mysql`

### 6.2 前端配置（vite.config.js）

```js
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
});
```

### 6.3 本地启动步骤

1. **启动后端**：
   ```bash
   cd backend
   mvn spring-boot:run
   ```

2. **启动前端**：
   ```bash
   cd frontend
   npm install
   npm run dev
   ```

3. **初始化数据**：
   ```bash
   curl -X POST http://localhost:8080/api/init
   ```

4. **访问**：
   - 前端：http://localhost:5173
   - 后端 API：http://localhost:8080/api
   - H2 Console：http://localhost:8080/api/h2-console（开发环境）

---

## 7. 计费逻辑

### 7.1 计费公式

```
电费 = Σ(每小时充电量 × 对应时段电价)
服务费 = Σ(每小时充电量 × 对应模式服务费)
总金额 = 电费 + 服务费
```

### 7.2 时段划分

由 `PricePeriod.ofHour(hour)` 决定：

| 时段 | hour 范围 | 电价 |
|---|---|---|
| PEAK（高峰）| [8, 11] ∪ [18, 21] | 1.20 元/度 |
| FLAT（平峰）| [12, 17] | 0.80 元/度 |
| VALLEY（低谷）| [0, 7] ∪ [22, 23] | 0.40 元/度 |

### 7.3 服务费

| 模式 | 服务费 |
|---|---|
| FAST（快充）| 0.70 元/度 |
| SLOW（慢充）| 0.40 元/度 |

---

## 8. API 总览

### 8.1 已实现接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/health` | 健康检查 |
| GET | `/enums` | 获取所有枚举值 |
| POST | `/init` | 初始化演示数据 |
| POST | `/users/register` | 用户注册 |
| POST | `/users/login` | 用户登录 |
| GET | `/users/{id}` | 获取用户信息 |
| POST | `/vehicles` | 绑定车辆 |
| GET | `/vehicles/user/{userId}` | 获取用户车辆 |
| DELETE | `/vehicles/{id}` | 删除车辆 |
| GET | `/stations` | 获取充电站列表 |
| GET | `/piles` | 获取充电桩列表 |
| GET | `/piles/{id}` | 获取单个充电桩 |
| POST | `/charging-requests` | 提交充电请求 |
| GET | `/charging-requests/user/{userId}` | 用户请求列表 |
| POST | `/charging-requests/{id}/cancel` | 取消请求 |
| POST | `/scheduler/dispatch` | 调度分配 |
| GET | `/scheduler/queue/{mode}` | 获取排队列表 |
| POST | `/sessions/start` | 开始充电 |
| POST | `/sessions/{id}/stop` | 停止充电 |
| GET | `/sessions/user/{userId}` | 用户会话列表 |
| GET | `/bills/user/{userId}` | 用户账单列表 |
| GET | `/bills/{id}` | 获取账单详情 |
| POST | `/bills/{id}/pay` | 支付账单 |
| POST | `/fees/calc` | 计算费用 |
| GET | `/fees/prices` | 获取电价列表 |
| GET | `/admin/dashboard` | 管理端仪表盘 |

### 8.2 统一响应

所有接口返回：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

---

## 9. 关键设计决策

### 9.1 排队机制

- **第一版**：直接从 `charging_request` 表按 `mode + status IN ('WAITING', 'ASSIGNED')` 查询，按 `created_at` 排序
- 无需独立 `wait_queue` 表，简化实现

### 9.2 状态流转

**充电请求**：`WAITING → ASSIGNED → CHARGING → COMPLETED / CANCELLED`

**充电桩**：`IDLE → RESERVED → CHARGING → IDLE / FAULT / OFFLINE`

**充电会话**：`CHARGING → COMPLETED / INTERRUPTED`

**账单**：`UNPAID → PAID / CANCELLED`

### 9.3 安全考虑

- 密码存储：使用 BCrypt 加密
- 权限控制：`role` 字段区分 USER/ADMIN
- CORS 配置：仅允许本地开发地址（生产需调整）

---

## 10. 扩展建议

### 10.1 第二版可增加

- 用户钱包/余额管理
- 优惠券/折扣系统
- 预约充电功能
- 数据统计与报表
- 邮件/短信通知
- WebSocket 实时状态推送
- 运维日志审计

### 10.2 部署优化

- 接入 Spring Security + JWT
- Redis 缓存（电价、排队列表）
- Docker 容器化
- Nginx 反向代理
- CI/CD 流水线
