import axios from 'axios';

const request = axios.create({
  baseURL: '/api',
  timeout: 10000
});

/**
 * 统一响应拦截器：后端返回格式固定为
 * { code: 0, message: "success", data: ... }
 * - code === 0：返回 data 字段（业务层直接取数据）
 * - 其他 code：抛出异常，由上层 catch 处理
 */
request.interceptors.response.use(
  (response) => {
    const res = response.data;
    if (res && typeof res.code !== 'undefined') {
      if (res.code === 0) {
        return res.data;
      }
      return Promise.reject(new Error(res.message || '请求失败'));
    }
    return res;
  },
  (error) => {
    const msg =
      error.response?.data?.message ||
      (error.response?.status === 404
        ? `接口不存在 (${error.config?.method?.toUpperCase()} ${error.config?.url})，请确认后端已重启`
        : null) ||
      error.message;
    return Promise.reject(new Error(msg));
  }
);

export default request;
