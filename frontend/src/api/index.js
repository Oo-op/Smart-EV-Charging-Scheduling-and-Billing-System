import request from './request';

// ─── 公共 / 初始化 ───────────────────────────────────────────

export function getHealth() {
  return request.get('/health');
}

export function initSystem(options = {}) {
  return request.post('/init', options);
}

export function getStations() {
  return request.get('/stations');
}

export function getEnums() {
  return request.get('/enums');
}

export function getPrices() {
  return request.get('/fees/prices');
}

/**
 * 分时计费试算
 * payload: { mode: 'FAST', energyKwh: 100, startTime: ISO, endTime: ISO }
 */
export function calcFee(payload) {
  return request.post('/fees/calc', payload);
}

// ─── B：用户模块 ─────────────────────────────────────────────

export function registerUser(payload) {
  return request.post('/users/register', payload);
}

export function loginUser(payload) {
  return request.post('/users/login', payload);
}

// ─── B：车辆模块 ─────────────────────────────────────────────

export function bindVehicle(payload) {
  return request.post('/vehicles', payload);
}

export function getUserVehicles(userId) {
  return request.get(`/users/${userId}/vehicles`);
}

export function unbindVehicle(vehicleId, userId) {
  return request.delete(`/vehicles/${vehicleId}`, { params: { userId } });
}

// ─── B：充电请求模块 ─────────────────────────────────────────

export function submitChargingRequest(payload) {
  return request.post('/charging-requests', payload);
}

export function getChargingRequest(requestId, userId) {
  return request.get(`/charging-requests/${requestId}`, { params: { userId } });
}

export function getUserChargingRequests(userId) {
  return request.get(`/users/${userId}/charging-requests`);
}

export function cancelChargingRequest(requestId, userId) {
  return request.post(`/charging-requests/${requestId}/cancel`, {}, { params: { userId } });
}

export function modifyChargingRequest(requestId, userId, payload) {
  return request.post(`/charging-requests/${requestId}/modify`, payload, { params: { userId } });
}

// ─── C：调度模块 ─────────────────────────────────────────────

export function getSchedulerQueue(mode) {
  return request.get(`/scheduler/queue/${mode}`);
}

export function dispatchScheduler(payload) {
  return request.post('/scheduler/dispatch', payload);
}

// ─── C：充电桩模块 ───────────────────────────────────────────

export function getPiles() {
  return request.get('/piles');
}

export function markPileFault(pileId, payload = {}) {
  return request.post(`/piles/${pileId}/fault`, payload);
}

export function recoverPile(pileId) {
  return request.post(`/piles/${pileId}/recover`, {});
}

// ─── C：充电会话模块 ─────────────────────────────────────────

export function startSession(payload) {
  return request.post('/sessions/start', payload);
}

export function stopSession(sessionId, payload = {}) {
  return request.post(`/sessions/${sessionId}/stop`, payload);
}

// ─── D：账单模块 ─────────────────────────────────────────────

export function getUserBills(userId) {
  return request.get(`/bills/user/${userId}`);
}

export function getBillDetail(billId) {
  return request.get(`/bills/${billId}`);
}

export function payBill(billId, payload) {
  return request.post(`/bills/${billId}/pay`, payload);
}

// ─── E：管理端模块 ───────────────────────────────────────────

export function getAdminDashboard() {
  return request.get('/admin/dashboard');
}

export function getAdminPiles() {
  return request.get('/admin/piles');
}

export function getAdminQueue() {
  return request.get('/admin/queue');
}
