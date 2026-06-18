/**
 * 格式化开放桩位 / 调整容量接口的反馈文案（与 PileCapacityUpdateResult 对齐）
 */
export function formatPileCapacityMessage(pileCode, result, { opening = null } = {}) {
  const pile = result?.pile ?? result;
  const enabled = pile?.enabled;
  const openQueueSlots = pile?.openQueueSlots;
  const maxQueueSlots = pile?.maxQueueSlots;

  let base;
  if (opening === true || (opening == null && enabled)) {
    base = `充电桩 ${pileCode} 已开放`;
  } else if (opening === false || (opening == null && !enabled)) {
    base = `充电桩 ${pileCode} 已关闭`;
  } else if (openQueueSlots != null && maxQueueSlots != null) {
    base = `充电桩 ${pileCode} 的开放位置已调整为 ${openQueueSlots}/${maxQueueSlots}`;
  } else {
    base = `充电桩 ${pileCode} 容量已更新`;
  }

  if (!result?.dispatchTriggered) {
    return base;
  }
  const note = result?.note;
  if (note) {
    const count = result.assignedCount ?? 0;
    if (count > 0) {
      return `${base}，${note}，新分配 ${count} 个请求`;
    }
    return `${base}，${note}`;
  }
  const count = result.assignedCount ?? 0;
  if (count > 0) {
    return `${base}，已对等候区全部车辆重新调度，新分配 ${count} 个请求`;
  }
  return `${base}，已对等候区全部车辆重新调度（当前无等候中请求）`;
}
