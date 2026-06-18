# 调度逻辑说明

本文档说明当前代码中的调度实现，主要对应：

- `backend/src/main/java/com/example/charging/service/SchedulerService.java`
- `backend/src/main/java/com/example/charging/service/PileService.java`
- `backend/src/main/java/com/example/charging/service/SessionService.java`
- `backend/src/main/java/com/example/charging/service/ChargingRequestService.java`

## 核心概念

### 队列区域

系统用 `ChargingRequest.queueArea` 区分请求所在位置：

- `WAITING_AREA`：等候区。用户新提交、修改后重新排队、或取消/移出桩队列后回到这里。
- `PILE_QUEUE`：某个充电桩的排队区。此时 `assignedPileId` 指向目标桩。
- `RECOVERY_QUEUE`：故障中断后的恢复请求队列，优先级最高。
- `MIGRATION_QUEUE`：故障桩原排队车辆迁移队列，优先级高于普通等候区。

### 请求状态

调度主要处理以下请求状态：

- `WAITING`：等待调度、等待进入桩队列、或在桩队列中等待。
- `ASSIGNED`：已分配到桩，准备开始充电。
- `CHARGING`：正在充电。
- `CANCELLED`：已取消。
- `COMPLETED`：已完成。

### 桩状态

调度主要关注以下桩状态：

- `IDLE`：空闲，可直接预约给队首车辆。
- `RESERVED`：已有车辆被分配，等待开始充电。
- `CHARGING`：正在充电，可接收排队车辆。
- `FAULT`：故障，不参与普通调度。
- `OFFLINE`：离线，不参与普通调度。

## 普通调度规则

当前调度算法对外标记为 `SJF`，但它并不是从等待区中选择“充电量最短”的请求。实际实现是：

1. 按队列优先级取请求：
   - `RECOVERY_QUEUE`
   - `MIGRATION_QUEUE`
   - `WAITING_AREA`
2. 在同一个队列区域内，按当前充电模式取车。
3. 同一模式内按 FIFO 取车：
   - 先比较 `queueNumber`
   - 再比较 `createdAt`
   - 最后比较 `id`
4. 对取出的这辆车，用 SJF 思路选择目标桩：
   - 计算每个可服务桩的预计完成时间
   - 预计完成时间 = 该桩已有等待时间 + 当前请求预计充电时长
   - 选择预计完成时间最短的桩
   - 若相同，按桩编号 `code` 排序

也就是说，当前实现是：

> 等待区同模式 FIFO 取车，给这辆车按 SJF 选择充电桩。

## SJF 选桩计算方式

`SchedulerService.bestCandidatePile()` 会遍历所有同模式充电桩，过滤条件是：

- 桩模式等于请求模式。
- 桩 `enabled = true`。
- 桩状态不是 `FAULT`。
- 桩状态不是 `OFFLINE`。
- 桩 `openQueueSlots > 0`。

然后计算：

```text
estimatedFinishMinutes = expectedWaitMinutes(pile) + estimatedDurationMinutes(request, pile)
```

其中：

- `expectedWaitMinutes(pile)` 包括：
  - 当前正在该桩充电的会话剩余时间。
  - 该桩队列中等待车辆的预计充电时长总和。
- `estimatedDurationMinutes(request, pile)` 等于：
  - `(targetAmount - chargedAmount) / pile.power`
  - 换算成分钟，向上取整。

如果 SJF 选中的桩当前不能接收该请求，例如非空闲桩的等待队列已满，则本次调度停止，请求回到或保留在 `WAITING_AREA`。

## 桩队列容量

桩队列容量来自配置：

```yaml
charging:
  queue:
    pile-capacity: ...
```

当前代码中的 `pileCapacity` 表示“某个桩的等待队列容量”，不包含正在充电的车辆，也不包含已经 `ASSIGNED` 且准备开始充电的车辆。

在 `application-acceptance.yml` 中，验收场景目前配置为：

```yaml
charging:
  queue:
    pile-capacity: 2
    waiting-area-capacity: 10
```

## 新请求提交

用户提交请求时，`ChargingRequestService.submitRequest()` 会：

1. 校验用户和车辆归属。
2. 创建 `ChargingRequest`：
   - `status = WAITING`
   - `queueArea = WAITING_AREA`
   - `queueNumber = null`
   - `assignedPileId = null`
3. 保存请求。
4. 调用 `schedulerService.triggerDispatch(saved.getMode())` 触发该模式调度。

因此，新请求提交后会立即尝试进入桩队列或被分配到空闲桩；如果当前 SJF 目标桩无法接收，则留在等候区。

## 调度执行流程

`SchedulerService.triggerDispatch(mode)` 是普通调度主入口。

循环执行：

1. 调用 `nextWaitingRequest(mode)` 取下一辆待调度车。
2. 如果没有待调度车：
   - 调用 `reserveIdlePileHeads(mode, assigned)`。
   - 尝试把空闲桩队列头部车辆设为 `ASSIGNED`。
   - 返回本次调度结果。
3. 如果有待调度车：
   - 调用 `bestCandidatePile(mode, next)` 选 SJF 目标桩。
4. 如果没有可服务桩：
   - 尝试 `reserveIdlePileHeads()`。
   - 返回。
5. 调用 `moveToPileQueue(next, targetPile)`。
6. 如果移动失败：
   - 若请求来自 `WAITING_AREA`，重新标记为 `WAITING_AREA`。
   - 尝试 `reserveIdlePileHeads()`。
   - 返回。
7. 如果移动后请求状态为 `ASSIGNED`，加入调度结果。
8. 继续循环。

## 进入桩队列

`moveToPileQueue(request, pile)` 的行为：

1. 如果桩是 `IDLE`，且该桩没有队首请求：
   - 请求直接设为 `ASSIGNED`。
   - `assignedPileId = pile.id`
   - `queueArea = PILE_QUEUE`
   - `queueNumber = null`
   - 桩状态改为 `RESERVED`
2. 否则：
   - 如果该桩等待队列数量已经达到 `pileCapacity`，返回失败。
   - 请求进入该桩等待队列。
   - `status = WAITING`
   - `queueArea = PILE_QUEUE`
   - `queueNumber = waitingQueueSize(pile.id) + 1`
3. 保存请求。
4. 对该桩等待队列重新编号。

注意：桩等待队列数量只统计 `queueArea = PILE_QUEUE` 且 `status = WAITING` 的请求。

## 空闲桩拉起队首车辆

`reserveIdlePileHeads(mode, assigned)` 会：

1. 找到同模式、状态为 `IDLE`、启用且开放队列位的桩。
2. 按桩编号排序。
3. 对每个空闲桩：
   - 找到该桩 `PILE_QUEUE` 的队首请求。
   - 将队首请求设为 `ASSIGNED`。
   - 清空 `queueNumber`。
   - 将桩状态设为 `RESERVED`。
   - 重新编号该桩等待队列。

## 开始充电后的调度

`SessionService.start(requestId, pileId)` 会：

1. 要求请求状态为 `ASSIGNED`。
2. 要求桩状态为 `RESERVED` 或 `IDLE`。
3. 创建 `ChargingSession`，状态为 `CHARGING`。
4. 将请求状态改为 `CHARGING`。
5. 清空请求的 `queueArea` 和 `queueNumber`。
6. 将桩状态改为 `CHARGING`。
7. 调用 `schedulerService.onPileQueueSlotFreed(pile.id)`。

`onPileQueueSlotFreed()` 当前只触发优先队列调度：

```text
RECOVERY_QUEUE > MIGRATION_QUEUE
```

它不会因为某个车辆开始充电，就主动从普通 `WAITING_AREA` 拉车进入桩队列。

## 停止充电后的调度

`SessionService.stop(sessionId, req)` 会：

1. 将会话设为 `COMPLETED`。
2. 累加请求的已充电量。
3. 将请求设为 `COMPLETED`。
4. 将桩状态设为 `IDLE`。
5. 生成账单。
6. 调用 `schedulerService.promoteNextForPile(pile.id)`。

`promoteNextForPile()` 内部调用 `triggerDispatch(pile.mode)`，然后只返回分配到当前桩的调度结果。

实际效果是：车辆停止充电后，会触发同模式普通调度。

## 取消请求后的调度

`ChargingRequestService.cancelRequest()` 会：

1. 只允许取消 `WAITING` 或 `ASSIGNED` 请求。
2. 将请求设为 `CANCELLED`。
3. 清空 `assignedPileId`、`queueArea`、`queueNumber`。
4. 如果取消前请求在 `PILE_QUEUE`：
   - 调用 `schedulerService.triggerDispatch(mode)`。

如果取消的是普通 `WAITING_AREA` 请求，不会额外触发调度。

## 修改请求后的调度

`ChargingRequestService.modifyRequest()` 分两种情况。

### 正在充电的请求

如果请求状态是 `CHARGING`：

- 不允许切换模式。
- 不允许增加目标充电量。
- 允许降低目标充电量。
- 不触发重新调度。

### 等待或已分配的请求

如果请求状态是 `WAITING` 或 `ASSIGNED`：

1. 更新目标充电量和模式。
2. 将请求重新放回 `WAITING_AREA`。
3. 清空 `assignedPileId` 和 `queueNumber`。
4. 调用 `schedulerService.triggerDispatch(oldMode)`。
5. 如果模式发生变化，再调用 `schedulerService.triggerDispatch(newMode)`。

## 故障处理总览

故障处理入口是 `PileService.markFault(pileId, faultReason, chargedAmount)`。

当前实现包含三类动作：

1. 如果故障桩正在充电，中断当前充电会话。
2. 将故障桩状态设为 `FAULT`。
3. 处理恢复请求和故障桩原队列。

当前故障处理会立即迁移故障桩队列，并触发优先调度。这一点会影响验收用例中的故障场景。

## 故障时中断正在充电车辆

如果桩状态为 `CHARGING`，`markFault()` 会调用 `interruptActiveSession()`。

中断逻辑：

1. 找到该桩当前 `CHARGING` 会话。
2. 使用传入的 `chargedAmount` 作为本次会话已充电量；如果为空，则使用 session 当前记录。
3. 计算总已充电量：

```text
request.chargedAmount + sessionCharged
```

4. 计算剩余电量：

```text
targetAmount - totalCharged
```

5. 将 session 设为：
   - `chargedAmount = sessionCharged`
   - `endTime = now`
   - `status = INTERRUPTED`
6. 更新请求：
   - `chargedAmount = totalCharged`
   - `assignedPileId = null`
   - 如果仍有剩余电量：
     - `status = WAITING`
     - `queueNumber = 0`
   - 如果没有剩余电量：
     - `status = COMPLETED`

如果请求仍需继续充电，`markFault()` 会调用：

```java
schedulerService.placeRecoveryRequest(recoveredRequestId)
```

## 恢复请求队列

`placeRecoveryRequest(requestId)` 会：

1. 将请求设为 `WAITING`。
2. 清空 `assignedPileId`。
3. 设置 `queueArea = RECOVERY_QUEUE`。
4. 设置 `queueNumber = 0`。
5. 保存请求。
6. 调用 `triggerPriorityDispatch(request.mode)`。

`RECOVERY_QUEUE` 的优先级高于 `MIGRATION_QUEUE` 和 `WAITING_AREA`。

## 故障桩原队列迁移

故障桩状态设为 `FAULT` 后，`markFault()` 会调用：

```java
schedulerService.migrateFaultedPileQueue(pile.id)
```

迁移逻辑：

1. 读取故障桩上 `PILE_QUEUE` 中的请求。
2. 按原桩队列顺序遍历。
3. 对每个请求：
   - `status = WAITING`
   - `assignedPileId = null`
   - `queueArea = MIGRATION_QUEUE`
   - `queueNumber = 原顺序`
4. 保存请求。
5. 调用 `triggerPriorityDispatch(pile.mode)`。

因此，当前实现中，故障桩原队列不会长期保留在故障桩的 `PILE_QUEUE` 上，而是马上进入 `MIGRATION_QUEUE` 并尝试迁移到其他可服务桩。

## 优先调度

`triggerPriorityDispatch(mode)` 只处理：

```text
RECOVERY_QUEUE > MIGRATION_QUEUE
```

它不会调度普通 `WAITING_AREA`。

循环逻辑：

1. 先取 `RECOVERY_QUEUE` 队首请求。
2. 如果没有，再取 `MIGRATION_QUEUE` 队首请求。
3. 用 SJF 选桩。
4. 调用 `moveToPileQueue()`。
5. 如果无法移动，停止优先调度。

## 故障恢复

故障桩恢复入口是 `PileService.recover(pileId)`。

逻辑：

1. 要求桩当前状态为 `FAULT`。
2. 将桩状态改为 `IDLE`。
3. 保存桩。
4. 调用 `schedulerService.promoteNextForPile(pile.id)`。

`promoteNextForPile()` 会调用同模式 `triggerDispatch()`，因此恢复后会触发：

```text
RECOVERY_QUEUE > MIGRATION_QUEUE > WAITING_AREA
```

但返回结果只保留分配到当前恢复桩的那一条。

## 当前实现与验收用例的注意点

目前普通调度主线与验收表前半段一致：

- 同模式等待区 FIFO。
- 为当前请求按 SJF 选桩。
- SJF 选中的桩满时，请求留在等候区。
- `application-acceptance.yml` 中每桩等待队列容量应为 2。

但故障场景从 `8:25 (B,T1,O,0)` 起仍与验收表存在差异。当前实现故障时会立即迁移故障桩原队列，而验收表在故障当刻仍保留故障桩原队列展示，例如慢充 1 的 `V17` 仍显示在原桩队列中。

因此，若要完全匹配验收 CSV，后续需要重点核对并调整故障迁移时机和恢复调度策略，而不是普通等待区 FIFO/SJF 选桩逻辑。
