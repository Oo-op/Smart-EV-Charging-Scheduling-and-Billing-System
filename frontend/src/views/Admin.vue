<template>
  <div class="admin">
    <header class="hero">
      <p class="eyebrow">Admin Dashboard</p>
      <h1>运营管理后台</h1>
      <p class="subtitle">查看运营概览、充电桩实时状态、等待队列，并支持标记故障与恢复。</p>
      <div class="hero-actions">
        <div class="admin-chip">
          <span class="admin-name">{{ adminName }}</span>
          <span class="admin-role">已登录管理员</span>
        </div>
        <button :disabled="loading" @click="refreshAll">
          {{ loading ? '刷新中...' : '刷新全部' }}
        </button>
        <button type="button" class="nav-link action-link" @click="logout">退出登录</button>
      </div>
    </header>

    <p v-if="error" class="error banner">{{ error }}</p>

    <section v-if="dashboard" class="stats-grid">
      <article class="stat-card">
        <span class="stat-label">注册用户</span>
        <strong class="stat-value">{{ dashboard.totalUsers }}</strong>
      </article>
      <article class="stat-card">
        <span class="stat-label">绑定车辆</span>
        <strong class="stat-value">{{ dashboard.totalVehicles }}</strong>
      </article>
      <article class="stat-card">
        <span class="stat-label">充电请求</span>
        <strong class="stat-value">{{ dashboard.totalRequests }}</strong>
      </article>
      <article class="stat-card">
        <span class="stat-label">进行中会话</span>
        <strong class="stat-value">{{ dashboard.activeSessions }}</strong>
      </article>
      <article class="stat-card highlight">
        <span class="stat-label">今日收入（元）</span>
        <strong class="stat-value">{{ formatMoney(dashboard.todayRevenue) }}</strong>
      </article>
      <article v-if="dashboard.capacityOverview" class="stat-card">
        <span class="stat-label">已开放充电桩</span>
        <strong class="stat-value">{{ dashboard.capacityOverview.enabledPiles }}</strong>
      </article>
      <article v-if="dashboard.capacityOverview" class="stat-card">
        <span class="stat-label">开放排队位置</span>
        <strong class="stat-value">
          {{ dashboard.capacityOverview.totalOpenQueueSlots }}/{{ dashboard.capacityOverview.totalMaxQueueSlots }}
        </strong>
      </article>
      <article v-if="dashboard.pileSummary" class="stat-card wide">
        <span class="stat-label">充电桩状态分布</span>
        <div class="pile-summary">
          <span>空闲 {{ dashboard.pileSummary.idle }}</span>
          <span>充电/预约 {{ dashboard.pileSummary.charging }}</span>
          <span>故障 {{ dashboard.pileSummary.fault }}</span>
          <span>离线 {{ dashboard.pileSummary.offline }}</span>
        </div>
      </article>
    </section>

    <section v-if="dashboard?.capacityOverview" class="panel">
      <div class="panel-head">
        <h2>容量配置总览</h2>
      </div>
      <div class="capacity-summary">
        <article>
          <span>快充开放桩</span>
          <strong>{{ dashboard.capacityOverview.fastEnabledPiles }}</strong>
        </article>
        <article>
          <span>慢充开放桩</span>
          <strong>{{ dashboard.capacityOverview.slowEnabledPiles }}</strong>
        </article>
        <article>
          <span>总桩数</span>
          <strong>{{ dashboard.capacityOverview.totalPiles }}</strong>
        </article>
      </div>
      <p class="muted panel-note">
        现在管理员不仅能监控故障，还能直接控制哪些桩开放，以及每个桩后允许排队的开放位置数量。
      </p>
    </section>

    <section class="panel">
      <div class="panel-head">
        <h2>充电桩监控</h2>
      </div>
      <div v-if="!piles.length" class="muted empty">暂无充电桩数据</div>
      <div v-else class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>编号</th>
              <th>模式</th>
              <th>功率</th>
              <th>开放状态</th>
              <th>开放位置</th>
              <th>状态</th>
              <th>当前会话</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="pile in piles" :key="pile.pileId">
              <td><strong>{{ pile.code }}</strong></td>
              <td>{{ getStatusDesc('chargeMode', pile.mode) }}</td>
              <td>{{ pile.power }} kW</td>
              <td>
                <span class="badge" :class="pile.enabled ? 'idle' : 'offline'">
                  {{ pile.enabled ? '开放中' : '已关闭' }}
                </span>
              </td>
              <td>{{ pile.openQueueSlots }}/{{ pile.maxQueueSlots }}</td>
              <td>
                <span class="badge" :class="statusClass(pile.status)">
                  {{ getStatusDesc('chargingPileStatus', pile.status) }}
                </span>
              </td>
              <td>
                <template v-if="pile.currentSession">
                  #{{ pile.currentSession.sessionId }}
                  · {{ pile.currentSession.chargedAmount }}/{{ pile.currentSession.targetAmount }} kWh
                </template>
                <span v-else class="muted">—</span>
              </td>
              <td class="actions">
                <button
                  :disabled="actionPileId === pile.pileId"
                  @click="togglePileEnabled(pile)"
                >
                  {{ pile.enabled ? '关闭桩位' : '开放桩位' }}
                </button>
                <button
                  :disabled="actionPileId === pile.pileId"
                  @click="updateQueueSlots(pile)"
                >
                  调整位置
                </button>
                <button
                  v-if="pile.status !== 'FAULT'"
                  class="btn-danger"
                  :disabled="actionPileId === pile.pileId"
                  @click="markFault(pile)"
                >
                  标记故障
                </button>
                <button
                  v-else
                  :disabled="actionPileId === pile.pileId"
                  @click="recoverPile(pile)"
                >
                  恢复
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-if="actionMessage" class="action-msg">{{ actionMessage }}</p>
    </section>

    <section v-if="queue" class="queue-grid">
      <article class="panel">
        <div class="panel-head">
          <h2>快充队列</h2>
          <span class="tag">{{ queue.fastQueue.queueLength }} / {{ queue.fastQueue.totalOpenQueueSlots }} 位置</span>
        </div>
        <p class="muted queue-meta">
          空闲桩 {{ queue.fastQueue.availablePileCount }} 台，剩余排队位置 {{ queue.fastQueue.remainingQueueCapacity }}
        </p>
        <QueueList :items="queue.fastQueue.waitingList" />
      </article>
      <article class="panel">
        <div class="panel-head">
          <h2>慢充队列</h2>
          <span class="tag">{{ queue.slowQueue.queueLength }} / {{ queue.slowQueue.totalOpenQueueSlots }} 位置</span>
        </div>
        <p class="muted queue-meta">
          空闲桩 {{ queue.slowQueue.availablePileCount }} 台，剩余排队位置 {{ queue.slowQueue.remainingQueueCapacity }}
        </p>
        <QueueList :items="queue.slowQueue.waitingList" />
      </article>
    </section>
  </div>
</template>

<script>
import {
  getAdminDashboard,
  getAdminPiles,
  getAdminQueue,
  markPileFault,
  recoverPile as recoverPileApi,
  updatePileCapacity
} from '../api';
import { formatPileCapacityMessage } from '../api/pileCapacity';
import { getStatusDesc } from '../api/enums';
import QueueList from '../components/QueueList.vue';
import { authState, clearSession } from '../session';

export default {
  name: 'Admin',
  components: { QueueList },
  data() {
    return {
      dashboard: null,
      piles: [],
      queue: null,
      loading: false,
      error: '',
      actionPileId: null,
      actionMessage: ''
    };
  },
  mounted() {
    this.refreshAll();
  },
  computed: {
    adminName() {
      return authState.session.username || 'admin';
    }
  },
  methods: {
    getStatusDesc,
    formatMoney(value) {
      const num = Number(value);
      return Number.isFinite(num) ? num.toFixed(2) : '0.00';
    },
    statusClass(status) {
      return {
        idle: status === 'IDLE',
        busy: status === 'CHARGING' || status === 'RESERVED',
        fault: status === 'FAULT',
        offline: status === 'OFFLINE'
      };
    },
    async refreshAll() {
      this.loading = true;
      this.error = '';
      try {
        const [dashboard, piles, queue] = await Promise.all([
          getAdminDashboard(),
          getAdminPiles(),
          getAdminQueue()
        ]);
        this.dashboard = dashboard;
        this.piles = piles;
        this.queue = queue;
      } catch (err) {
        this.error = err.message || '加载管理端数据失败';
        if (/登录|管理权限|登录态/.test(this.error)) {
          clearSession();
          this.$router.push({ name: 'Home', query: { redirect: '/admin' } });
        }
      } finally {
        this.loading = false;
      }
    },
    async markFault(pile) {
      const reason = window.prompt('请输入故障原因', '设备异常');
      if (reason === null) return;

      this.actionPileId = pile.pileId;
      this.actionMessage = '';
      try {
        await markPileFault(pile.pileId, { faultReason: reason });
        this.actionMessage = `充电桩 ${pile.code} 已标记为故障`;
        await this.refreshAll();
      } catch (err) {
        this.actionMessage = err.message || '标记故障失败';
      } finally {
        this.actionPileId = null;
      }
    },
    async recoverPile(pile) {
      this.actionPileId = pile.pileId;
      this.actionMessage = '';
      try {
        const result = await recoverPileApi(pile.pileId);
        this.actionMessage = result.dispatchResult
          ? `充电桩 ${pile.code} 已恢复，并完成一次自动调度`
          : `充电桩 ${pile.code} 已恢复为空闲`;
        await this.refreshAll();
      } catch (err) {
        this.actionMessage = err.message || '恢复失败';
      } finally {
        this.actionPileId = null;
      }
    },
    async togglePileEnabled(pile) {
      this.actionPileId = pile.pileId;
      this.actionMessage = '';
      try {
        const opening = !pile.enabled;
        const result = await updatePileCapacity(pile.pileId, { enabled: opening });
        this.actionMessage = formatPileCapacityMessage(pile.code, result, { opening });
        await this.refreshAll();
      } catch (err) {
        this.actionMessage = err.message || '更新开放状态失败';
      } finally {
        this.actionPileId = null;
      }
    },
    async updateQueueSlots(pile) {
      const input = window.prompt(
        `请输入 ${pile.code} 的开放位置数量（固定上限 ${pile.maxQueueSlots}）`,
        String(pile.openQueueSlots)
      );
      if (input === null) return;
      const openQueueSlots = Number(input);
      if (!Number.isInteger(openQueueSlots)) {
        this.actionMessage = '请输入整数位置数量';
        return;
      }
      this.actionPileId = pile.pileId;
      this.actionMessage = '';
      try {
        const result = await updatePileCapacity(pile.pileId, { openQueueSlots });
        this.actionMessage = formatPileCapacityMessage(pile.code, result);
        await this.refreshAll();
      } catch (err) {
        this.actionMessage = err.message || '更新开放位置失败';
      } finally {
        this.actionPileId = null;
      }
    },
    logout() {
      clearSession();
      this.$router.push('/');
    }
  }
};
</script>

<style scoped>
.admin {
  min-height: 100vh;
  padding: 48px 24px 64px;
  color: #172033;
  background:
    radial-gradient(circle at top right, rgba(163, 196, 255, 0.45), transparent 30%),
    linear-gradient(180deg, #eef2fb 0%, #f8f5ef 100%);
}

.hero,
.stats-grid,
.panel,
.queue-grid {
  max-width: 1120px;
  margin: 0 auto;
}

.eyebrow {
  margin: 0 0 12px;
  font-size: 12px;
  letter-spacing: 0.24em;
  text-transform: uppercase;
  color: #2d5db7;
}

.hero h1 {
  margin: 0;
  font-size: clamp(32px, 5vw, 48px);
}

.subtitle {
  max-width: 720px;
  margin: 16px 0 0;
  line-height: 1.7;
  color: #4a5670;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-top: 24px;
}

.admin-chip {
  display: flex;
  flex-direction: column;
  padding: 10px 14px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.8);
  color: #172033;
}

.admin-name {
  font-size: 14px;
  font-weight: 700;
}

.admin-role {
  font-size: 12px;
  color: #667186;
}

button,
.nav-link {
  border: none;
  border-radius: 999px;
  background: #172033;
  color: #fff;
  cursor: pointer;
  transition: transform 0.2s ease, opacity 0.2s ease;
}

button {
  padding: 10px 18px;
  font-size: 14px;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.65;
}

button:not(:disabled):hover,
.nav-link:hover {
  transform: translateY(-1px);
}

.nav-link {
  display: inline-flex;
  align-items: center;
  padding: 10px 18px;
  text-decoration: none;
}

.action-link {
  justify-content: center;
}

.btn-danger {
  background: #bc3b2f;
}

.error.banner {
  max-width: 1120px;
  margin: 20px auto 0;
  padding: 12px 16px;
  border-radius: 12px;
  background: rgba(188, 59, 47, 0.1);
  color: #bc3b2f;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 16px;
  margin-top: 32px;
}

.stat-card {
  padding: 20px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.88);
  box-shadow: 0 12px 40px rgba(31, 44, 71, 0.06);
}

.stat-card.wide {
  grid-column: span 2;
}

.stat-card.highlight {
  background: linear-gradient(135deg, #172033, #2d4a7a);
  color: #fff;
}

.stat-card.highlight .stat-label {
  color: rgba(255, 255, 255, 0.75);
}

.stat-label {
  display: block;
  font-size: 13px;
  color: #69748b;
}

.stat-value {
  display: block;
  margin-top: 8px;
  font-size: 28px;
}

.pile-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 20px;
  margin-top: 12px;
  font-size: 14px;
}

.capacity-summary {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 14px;
}

.capacity-summary article {
  padding: 18px;
  border-radius: 18px;
  background: #f5f8fd;
}

.capacity-summary span {
  display: block;
  font-size: 13px;
  color: #69748b;
}

.capacity-summary strong {
  display: block;
  margin-top: 8px;
  font-size: 24px;
}

.panel-note,
.queue-meta {
  margin: 14px 0 0;
}

.panel {
  margin-top: 24px;
  padding: 24px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.88);
  box-shadow: 0 18px 60px rgba(31, 44, 71, 0.08);
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.panel-head h2 {
  margin: 0;
  font-size: 20px;
}

.muted {
  color: #69748b;
}

.empty {
  padding: 12px 0;
}

.table-wrap {
  overflow-x: auto;
}

table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}

th,
td {
  padding: 12px 10px;
  text-align: left;
  border-bottom: 1px solid rgba(23, 32, 51, 0.08);
}

th {
  font-size: 12px;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: #69748b;
}

.badge {
  display: inline-block;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  background: #edf1f7;
}

.badge.idle {
  background: #e6f4ea;
  color: #1e6b3a;
}

.badge.busy {
  background: #fff3e0;
  color: #b75d2d;
}

.badge.fault {
  background: #fdecea;
  color: #bc3b2f;
}

.badge.offline {
  background: #eef0f4;
  color: #69748b;
}

.actions button {
  padding: 6px 12px;
  font-size: 12px;
  margin-right: 8px;
}

.action-msg {
  margin: 16px 0 0;
  color: #2d5db7;
}

.queue-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 20px;
  margin-top: 24px;
}

.tag {
  padding: 4px 12px;
  border-radius: 999px;
  font-size: 12px;
  background: #edf1f7;
  color: #4a5670;
}

@media (max-width: 720px) {
  .admin {
    padding: 32px 16px 48px;
  }

  .stat-card.wide {
    grid-column: span 1;
  }

  .panel-head {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
