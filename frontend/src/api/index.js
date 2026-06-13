import request from './request';

export function getHealth() {
  return request.get('/health');
}

export function getStations() {
  return request.get('/stations');
}

export function initSystem(options = {}) {
  return request.post('/init', options);
}

export function getPrices() {
  return request.get('/fees/prices');
}

/**
 * 计费接口
 * payload: { mode: 'FAST', energyKwh: 100, startTime: ISO, endTime: ISO }
 */
export function calcFee(payload) {
  return request.post('/fees/calc', payload);
}

export function getEnums() {
  return request.get('/enums');
}
