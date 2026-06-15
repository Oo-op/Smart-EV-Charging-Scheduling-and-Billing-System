<template>
  <div class="home">
    <header class="hero">
      <p class="eyebrow">Smart Charging System</p>
      <h1>智能充电系统基础控制台</h1>
      <p class="subtitle">用于验证后端健康状态、初始化演示数据，并快速查看当前已经接通的核心能力。</p>
    </header>

    <section class="panel-grid">
      <article class="panel">
        <div class="panel-head">
          <h2>服务状态</h2>
          <button :disabled="healthLoading" @click="loadHealth">
            {{ healthLoading ? '检查中...' : '检查健康状态' }}
          </button>
        </div>
        <p v-if="healthError" class="error">{{ healthError }}</p>
        <div v-else-if="health" class="kv">
          <span>状态</span>
          <strong>{{ health.status }}</strong>
          <span>服务</span>
          <strong>{{ health.service }}</strong>
        </div>
        <p v-else class="muted">点击按钮后会请求 `GET /api/health`。</p>
      </article>

      <article class="panel">
        <div class="panel-head">
          <h2>演示数据</h2>
          <button :disabled="initLoading" @click="runInit">
            {{ initLoading ? '初始化中...' : '初始化系统' }}
          </button>
        </div>
        <p v-if="initError" class="error">{{ initError }}</p>
        <div v-else-if="initResult" class="kv">
          <span>充电站</span>
          <strong>{{ initResult.stations }}</strong>
          <span>充电桩</span>
          <strong>{{ initResult.piles }}</strong>
          <span>电价配置</span>
          <strong>{{ initResult.prices }}</strong>
        </div>
        <p v-else class="muted">会调用 `POST /api/init` 生成一套最小演示数据。</p>
      </article>

      <article class="panel">
        <div class="panel-head">
          <h2>电价策略</h2>
          <button :disabled="priceLoading" @click="loadPrices">
            {{ priceLoading ? '加载中...' : '加载电价' }}
          </button>
        </div>
        <p v-if="priceError" class="error">{{ priceError }}</p>
        <ul v-else-if="prices.length" class="price-list">
          <li v-for="price in prices" :key="`${price.period}-${price.mode}`">
            <strong>{{ price.period }} / {{ price.mode }}</strong>
            <span>电费 {{ price.chargingFee }} + 服务费 {{ price.serviceFee }}</span>
          </li>
        </ul>
        <p v-else class="muted">点击按钮后会请求 `GET /api/fees/prices`。</p>
      </article>
    </section>

    <section class="roadmap">
      <div>
        <h2>当前已接通</h2>
        <ul>
          <li>统一响应包装与全局异常处理</li>
          <li>健康检查、系统初始化、充电站查询、枚举查询</li>
          <li>分时计费能力和完整 Axios API 封装</li>
          <li>用户端主流程页面（注册→充电→账单→支付）</li>
          <li>管理端 Dashboard（运营概览、桩监控、队列、故障处理）</li>
        </ul>
      </div>
      <div>
        <h2>快速入口</h2>
        <ul>
          <li><RouterLink to="/user">用户端</RouterLink> — 完整充电业务演示</li>
          <li><RouterLink to="/admin">管理端</RouterLink> — 运营监控与故障处理</li>
          <li><RouterLink to="/stations">充电站</RouterLink> — 站点列表</li>
        </ul>
      </div>
    </section>

    <div class="link-row">
      <RouterLink class="stations-link" to="/user">进入用户端</RouterLink>
      <RouterLink class="stations-link" to="/admin">进入管理后台</RouterLink>
      <RouterLink class="stations-link" to="/stations">查看充电站列表</RouterLink>
    </div>
  </div>
</template>

<script>
import { RouterLink } from 'vue-router';
import { getHealth, getPrices, initSystem } from '../api';

export default {
  name: 'Home',
  components: { RouterLink },
  data() {
    return {
      health: null,
      healthError: '',
      healthLoading: false,
      initResult: null,
      initError: '',
      initLoading: false,
      prices: [],
      priceError: '',
      priceLoading: false
    };
  },
  mounted() {
    this.loadHealth();
  },
  methods: {
    async loadHealth() {
      this.healthLoading = true;
      this.healthError = '';
      try {
        this.health = await getHealth();
      } catch (error) {
        this.healthError = error.message || '健康检查失败';
      } finally {
        this.healthLoading = false;
      }
    },
    async runInit() {
      this.initLoading = true;
      this.initError = '';
      try {
        this.initResult = await initSystem();
      } catch (error) {
        this.initError = error.message || '初始化失败';
      } finally {
        this.initLoading = false;
      }
    },
    async loadPrices() {
      this.priceLoading = true;
      this.priceError = '';
      try {
        this.prices = await getPrices();
      } catch (error) {
        this.priceError = error.message || '加载电价失败';
      } finally {
        this.priceLoading = false;
      }
    }
  }
};
</script>

<style scoped>
.home {
  min-height: 100vh;
  padding: 48px 24px 64px;
  color: #172033;
  background:
    radial-gradient(circle at top left, rgba(255, 212, 163, 0.55), transparent 28%),
    linear-gradient(180deg, #f8f5ef 0%, #edf3fb 100%);
}
.hero,
.roadmap,
.panel-grid,
.stations-link {
  max-width: 1120px;
  margin: 0 auto;
}
.eyebrow {
  margin: 0 0 12px;
  font-size: 12px;
  letter-spacing: 0.24em;
  text-transform: uppercase;
  color: #b75d2d;
}
.hero h1 {
  margin: 0;
  font-size: clamp(32px, 5vw, 56px);
  line-height: 1.05;
}
.subtitle {
  max-width: 760px;
  margin: 16px 0 0;
  font-size: 18px;
  line-height: 1.7;
  color: #4a5670;
}
.panel-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 20px;
  margin-top: 36px;
}
.panel {
  padding: 24px;
  border: 1px solid rgba(23, 32, 51, 0.08);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.82);
  box-shadow: 0 18px 60px rgba(31, 44, 71, 0.08);
  backdrop-filter: blur(8px);
}
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.panel-head h2,
.roadmap h2 {
  margin: 0;
  font-size: 20px;
}
button,
.stations-link {
  border: none;
  border-radius: 999px;
  background: #172033;
  color: #fff;
  cursor: pointer;
  transition: transform 0.2s ease, opacity 0.2s ease;
}
button {
  padding: 10px 16px;
  font-size: 14px;
}
button:disabled {
  cursor: not-allowed;
  opacity: 0.65;
}
button:not(:disabled):hover,
.stations-link:hover {
  transform: translateY(-1px);
}
.kv {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 10px 18px;
  margin-top: 20px;
}
.kv span {
  color: #69748b;
}
.muted {
  margin-top: 20px;
  color: #69748b;
  line-height: 1.6;
}
.error {
  margin-top: 20px;
  color: #bc3b2f;
}
.price-list,
.roadmap ul {
  padding: 0;
  margin: 20px 0 0;
  list-style: none;
}
.price-list li,
.roadmap li {
  margin-bottom: 12px;
  line-height: 1.6;
}
.price-list strong {
  display: block;
  margin-bottom: 4px;
}
.roadmap {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 20px;
  margin-top: 24px;
}
.roadmap > div {
  padding: 24px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.72);
}
.roadmap li a {
  color: #2d5db7;
}
.link-row {
  max-width: 1120px;
  margin: 28px auto 0;
}
.stations-link {
  display: inline-flex;
  margin-top: 0;
  margin-right: 12px;
  padding: 14px 20px;
  text-decoration: none;
}
@media (max-width: 720px) {
  .home {
    padding: 32px 16px 48px;
  }
  .panel-head {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
