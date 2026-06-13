# 智能充电桩调度计费系统接口规范表

> 文档定位：这是目标接口规范，不代表仓库当前已全部实现。
> 截至 2026-06-13，当前已实现的接口主要是 `health / init / stations / enums / fees`，其余接口仍需按本文继续落地。

## 1. 全局约定

### 1.1 基础路径

```text
后端基础路径：http://localhost:8080/api
```

### 1.2 统一返回格式

所有接口必须返回统一结构：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

失败时：

```json
{
  "code": 1,
  "message": "错误信息",
  "data": null
}
```

### 1.3 通用枚举

```text
ChargeMode:
FAST / SLOW

ChargingRequestStatus:
WAITING / ASSIGNED / CHARGING / CANCELLED / COMPLETED

ChargingPileStatus:
IDLE / RESERVED / CHARGING / FAULT / OFFLINE

ChargingSessionStatus:
CHARGING / COMPLETED / INTERRUPTED

BillStatus:
UNPAID / PAID / CANCELLED

PaymentStatus:
PENDING / SUCCESS / FAILED

UserRole:
USER / ADMIN
```

所有接口字段中的状态值必须使用以上枚举，不允许各模块自行定义字符串。

---

# 2. 用户模块接口

## 2.1 用户注册

| 项目  | 内容                          |
| --- | --------------------------- |
| 接口  | `POST /api/users/register`  |
| 负责人 | B                           |
| 功能  | 创建普通用户账号                    |
| 请求体 | `username, password, phone` |
| 返回  | 注册成功后的用户信息                  |

请求示例：

```json
{
  "username": "yumo",
  "password": "123456",
  "phone": "13800000000"
}
```

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "userId": 1,
    "username": "yumo",
    "phone": "13800000000",
    "role": "USER"
  }
}
```

---

## 2.2 用户登录

| 项目  | 内容                      |
| --- | ----------------------- |
| 接口  | `POST /api/users/login` |
| 负责人 | B                       |
| 功能  | 用户或管理员登录                |
| 请求体 | `username, password`    |
| 返回  | 用户信息和登录标识               |

请求示例：

```json
{
  "username": "yumo",
  "password": "123456"
}
```

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "userId": 1,
    "username": "yumo",
    "role": "USER",
    "token": "mock-token"
  }
}
```

第一版可以使用 mock-token，不强制实现 JWT。

---

# 3. 车辆模块接口

## 3.1 绑定车辆

| 项目  | 内容                                            |
| --- | --------------------------------------------- |
| 接口  | `POST /api/vehicles`                          |
| 负责人 | B                                             |
| 功能  | 用户绑定车辆                                        |
| 请求体 | `userId, plateNumber, model, batteryCapacity` |
| 返回  | 车辆信息                                          |

请求示例：

```json
{
  "userId": 1,
  "plateNumber": "京A12345",
  "model": "Tesla Model 3",
  "batteryCapacity": 60.0
}
```

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "vehicleId": 1,
    "userId": 1,
    "plateNumber": "京A12345",
    "model": "Tesla Model 3",
    "batteryCapacity": 60.0
  }
}
```

---

## 3.2 查询用户车辆列表

| 项目   | 内容                                 |
| ---- | ---------------------------------- |
| 接口   | `GET /api/users/{userId}/vehicles` |
| 负责人  | B                                  |
| 功能   | 查询某用户绑定的所有车辆                       |
| 路径参数 | `userId`                           |
| 返回   | 车辆列表                               |

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "vehicleId": 1,
      "plateNumber": "京A12345",
      "model": "Tesla Model 3",
      "batteryCapacity": 60.0
    }
  ]
}
```

---

# 4. 充电申请模块接口

## 4.1 提交充电请求

| 项目  | 内容                                      |
| --- | --------------------------------------- |
| 接口  | `POST /api/charging-requests`           |
| 负责人 | B                                       |
| 功能  | 用户选择车辆、充电模式和目标电量后提交请求                   |
| 请求体 | `userId, vehicleId, mode, targetAmount` |
| 返回  | 请求编号、排队号、状态、预计等待时间                      |

请求示例：

```json
{
  "userId": 1,
  "vehicleId": 1,
  "mode": "FAST",
  "targetAmount": 30.0
}
```

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "requestId": 1001,
    "userId": 1,
    "vehicleId": 1,
    "mode": "FAST",
    "targetAmount": 30.0,
    "chargedAmount": 0.0,
    "status": "WAITING",
    "queueNumber": 1,
    "estimatedWaitTime": 0
  }
}
```

业务规则：

```text
1. 创建 ChargingRequest。
2. status = WAITING。
3. queueNumber = 同 mode 下 WAITING 请求数量 + 1。
4. 第一版 estimatedWaitTime 可先返回 0 或按队列长度粗略估算。
```

---

## 4.2 查询充电请求详情

| 项目   | 内容                                       |
| ---- | ---------------------------------------- |
| 接口   | `GET /api/charging-requests/{requestId}` |
| 负责人  | B                                        |
| 功能   | 查询某个充电请求当前状态                             |
| 路径参数 | `requestId`                              |
| 返回   | 请求详情                                     |

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "requestId": 1001,
    "userId": 1,
    "vehicleId": 1,
    "mode": "FAST",
    "targetAmount": 30.0,
    "chargedAmount": 0.0,
    "status": "ASSIGNED",
    "queueNumber": 1,
    "assignedPileId": 1,
    "createdAt": "2026-06-13 10:00:00"
  }
}
```

---

## 4.3 取消充电请求

| 项目   | 内容                                               |
| ---- | ------------------------------------------------ |
| 接口   | `POST /api/charging-requests/{requestId}/cancel` |
| 负责人  | B                                                |
| 功能   | 用户取消等待中的充电请求                                     |
| 路径参数 | `requestId`                                      |
| 返回   | 取消结果                                             |

请求体可为空：

```json
{}
```

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "requestId": 1001,
    "status": "CANCELLED"
  }
}
```

业务规则：

```text
1. WAITING / ASSIGNED 状态允许取消。
2. CHARGING 状态不允许取消，只能结束充电。
3. 已取消后不再参与调度。
```

---

## 4.4 修改充电请求

| 项目   | 内容                                               |
| ---- | ------------------------------------------------ |
| 接口   | `POST /api/charging-requests/{requestId}/modify` |
| 负责人  | B                                                |
| 功能   | 修改等待中的目标电量或充电模式                                  |
| 路径参数 | `requestId`                                      |
| 请求体  | `mode, targetAmount`                             |
| 返回   | 修改后的请求信息                                         |

请求示例：

```json
{
  "mode": "SLOW",
  "targetAmount": 40.0
}
```

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "requestId": 1001,
    "mode": "SLOW",
    "targetAmount": 40.0,
    "status": "WAITING",
    "queueNumber": 2
  }
}
```

业务规则：

```text
1. WAITING 状态允许修改。
2. CHARGING 状态不允许增加目标电量。
3. 修改 mode 后需要重新计算队列位置。
```

第一版可以只允许修改 targetAmount，mode 修改可作为增强功能。

---

# 5. 充电桩模块接口

## 5.1 查询全部充电桩

| 项目  | 内容               |
| --- | ---------------- |
| 接口  | `GET /api/piles` |
| 负责人 | C                |
| 功能  | 查询所有充电桩状态        |
| 返回  | 充电桩列表            |

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
      "status": "IDLE",
      "serviceFee": 0.7
    },
    {
      "pileId": 3,
      "code": "S01",
      "mode": "SLOW",
      "power": 7.0,
      "status": "IDLE",
      "serviceFee": 0.4
    }
  ]
}
```

---

## 5.2 标记充电桩故障

| 项目   | 内容                               |
| ---- | -------------------------------- |
| 接口   | `POST /api/piles/{pileId}/fault` |
| 负责人  | C                                |
| 功能   | 将指定充电桩置为故障状态                     |
| 路径参数 | `pileId`                         |
| 请求体  | `faultReason`                    |
| 返回   | 故障处理结果                           |

请求示例：

```json
{
  "faultReason": "设备离线"
}
```

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "pileId": 1,
    "status": "FAULT",
    "faultReason": "设备离线"
  }
}
```

第一版业务规则：

```text
1. 将 pile.status 改为 FAULT。
2. 如果该桩正在充电，可先返回错误或标记 session INTERRUPTED。
3. 故障恢复可作为后续增强。
```

---

# 6. 调度模块接口

## 6.1 手动触发调度

| 项目  | 内容                             |
| --- | ------------------------------ |
| 接口  | `POST /api/scheduler/dispatch` |
| 负责人 | C                              |
| 功能  | 系统从等待队列中选择一个请求并分配空闲充电桩         |
| 请求体 | `mode`                         |
| 返回  | 调度结果                           |

请求示例：

```json
{
  "mode": "FAST"
}
```

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "requestId": 1001,
    "pileId": 1,
    "pileCode": "F01",
    "mode": "FAST",
    "requestStatus": "ASSIGNED",
    "pileStatus": "RESERVED",
    "estimatedStartTime": "2026-06-13 10:05:00"
  }
}
```

第一版调度规则：

```text
1. 根据 mode 查找同类型 WAITING 请求。
2. 按 createdAt 升序选择最早请求。
3. 查找同 mode 且 status=IDLE 的充电桩。
4. 更新 request.status = ASSIGNED。
5. 更新 request.assignedPileId = pileId。
6. 更新 pile.status = RESERVED。
```

如果没有等待请求：

```json
{
  "code": 1,
  "message": "当前模式下没有等待请求",
  "data": null
}
```

如果没有空闲充电桩：

```json
{
  "code": 1,
  "message": "当前模式下没有空闲充电桩",
  "data": null
}
```

---

## 6.2 查询队列状态

| 项目   | 内容                                |
| ---- | --------------------------------- |
| 接口   | `GET /api/scheduler/queue/{mode}` |
| 负责人  | C                                 |
| 功能   | 查询快充或慢充等待队列                       |
| 路径参数 | `mode=FAST/SLOW`                  |
| 返回   | 队列长度、等待车辆、预计等待时间                  |

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "mode": "FAST",
    "queueLength": 2,
    "availablePileCount": 1,
    "estimatedWaitTime": 30,
    "waitingList": [
      {
        "requestId": 1001,
        "vehicleId": 1,
        "plateNumber": "京A12345",
        "targetAmount": 30.0,
        "queueNumber": 1,
        "status": "WAITING",
        "createdAt": "2026-06-13 10:00:00"
      }
    ]
  }
}
```

---

# 7. 充电会话模块接口

## 7.1 开始充电

| 项目  | 内容                         |
| --- | -------------------------- |
| 接口  | `POST /api/sessions/start` |
| 负责人 | C                          |
| 功能  | 根据已分配的请求和充电桩创建充电会话         |
| 请求体 | `requestId, pileId`        |
| 返回  | 会话信息                       |

请求示例：

```json
{
  "requestId": 1001,
  "pileId": 1
}
```

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "sessionId": 5001,
    "requestId": 1001,
    "pileId": 1,
    "mode": "FAST",
    "targetAmount": 30.0,
    "chargedAmount": 0.0,
    "status": "CHARGING",
    "startTime": "2026-06-13 10:10:00",
    "estimatedEndTime": "2026-06-13 10:40:00"
  }
}
```

业务规则：

```text
1. request.status 必须是 ASSIGNED。
2. pile.status 必须是 RESERVED 或 IDLE。
3. 创建 ChargingSession。
4. session.status = CHARGING。
5. request.status = CHARGING。
6. pile.status = CHARGING。
```

---

## 7.2 结束充电

| 项目   | 内容                                    |
| ---- | ------------------------------------- |
| 接口   | `POST /api/sessions/{sessionId}/stop` |
| 负责人  | C + D                                 |
| 功能   | 结束充电会话并生成账单                           |
| 路径参数 | `sessionId`                           |
| 请求体  | `chargedAmount` 可选                    |
| 返回   | 结束结果和账单信息                             |

请求示例：

```json
{
  "chargedAmount": 30.0
}
```

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "sessionId": 5001,
    "requestId": 1001,
    "pileId": 1,
    "chargedAmount": 30.0,
    "status": "COMPLETED",
    "endTime": "2026-06-13 10:40:00",
    "bill": {
      "billId": 9001,
      "electricityFee": 24.0,
      "serviceFee": 21.0,
      "totalFee": 45.0,
      "status": "UNPAID"
    }
  }
}
```

第一版计费规则：

```text
electricityFee = chargedAmount × 0.8
serviceFee = chargedAmount × pile.serviceFee
totalFee = electricityFee + serviceFee
```

业务规则：

```text
1. session.status 必须是 CHARGING。
2. session.status 改为 COMPLETED。
3. request.status 改为 COMPLETED。
4. pile.status 改为 IDLE。
5. 调用 BillService.generateBill() 生成账单。
```

---

# 8. 账单模块接口

## 8.1 查询用户账单列表

| 项目   | 内容                             |
| ---- | ------------------------------ |
| 接口   | `GET /api/bills/user/{userId}` |
| 负责人  | D                              |
| 功能   | 查询某用户全部账单                      |
| 路径参数 | `userId`                       |
| 返回   | 账单列表                           |

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "billId": 9001,
      "userId": 1,
      "sessionId": 5001,
      "electricityFee": 24.0,
      "serviceFee": 21.0,
      "totalFee": 45.0,
      "status": "UNPAID",
      "createdAt": "2026-06-13 10:40:00"
    }
  ]
}
```

---

## 8.2 查询账单详情

| 项目   | 内容                        |
| ---- | ------------------------- |
| 接口   | `GET /api/bills/{billId}` |
| 负责人  | D                         |
| 功能   | 查询单个账单详情                  |
| 路径参数 | `billId`                  |
| 返回   | 账单详情                      |

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "billId": 9001,
    "userId": 1,
    "sessionId": 5001,
    "pileCode": "F01",
    "mode": "FAST",
    "chargedAmount": 30.0,
    "electricityFee": 24.0,
    "serviceFee": 21.0,
    "totalFee": 45.0,
    "status": "UNPAID",
    "createdAt": "2026-06-13 10:40:00"
  }
}
```

---

## 8.3 支付账单

| 项目   | 内容                             |
| ---- | ------------------------------ |
| 接口   | `POST /api/bills/{billId}/pay` |
| 负责人  | D                              |
| 功能   | 模拟支付账单                         |
| 路径参数 | `billId`                       |
| 请求体  | `paymentMethod`                |
| 返回   | 支付结果                           |

请求示例：

```json
{
  "paymentMethod": "WECHAT"
}
```

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "billId": 9001,
    "paymentId": 3001,
    "paymentMethod": "WECHAT",
    "amount": 45.0,
    "billStatus": "PAID",
    "paymentStatus": "SUCCESS",
    "paidAt": "2026-06-13 10:45:00"
  }
}
```

业务规则：

```text
1. bill.status 必须是 UNPAID。
2. 支付成功后 bill.status = PAID。
3. 创建 Payment 记录。
4. 第一版只模拟支付成功。
```

---

# 9. 管理员模块接口

## 9.1 管理员仪表盘

| 项目  | 内容                         |
| --- | -------------------------- |
| 接口  | `GET /api/admin/dashboard` |
| 负责人 | E 配合 A                     |
| 功能  | 查询运营概览                     |
| 返回  | 用户数、请求数、充电桩状态、收入等统计        |

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

---

## 9.2 管理员查看充电桩状态

| 项目  | 内容                     |
| --- | ---------------------- |
| 接口  | `GET /api/admin/piles` |
| 负责人 | E 配合 C                 |
| 功能  | 管理员查看所有充电桩及当前会话        |
| 返回  | 充电桩状态列表                |

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
        "startTime": "2026-06-13 10:10:00"
      }
    }
  ]
}
```

第一版如果 currentSession 暂时不好查，可以返回 null。

---

## 9.3 管理员查看队列状态

| 项目  | 内容                     |
| --- | ---------------------- |
| 接口  | `GET /api/admin/queue` |
| 负责人 | E 配合 C                 |
| 功能  | 管理员同时查看快充和慢充队列         |
| 返回  | FAST 和 SLOW 两类队列       |

响应示例：

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
      "waitingList": [
        {
          "requestId": 1002,
          "plateNumber": "京B88888",
          "targetAmount": 20.0,
          "queueNumber": 1,
          "status": "WAITING"
        }
      ]
    }
  }
}
```

---

# 10. 前后端联调用主流程接口顺序

## 10.1 用户侧完整流程

```text
1. POST /api/users/register
2. POST /api/users/login
3. POST /api/vehicles
4. POST /api/charging-requests
5. GET  /api/scheduler/queue/{mode}
6. POST /api/scheduler/dispatch
7. POST /api/sessions/start
8. POST /api/sessions/{sessionId}/stop
9. GET  /api/bills/user/{userId}
10. GET /api/bills/{billId}
11. POST /api/bills/{billId}/pay
```

## 10.2 管理员侧完整流程

```text
1. GET /api/admin/dashboard
2. GET /api/admin/piles
3. GET /api/admin/queue
4. POST /api/piles/{pileId}/fault
```

---

# 11. 责任划分总表

| 模块   | 接口                                                      | 负责人   |
| ---- | ------------------------------------------------------- | ----- |
| 用户模块 | `/api/users/register`, `/api/users/login`               | B     |
| 车辆模块 | `/api/vehicles`, `/api/users/{userId}/vehicles`         | B     |
| 充电申请 | `/api/charging-requests/**`                             | B     |
| 充电桩  | `/api/piles`, `/api/piles/{pileId}/fault`               | C     |
| 调度   | `/api/scheduler/**`                                     | C     |
| 充电会话 | `/api/sessions/start`, `/api/sessions/{sessionId}/stop` | C     |
| 账单   | `/api/bills/**`                                         | D     |
| 管理员  | `/api/admin/**`                                         | E + A |
| 公共规范 | `ApiResponse`, 枚举, 异常处理, CORS                           | A     |
| 集成联调 | 主流程串联、README、演示脚本                                       | A     |

---

# 12. 第一版必须保证的验收标准

```text
1. 所有接口都返回 ApiResponse。
2. 所有状态字段都使用统一枚举。
3. 注册、车辆绑定、提交请求、调度、开始充电、结束充电、生成账单、支付账单能够完整跑通。
4. Controller 不写业务逻辑，只调用 Service。
5. Service 负责状态流转和业务规则。
6. Repository / Mapper 只负责数据库读写。
7. 第一版调度使用 FCFS。
8. 第一版计费使用简单计费。
9. 后续再升级 SJF 调度和分时计费。
```
