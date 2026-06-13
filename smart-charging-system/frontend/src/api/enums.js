/**
 * 前端统一状态枚举：与后端 api-spec.md 1.3 保持一致。
 * 团队新增状态时，必须同步更新：后端 Enum → 后端 EnumController → 本文件。
 *
 * 展示文案可使用 getStatusDesc('chargingPileStatus', 'CHARGING')。
 */

export const STATION_STATUS = {
  AVAILABLE: '空闲可用',
  OCCUPIED: '使用中',
  MAINTENANCE: '维护中',
  OFFLINE: '离线'
};

/** 对应后端 ChargingRequestStatus */
export const CHARGING_REQUEST_STATUS = {
  WAITING: '等待调度',
  ASSIGNED: '已分配充电桩',
  CHARGING: '充电中',
  CANCELLED: '已取消',
  COMPLETED: '已完成'
};

/** 对应后端 ChargingPileStatus */
export const CHARGING_PILE_STATUS = {
  IDLE: '空闲',
  RESERVED: '已预约',
  CHARGING: '充电中',
  FAULT: '故障',
  OFFLINE: '离线'
};

/** 对应后端 ChargingSessionStatus */
export const CHARGING_SESSION_STATUS = {
  CHARGING: '充电中',
  COMPLETED: '已完成',
  INTERRUPTED: '被中断'
};

/** 对应后端 BillStatus */
export const BILL_STATUS = {
  UNPAID: '待支付',
  PAID: '已支付',
  CANCELLED: '已取消'
};

/** 第一版：只有 FAST / SLOW 在业务中使用；其余为第二版预留 */
export const CHARGE_MODE = {
  FAST: '快充',
  SLOW: '慢充',
  NORMAL: '普通充电（预留）',
  SMART: '智能充电（预留）',
  SCHEDULED: '定时充电（预留）'
};

/** 对应后端 PaymentStatus */
export const PAYMENT_STATUS = {
  PENDING: '待支付',
  SUCCESS: '支付成功',
  FAILED: '支付失败'
};

export const PRICE_PERIOD = {
  PEAK: '高峰',
  FLAT: '平峰',
  VALLEY: '低谷'
};

const ENUM_MAP = {
  stationStatus: STATION_STATUS,
  chargingRequestStatus: CHARGING_REQUEST_STATUS,
  chargingPileStatus: CHARGING_PILE_STATUS,
  chargingSessionStatus: CHARGING_SESSION_STATUS,
  billStatus: BILL_STATUS,
  chargeMode: CHARGE_MODE,
  paymentStatus: PAYMENT_STATUS,
  pricePeriod: PRICE_PERIOD
};

export function getStatusDesc(type, code) {
  return ENUM_MAP[type]?.[code] || code || '未知';
}

export default ENUM_MAP;
