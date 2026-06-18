<template>
  <div class="user-portal">
    <!-- 未登录：门户首页 -->
    <template v-if="!session.userId">
      <header class="hero">
        <p class="eyebrow">Campus EV Charging</p>
        <h1>校园智能充电服务</h1>
        <p class="subtitle">
          在线预约充电桩、实时查看排队进度、充电结束后一键支付。支持快充与慢充，分时电价透明可查。
        </p>
        <p class="multi-user-hint">每个浏览器标签页可独立登录不同账号，新开标签页即可模拟多用户演示。</p>
      </header>

      <section class="highlights">
        <article class="highlight-card">
          <span class="highlight-icon">⚡</span>
          <h3>快充 / 慢充</h3>
          <p>60kW 快充与 7kW 慢充，按需求自由选择。</p>
        </article>
        <article class="highlight-card">
          <span class="highlight-icon">📋</span>
          <h3>智能排队</h3>
          <p>系统自动分配最优桩位，减少等待时间。</p>
        </article>
        <article class="highlight-card">
          <span class="highlight-icon">💳</span>
          <h3>在线结算</h3>
          <p>充电结束生成账单，支持微信模拟支付。</p>
        </article>
      </section>

      <section class="panel auth-panel">
        <div class="auth-tabs">
          <button type="button" :class="{ active: authTab === 'login' }" @click="authTab = 'login'">登录</button>
          <button type="button" :class="{ active: authTab === 'register' }" @click="authTab = 'register'">注册账号</button>
        </div>

        <form v-if="authTab === 'login'" class="auth-form" @submit.prevent="handleLogin">
          <label>
            <span>用户名</span>
            <input v-model="loginForm.username" placeholder="请输入用户名" required />
          </label>
          <label>
            <span>密码</span>
            <input v-model="loginForm.password" type="password" placeholder="请输入密码" required />
          </label>
          <button type="submit" class="btn-primary" :disabled="loading.login">
            {{ loading.login ? '登录中…' : '登录并开始使用' }}
          </button>
        </form>

        <form v-else class="auth-form" @submit.prevent="handleRegister">
          <label>
            <span>用户名</span>
            <input v-model="registerForm.username" placeholder="设置用户名" required />
          </label>
          <label>
            <span>密码</span>
            <input v-model="registerForm.password" type="password" placeholder="设置密码" required />
          </label>
          <label>
            <span>手机号</span>
            <input v-model="registerForm.phone" placeholder="用于接收充电通知" required />
          </label>
          <button type="submit" class="btn-primary" :disabled="loading.register">
            {{ loading.register ? '注册中…' : '注册账号' }}
          </button>
        </form>
      </section>
    </template>

    <!-- 已登录：用户中心 -->
    <template v-else>
      <header class="hero compact">
        <div class="hero-row">
          <div>
            <p class="eyebrow">Welcome back</p>
            <h1>您好，{{ session.username }}</h1>
            <p class="subtitle">选择车辆与充电方式，完成预约后在指定桩位插枪即可开始充电。</p>
          </div>
          <button type="button" class="btn-outline" @click="logout">退出登录</button>
        </div>
      </header>

      <nav class="page-tabs">
        <button
          v-for="tab in pageTabs"
          :key="tab.id"
          type="button"
          :class="{ active: pageTab === tab.id }"
          @click="pageTab = tab.id"
        >
          {{ tab.label }}
          <span v-if="tab.id === 'bills' && unpaidCount" class="tab-badge">{{ unpaidCount }}</span>
          <span v-if="tab.id === 'orders' && ongoingOrderCount" class="tab-badge">{{ ongoingOrderCount }}</span>
        </button>
      </nav>

      <ol class="step-track">
        <li v-for="(step, i) in flowSteps" :key="step.id" :class="{ done: step.done, current: step.current }">
          <span class="step-num">{{ step.done ? '✓' : i + 1 }}</span>
          <span class="step-text">{{ step.label }}</span>
        </li>
      </ol>

      <p v-if="message" class="banner" :class="messageType">{{ message }}</p>

      <!-- 预约充电 -->
      <div v-show="pageTab === 'charge'" class="layout-main">
        <div class="layout-primary">
          <!-- 进行中的订单 -->
          <article v-if="activeRequest && !isTerminalRequest" class="panel order-panel">
            <div class="panel-head">
              <h2>当前充电订单</h2>
              <span class="order-id">订单号 {{ activeRequest.requestId }}</span>
            </div>
            <div class="order-status">
              <span class="status-pill" :class="statusPillClass">{{ orderStatusText }}</span>
              <span v-if="dispatchResult?.pileCode" class="pile-tag">桩位 {{ dispatchResult.pileCode }}</span>
            </div>
            <div class="order-progress">
              <div class="progress-bar">
                <div class="progress-fill" :style="{ width: `${chargePercent}%` }" />
              </div>
              <p class="progress-caption">
                已充 <strong>{{ displayCharged }}</strong> / {{ displayTarget }} kWh
                <span v-if="currentSession && chargingRemainingText" class="countdown"> · {{ chargingRemainingText }}</span>
              </p>
            </div>
            <dl class="order-details">
              <div><dt>充电方式</dt><dd>{{ getStatusDesc('chargeMode', activeRequest.mode) }}</dd></div>
              <div><dt>排队序号</dt><dd>{{ activeRequest.queueNumber ?? '—' }}</dd></div>
              <div><dt>分配桩位</dt><dd>{{ activeRequest.assignedPileId ? `#${activeRequest.assignedPileId}` : '等待分配' }}</dd></div>
            </dl>

            <div class="order-actions">
              <p v-if="activeRequest.status === 'WAITING'" class="muted inline-tip">
                系统会在有空闲桩位时自动尝试分配，无需手动操作。
              </p>
              <p v-if="activeRequest.status === 'ASSIGNED' && assignmentCountdownText" class="assignment-timeout">
                {{ assignmentCountdownText }}
              </p>
              <button
                v-if="canStartSession"
                type="button"
                class="btn-primary"
                :disabled="loading.start"
                @click="handleStartSession"
              >
                {{ loading.start ? '启动中…' : '插枪并开始充电' }}
              </button>
              <template v-if="currentSession">
                <p class="charging-live">充电进行中，进度将自动更新</p>
                <button
                  type="button"
                  class="btn-outline"
                  :disabled="loading.stop || autoStopping"
                  @click="handleEarlyStop"
                >
                  {{ loading.stop || autoStopping ? '结算中…' : '提前结束充电' }}
                </button>
                <p class="muted inline-tip">充至目标电量后将自动结束并生成账单</p>
              </template>
              <button type="button" class="btn-text" :disabled="loading.refreshRequest" @click="refreshActiveRequest">
                刷新订单状态
              </button>
              <button v-if="canCancel" type="button" class="btn-text danger" :disabled="loading.cancel" @click="handleCancelRequest">
                取消订单
              </button>
              <button
                v-if="activeRequest.status === 'WAITING'"
                type="button"
                class="btn-text"
                :disabled="loading.modify"
                @click="handleModifyRequest"
              >
                调整目标电量
              </button>
            </div>

            <div v-if="dispatchResult && !currentSession" class="notice success">
              已为您分配 <strong>{{ dispatchResult.pileCode }}</strong> 号桩，预计充电
              {{ dispatchResult.estimatedDurationMinutes }} 分钟。请前往对应车位插枪。
              <span v-if="activeRequest.status === 'ASSIGNED'">若 {{ assignmentTimeoutMinutes }} 分钟内未手动开始，系统将自动开始计费。</span>
            </div>
          </article>

          <!-- 新建预约 -->
          <article v-else class="panel">
            <h2>预约充电</h2>
            <p class="muted">请先选择车辆与充电方式，提交后进入排队队列。</p>

            <div v-if="!vehicles.length" class="empty-block">
              <p>您还没有绑定车辆</p>
              <button type="button" class="btn-secondary" @click="pageTab = 'vehicles'">去绑定车辆</button>
            </div>

            <template v-else>
              <p class="field-label">选择车辆</p>
              <div class="vehicle-cards">
                <button
                  v-for="v in vehicles"
                  :key="v.vehicleId"
                  type="button"
                  class="vehicle-card"
                  :class="{ active: selectedVehicleId === v.vehicleId }"
                  @click="selectedVehicleId = v.vehicleId"
                >
                  <span class="plate">{{ v.plateNumber }}</span>
                  <span class="meta">{{ v.model }}</span>
                </button>
              </div>

              <p class="field-label">充电方式</p>
              <div class="mode-cards">
                <button
                  type="button"
                  class="mode-card"
                  :class="{ active: requestForm.mode === 'FAST' }"
                  @click="switchMode('FAST')"
                >
                  <strong>快充</strong>
                  <span>约 60 kW · 适合赶时间</span>
                </button>
                <button
                  type="button"
                  class="mode-card"
                  :class="{ active: requestForm.mode === 'SLOW' }"
                  @click="switchMode('SLOW')"
                >
                  <strong>慢充</strong>
                  <span>约 7 kW · 更经济温和</span>
                </button>
              </div>

              <label class="field-block">
                <span class="field-label">目标充电量 (kWh)</span>
                <input v-model.number="requestForm.targetAmount" type="number" step="1" min="5" />
              </label>

              <button
                type="button"
                class="btn-primary wide"
                :disabled="loading.request || !selectedVehicleId"
                @click="handleSubmitRequest"
              >
                {{ loading.request ? '提交中…' : '立即预约充电' }}
              </button>
            </template>
          </article>
        </div>

        <aside class="layout-side">
          <article class="panel">
            <div class="panel-head">
              <h2>排队情况</h2>
              <button type="button" class="btn-text" :disabled="loading.queue" @click="loadQueue">刷新</button>
            </div>
            <p class="queue-summary">
              {{ getStatusDesc('chargeMode', requestForm.mode) }} ·
              前方 <strong>{{ queueInfo.queueLength ?? 0 }}</strong> 辆 ·
              空闲桩 <strong>{{ queueInfo.availablePileCount ?? 0 }}</strong> 台
            </p>
            <QueueList :items="queueInfo.waitingList || []" />
          </article>

          <article class="panel tips-panel">
            <h2>温馨提示</h2>
            <ul class="tips-list">
              <li>预约成功后请等待系统分配桩位，收到桩号后再前往车位。</li>
              <li>插枪后点击「开始充电」；充满或需离开时结束充电并支付账单。</li>
              <li>费用按分时电价与服务费计算，详情可在「账单记录」中查看。</li>
            </ul>
          </article>
        </aside>
      </div>

      <!-- 我的车辆 -->
      <section v-show="pageTab === 'vehicles'" class="panel">
        <div class="panel-head">
          <h2>我的车辆</h2>
          <button type="button" class="btn-text" :disabled="loading.vehicles" @click="loadVehicles">刷新</button>
        </div>

        <div v-if="vehicles.length" class="vehicle-cards wide">
          <article
            v-for="v in vehicles"
            :key="v.vehicleId"
            class="vehicle-card static"
            :class="{ active: selectedVehicleId === v.vehicleId }"
          >
            <div @click="selectedVehicleId = v.vehicleId" class="vehicle-info">
              <span class="plate">{{ v.plateNumber }}</span>
              <span class="meta">{{ v.model }}</span>
              <span class="cap">电池 {{ v.batteryCapacity }} kWh</span>
            </div>
            <button 
              class="btn-delete" 
              @click.stop="handleUnbindVehicle(v.vehicleId, v.plateNumber)"
              title="解绑车辆"
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18"></line>
                <line x1="6" y1="6" x2="18" y2="18"></line>
              </svg>
            </button>
          </article>
        </div>
        <p v-else class="muted">暂无车辆，请填写下方信息完成绑定。</p>

        <div class="bind-section">
          <h3>绑定新车辆</h3>
          <form class="bind-form" @submit.prevent="handleBindVehicle">
            <div class="bind-grid">
              <label>
                <span>车牌号</span>
                <input v-model="vehicleForm.plateNumber" placeholder="例如 京A12345" required />
              </label>
              <label>
                <span>车型</span>
                <input v-model="vehicleForm.model" placeholder="例如 Tesla Model 3" required />
              </label>
              <label>
                <span>电池容量 (kWh)</span>
                <input v-model.number="vehicleForm.batteryCapacity" type="number" step="0.1" required />
              </label>
            </div>
            <button type="submit" class="btn-primary" :disabled="loading.bind">
              {{ loading.bind ? '绑定中…' : '确认绑定' }}
            </button>
          </form>
        </div>
      </section>

      <!-- 我的订单 -->
      <section v-show="pageTab === 'orders'" class="panel">
        <div class="panel-head">
          <h2>我的充电订单</h2>
          <button type="button" class="btn-text" :disabled="loading.orders" @click="loadOrders">刷新</button>
        </div>
        <p class="muted">查看全部充电预约与完成情况；进行中的订单可点击「继续处理」返回预约页。</p>

        <p v-if="!orders.length" class="empty-block muted">暂无订单，提交预约后会显示在这里。</p>

        <div v-else class="table-wrap">
          <table class="bill-table">
            <thead>
              <tr>
                <th>订单号</th>
                <th>车辆</th>
                <th>方式</th>
                <th>目标 / 已充</th>
                <th>桩位</th>
                <th>状态</th>
                <th>下单时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="order in orders" :key="order.requestId">
                <td><strong>#{{ order.requestId }}</strong></td>
                <td>{{ order.plateNumber || '—' }}</td>
                <td>{{ getStatusDesc('chargeMode', order.mode) }}</td>
                <td>{{ order.targetAmount }} / {{ order.chargedAmount ?? 0 }} kWh</td>
                <td>{{ order.assignedPileId ? `#${order.assignedPileId}` : '—' }}</td>
                <td>
                  <span class="status-pill small" :class="orderStatusClass(order.status)">
                    {{ getStatusDesc('chargingRequestStatus', order.status) }}
                  </span>
                </td>
                <td class="time-cell">{{ formatTime(order.createdAt) }}</td>
                <td>
                  <button
                    v-if="!isOrderTerminal(order.status)"
                    type="button"
                    class="btn-text"
                    @click="openOrder(order)"
                  >
                    继续处理
                  </button>
                  <span v-else class="muted">已完成</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <!-- 账单记录 -->
      <section v-show="pageTab === 'bills'" class="panel">
        <div class="panel-head">
          <h2>账单记录</h2>
          <button type="button" class="btn-text" :disabled="loading.bills" @click="loadBills">刷新</button>
        </div>

        <p v-if="!bills.length" class="empty-block muted">暂无账单，完成一次充电后会自动生成。</p>

        <div v-else class="table-wrap">
          <table class="bill-table">
            <thead>
              <tr>
                <th>账单号</th>
                <th>电费</th>
                <th>服务费</th>
                <th>合计</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="bill in bills" :key="bill.billId">
                <td><strong>#{{ bill.billId }}</strong></td>
                <td>¥ {{ bill.electricityFee }}</td>
                <td>¥ {{ bill.serviceFee }}</td>
                <td><strong>¥ {{ bill.totalFee }}</strong></td>
                <td>
                  <span class="status-pill small" :class="bill.status.toLowerCase()">
                    {{ getStatusDesc('billStatus', bill.status) }}
                  </span>
                </td>
                <td>
                  <button
                    v-if="bill.status === 'UNPAID'"
                    type="button"
                    class="btn-pay"
                    :disabled="loading.pay === bill.billId"
                    @click="handlePayBill(bill.billId)"
                  >
                    去支付
                  </button>
                  <span v-else class="muted">—</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </template>
  </div>
</template>

<script>
import {
  registerUser,
  loginUser,
  bindVehicle,
  getUserVehicles,
  unbindVehicle,
  submitChargingRequest,
  getChargingRequest,
  getUserChargingRequests,
  cancelChargingRequest,
  modifyChargingRequest,
  getSchedulerQueue,
  dispatchScheduler,
  startSession,
  getActiveSession,
  stopSession,
  getUserBills,
  payBill
} from '../api';
import { getStatusDesc } from '../api/enums';
import QueueList from '../components/QueueList.vue';
import { authState, clearSession, setSession } from '../session';

export default {
  name: 'UserPortal',
  components: { QueueList },
  data() {
    return {
      authTab: 'login',
      pageTab: 'charge',
      session: { userId: null, username: '', token: '', role: '' },
      registerForm: { username: '', password: '', phone: '' },
      loginForm: { username: '', password: '' },
      vehicleForm: { plateNumber: '', model: '', batteryCapacity: 60 },
      vehicles: [],
      selectedVehicleId: null,
      requestForm: { mode: 'FAST', targetAmount: 30 },
      activeRequest: null,
      queueInfo: {},
      dispatchResult: null,
      currentSession: null,
      simulatedCharged: 0,
      chargeRateKwhPerSec: 0,
      chargeTimerId: null,
      chargeUiTick: 0,
      assignmentPollTimerId: null,
      assignmentCountdownTimerId: null,
      assignmentUiTick: 0,
      autoStopping: false,
      stopResult: null,
      bills: [],
      orders: [],
      message: '',
      messageType: 'info',
      pageTabs: [
        { id: 'charge', label: '预约充电' },
        { id: 'orders', label: '我的订单' },
        { id: 'vehicles', label: '我的车辆' },
        { id: 'bills', label: '账单记录' }
      ],
      loading: {
        register: false,
        login: false,
        vehicles: false,
        bind: false,
        request: false,
        refreshRequest: false,
        cancel: false,
        modify: false,
        queue: false,
        dispatch: false,
        start: false,
        stop: false,
        bills: false,
        orders: false,
        pay: null
      }
    };
  },
  computed: {
    canCancel() {
      return this.activeRequest && ['WAITING', 'ASSIGNED'].includes(this.activeRequest.status);
    },
    canStartSession() {
      return (
        this.activeRequest &&
        this.activeRequest.status === 'ASSIGNED' &&
        this.activeRequest.assignedPileId &&
        !this.currentSession
      );
    },
    isTerminalRequest() {
      return this.activeRequest && ['CANCELLED', 'COMPLETED'].includes(this.activeRequest.status);
    },
    unpaidCount() {
      return this.bills.filter((b) => b.status === 'UNPAID').length;
    },
    ongoingOrderCount() {
      return this.orders.filter((o) => !this.isOrderTerminal(o.status)).length;
    },
    orderStatusText() {
      if (this.currentSession) return '充电中';
      if (!this.activeRequest) return '';
      return getStatusDesc('chargingRequestStatus', this.activeRequest.status);
    },
    statusPillClass() {
      if (this.currentSession) return 'charging';
      if (!this.activeRequest) return '';
      const s = this.activeRequest.status;
      if (s === 'WAITING') return 'waiting';
      if (s === 'ASSIGNED') return 'assigned';
      if (s === 'CHARGING') return 'charging';
      return '';
    },
    displayCharged() {
      void this.chargeUiTick;
      if (this.currentSession) return this.simulatedCharged.toFixed(2);
      return Number(this.activeRequest?.chargedAmount ?? 0).toFixed(2);
    },
    displayTarget() {
      if (this.currentSession) return this.currentSession.targetAmount;
      return this.activeRequest?.targetAmount ?? this.requestForm.targetAmount;
    },
    chargePercent() {
      void this.chargeUiTick;
      const target = Number(this.displayTarget) || 1;
      const charged = Number(this.displayCharged) || 0;
      return Math.min(100, (charged / target) * 100);
    },
    chargingRemainingText() {
      void this.chargeUiTick;
      if (!this.currentSession || !this.chargeRateKwhPerSec) return '';
      const target = Number(this.currentSession.targetAmount) || 0;
      const remainingKwh = Math.max(0, target - this.simulatedCharged);
      if (remainingKwh <= 0.001) return '即将充满';
      const seconds = Math.max(0, Math.ceil(remainingKwh / this.chargeRateKwhPerSec));
      const mm = Math.floor(seconds / 60);
      const ss = String(seconds % 60).padStart(2, '0');
      return `剩余 ${mm}:${ss}`;
    },
    assignmentTimeoutMinutes() {
      return this.activeRequest?.assignmentTimeoutMinutes ?? 5;
    },
    assignmentCountdownText() {
      void this.assignmentUiTick;
      if (this.activeRequest?.status !== 'ASSIGNED') return '';
      const deadline = this.parseDateTime(this.activeRequest.autoStartAt)
        || this.computeAutoStartDeadline();
      if (!deadline) {
        return `请在 ${this.assignmentTimeoutMinutes} 分钟内插枪并开始充电，超时将自动开始计费`;
      }
      const remainingMs = deadline.getTime() - Date.now();
      if (remainingMs <= 0) return '即将自动开始充电…';
      const totalSec = Math.ceil(remainingMs / 1000);
      const mm = Math.floor(totalSec / 60);
      const ss = String(totalSec % 60).padStart(2, '0');
      return `请在 ${mm}:${ss} 内插枪并开始充电，超时将自动开始计费`;
    },
    flowSteps() {
      const hasVehicle = this.vehicles.length > 0;
      const hasRequest = this.activeRequest && !this.isTerminalRequest;
      const charging = !!this.currentSession || this.activeRequest?.status === 'CHARGING';
      const paid = this.bills.some((b) => b.status === 'PAID');
      return [
        { id: 'vehicle', label: '选择车辆', done: hasVehicle, current: !hasVehicle },
        { id: 'book', label: '提交预约', done: hasRequest || charging, current: hasVehicle && !hasRequest && !charging },
        { id: 'charge', label: '到站充电', done: charging || this.activeRequest?.status === 'COMPLETED', current: hasRequest && !charging },
        { id: 'pay', label: '支付账单', done: paid, current: !paid && this.activeRequest?.status === 'COMPLETED' }
      ];
    }
  },
  watch: {
    'activeRequest.status': {
      handler(status) {
        if (status === 'ASSIGNED') {
          this.startAssignmentPolling();
        } else {
          this.stopAssignmentPolling();
          if (status === 'CHARGING' && !this.currentSession) {
            this.loadActiveSessionAndStartSimulation();
          }
        }
      }
    }
  },
  mounted() {
    this.restoreSession();
  },
  beforeUnmount() {
    this.stopChargeSimulation();
    this.stopAssignmentPolling();
  },
  methods: {
    getStatusDesc,
    isOrderTerminal(status) {
      return ['CANCELLED', 'COMPLETED'].includes(status);
    },
    orderStatusClass(status) {
      if (status === 'WAITING') return 'waiting';
      if (status === 'ASSIGNED') return 'assigned';
      if (status === 'CHARGING') return 'charging';
      if (status === 'COMPLETED') return 'paid';
      if (status === 'CANCELLED') return 'cancelled';
      return '';
    },
    formatTime(value) {
      if (!value) return '—';
      return String(value).replace('T', ' ').slice(0, 16);
    },
    openOrder(order) {
      this.activeRequest = order;
      this.requestForm.mode = order.mode;
      this.requestForm.targetAmount = order.targetAmount;
      this.pageTab = 'charge';
      this.loadQueue();
      this.notify(`已打开订单 #${order.requestId}`, 'info');
    },
    syncActiveFromOrders() {
      const ongoing = this.orders.find((o) => !this.isOrderTerminal(o.status));
      if (ongoing) {
        this.activeRequest = ongoing;
        this.requestForm.mode = ongoing.mode;
      }
    },
    switchMode(mode) {
      this.requestForm.mode = mode;
      this.loadQueue();
    },
    notify(text, type = 'info') {
      this.message = text;
      this.messageType = type;
    },
    saveSession() {
      setSession(this.session);
    },
    restoreSession() {
      const saved = authState.session;
      if (saved.userId) {
        this.session = { ...saved };
        this.loginForm.username = saved.username || '';
        this.loadVehicles();
        this.loadBills();
        this.loadOrders().then(() => this.restoreChargeStateIfNeeded());
        this.loadQueue();
      }
    },
    chargeStateStorageKey() {
      return `smart_charging_active_charge_${this.session.userId || 'guest'}`;
    },
    persistChargeState() {
      if (!this.currentSession?.sessionId) return;
      sessionStorage.setItem(
        this.chargeStateStorageKey(),
        JSON.stringify({
          sessionId: this.currentSession.sessionId,
          requestId: this.currentSession.requestId,
          pileId: this.currentSession.pileId,
          mode: this.currentSession.mode,
          targetAmount: this.currentSession.targetAmount,
          startTime: this.currentSession.startTime,
          estimatedEndTime: this.currentSession.estimatedEndTime,
          simulatedCharged: this.simulatedCharged
        })
      );
    },
    clearPersistedChargeState() {
      sessionStorage.removeItem(this.chargeStateStorageKey());
    },
    parseDateTime(value) {
      if (!value) return null;
      return new Date(String(value).replace(' ', 'T'));
    },
    computeAutoStartDeadline() {
      const assignedAt = this.parseDateTime(this.activeRequest?.assignedAt);
      if (!assignedAt) return null;
      return new Date(assignedAt.getTime() + this.assignmentTimeoutMinutes * 60 * 1000);
    },
    startAssignmentPolling() {
      this.stopAssignmentPolling();
      if (this.activeRequest?.status !== 'ASSIGNED') return;
      this.assignmentPollTimerId = window.setInterval(() => {
        this.refreshActiveRequest({ silent: true });
      }, 5000);
      this.assignmentCountdownTimerId = window.setInterval(() => {
        this.assignmentUiTick += 1;
      }, 1000);
    },
    stopAssignmentPolling() {
      if (this.assignmentPollTimerId) {
        clearInterval(this.assignmentPollTimerId);
        this.assignmentPollTimerId = null;
      }
      if (this.assignmentCountdownTimerId) {
        clearInterval(this.assignmentCountdownTimerId);
        this.assignmentCountdownTimerId = null;
      }
    },
    async loadActiveSessionAndStartSimulation({ notifyAutoStart = true } = {}) {
      if (!this.activeRequest?.requestId || this.currentSession) return;
      try {
        this.currentSession = await getActiveSession(this.activeRequest.requestId, this.session.userId);
        this.startChargeSimulation();
        if (notifyAutoStart) {
          this.notify('系统已自动开始充电（分配后超时未插枪）', 'info');
        }
      } catch {
        // 会话尚未就绪，下一轮轮询重试
      }
    },
    computeChargeRate(session) {
      const target = Number(session.targetAmount) || 0;
      const start = this.parseDateTime(session.startTime);
      const end = this.parseDateTime(session.estimatedEndTime);
      if (start && end && end > start && target > 0) {
        const seconds = (end.getTime() - start.getTime()) / 1000;
        if (seconds > 0) return target / seconds;
      }
      const powerKw = session.mode === 'SLOW' ? 7 : 60;
      return powerKw / 3600;
    },
    syncSimulatedFromElapsed() {
      if (!this.currentSession) return;
      const start = this.parseDateTime(this.currentSession.startTime);
      if (!start) return;
      const elapsedSec = Math.max(0, (Date.now() - start.getTime()) / 1000);
      const target = Number(this.currentSession.targetAmount) || 0;
      const simulated = Math.min(target, elapsedSec * this.chargeRateKwhPerSec);
      this.simulatedCharged = Math.round(simulated * 100) / 100;
    },
    startChargeSimulation({ resume = false } = {}) {
      if (!this.currentSession) return;
      this.stopChargeSimulation();
      this.chargeRateKwhPerSec = this.computeChargeRate(this.currentSession);
      if (!resume) {
        this.simulatedCharged = 0;
      }
      this.syncSimulatedFromElapsed();
      const target = Number(this.currentSession.targetAmount) || 0;
      if (this.simulatedCharged >= target) {
        this.simulatedCharged = target;
        this.autoCompleteCharging();
        return;
      }
      this.persistChargeState();
      this.chargeUiTick += 1;
      this.chargeTimerId = window.setInterval(() => this.tickCharge(), 250);
    },
    stopChargeSimulation() {
      if (this.chargeTimerId) {
        clearInterval(this.chargeTimerId);
        this.chargeTimerId = null;
      }
    },
    tickCharge() {
      if (!this.currentSession || this.loading.stop || this.autoStopping) return;
      this.chargeUiTick += 1;
      this.syncSimulatedFromElapsed();
      const target = Number(this.currentSession.targetAmount) || 0;
      if (this.simulatedCharged >= target) {
        this.simulatedCharged = target;
        this.autoCompleteCharging();
        return;
      }
      this.persistChargeState();
    },
    async restoreChargeStateIfNeeded() {
      if (this.currentSession) return;
      let saved = null;
      try {
        const raw = sessionStorage.getItem(this.chargeStateStorageKey());
        saved = raw ? JSON.parse(raw) : null;
      } catch {
        saved = null;
      }
      if (!saved?.sessionId) return;
      const ongoing = this.orders.find(
        (o) => o.requestId === saved.requestId && o.status === 'CHARGING'
      );
      if (!ongoing) {
        this.clearPersistedChargeState();
        return;
      }
      this.activeRequest = ongoing;
      if (ongoing.status === 'CHARGING' && !saved?.sessionId) {
        await this.loadActiveSessionAndStartSimulation({ notifyAutoStart: false });
        return;
      }
      if (ongoing.assignedPileId && !this.dispatchResult) {
        this.dispatchResult = { pileCode: `#${ongoing.assignedPileId}` };
      }
      this.currentSession = {
        sessionId: saved.sessionId,
        requestId: saved.requestId,
        pileId: saved.pileId,
        mode: saved.mode || ongoing.mode,
        targetAmount: saved.targetAmount ?? ongoing.targetAmount,
        startTime: saved.startTime,
        estimatedEndTime: saved.estimatedEndTime
      };
      this.startChargeSimulation({ resume: true });
    },
    async handleEarlyStop() {
      const charged = this.simulatedCharged;
      const message =
        charged <= 0
          ? '当前几乎未充电，确定要提前结束吗？'
          : `当前已充 ${charged.toFixed(1)} kWh，确定提前结束充电吗？`;
      if (!window.confirm(message)) return;
      await this.handleStopSession(charged, { manual: true });
    },
    async autoCompleteCharging() {
      if (this.autoStopping || this.loading.stop || !this.currentSession) return;
      this.autoStopping = true;
      this.stopChargeSimulation();
      const target = Number(this.currentSession.targetAmount) || this.simulatedCharged;
      await this.handleStopSession(target, { manual: false });
    },
    logout() {
      this.stopChargeSimulation();
      this.stopAssignmentPolling();
      this.clearPersistedChargeState();
      this.session = { userId: null, username: '', token: '', role: '' };
      clearSession();
      this.vehicles = [];
      this.selectedVehicleId = null;
      this.activeRequest = null;
      this.currentSession = null;
      this.simulatedCharged = 0;
      this.chargeRateKwhPerSec = 0;
      this.autoStopping = false;
      this.bills = [];
      this.orders = [];
      this.pageTab = 'charge';
      this.notify('已退出登录', 'info');
    },
    async handleRegister() {
      this.loading.register = true;
      try {
        await registerUser(this.registerForm);
        this.authTab = 'login';
        this.loginForm.username = this.registerForm.username;
        this.loginForm.password = this.registerForm.password;
        this.notify('注册成功，请登录', 'success');
      } catch (err) {
        this.notify(err.message || '注册失败', 'error');
      } finally {
        this.loading.register = false;
      }
    },
    async handleLogin() {
      this.loading.login = true;
      try {
        const res = await loginUser(this.loginForm);
        this.session = { userId: res.userId, username: res.username, token: res.token, role: res.role };
        this.saveSession();
        if (res.role === 'ADMIN') {
          this.notify('管理员账号已跳转到后台', 'info');
          this.$router.push('/admin');
          return;
        }
        this.notify(`欢迎回来，${res.username}`, 'success');
        await this.loadVehicles();
        await this.loadBills();
        await this.loadOrders().then(() => this.restoreChargeStateIfNeeded());
        await this.loadQueue();
      } catch (err) {
        this.notify(err.message || '登录失败', 'error');
      } finally {
        this.loading.login = false;
      }
    },
    async loadVehicles() {
      if (!this.session.userId) return;
      this.loading.vehicles = true;
      try {
        this.vehicles = await getUserVehicles(this.session.userId);
        if (this.vehicles.length && !this.selectedVehicleId) {
          this.selectedVehicleId = this.vehicles[0].vehicleId;
        }
      } catch (err) {
        this.notify(err.message || '加载车辆失败', 'error');
      } finally {
        this.loading.vehicles = false;
      }
    },
    async handleBindVehicle() {
      this.loading.bind = true;
      try {
        const vehicle = await bindVehicle({ userId: this.session.userId, ...this.vehicleForm });
        this.selectedVehicleId = vehicle.vehicleId;
        await this.loadVehicles();
        this.notify(`已绑定 ${vehicle.plateNumber}`, 'success');
      } catch (err) {
        this.notify(err.message || '绑车失败', 'error');
      } finally {
        this.loading.bind = false;
      }
    },
    async handleUnbindVehicle(vehicleId, plateNumber) {
      if (!confirm(`确定要解绑车辆 ${plateNumber} 吗？`)) {
        return;
      }
      try {
        await unbindVehicle(vehicleId, this.session.userId);
        await this.loadVehicles();
        if (this.selectedVehicleId === vehicleId) {
          this.selectedVehicleId = this.vehicles.length > 0 ? this.vehicles[0].vehicleId : null;
        }
        this.notify(`已解绑 ${plateNumber}`, 'success');
      } catch (err) {
        this.notify(err.message || '解绑失败', 'error');
      }
    },
    async handleSubmitRequest() {
      if (!this.selectedVehicleId) {
        this.notify('请先选择或绑定车辆', 'error');
        this.pageTab = 'vehicles';
        return;
      }
      this.loading.request = true;
      try {
        this.activeRequest = await submitChargingRequest({
          userId: this.session.userId,
          vehicleId: this.selectedVehicleId,
          mode: this.requestForm.mode,
          targetAmount: this.requestForm.targetAmount
        });
        this.dispatchResult = null;
        this.currentSession = null;
        this.simulatedCharged = 0;
        this.chargeRateKwhPerSec = 0;
        this.autoStopping = false;
        this.stopChargeSimulation();
        this.clearPersistedChargeState();
        this.stopResult = null;
        await this.loadQueue();
        await this.loadOrders();
        let autoAssigned = false;
        try {
          this.dispatchResult = await dispatchScheduler({ mode: this.requestForm.mode });
          autoAssigned = true;
          await this.refreshActiveRequest();
          await this.loadQueue();
          await this.loadOrders();
        } catch {
          autoAssigned = false;
        }
        this.notify(
          autoAssigned
            ? `预约成功，系统已自动分配 ${this.dispatchResult.pileCode} 号充电桩`
            : '预约已提交，当前暂无空闲桩位，已进入等待队列',
          'success'
        );
      } catch (err) {
        this.notify(err.message || '提交失败', 'error');
      } finally {
        this.loading.request = false;
      }
    },
    async refreshActiveRequest({ silent = false } = {}) {
      if (!this.activeRequest?.requestId) return;
      if (!silent) this.loading.refreshRequest = true;
      try {
        this.activeRequest = await getChargingRequest(this.activeRequest.requestId, this.session.userId);
        if (this.activeRequest.assignedPileId && !this.dispatchResult) {
          this.dispatchResult = { pileCode: `#${this.activeRequest.assignedPileId}` };
        }
        if (this.activeRequest.status === 'CHARGING' && !this.currentSession) {
          await this.loadActiveSessionAndStartSimulation({ notifyAutoStart: !silent });
        }
        await this.loadOrders();
      } catch (err) {
        if (!silent) this.notify(err.message || '刷新失败', 'error');
      } finally {
        if (!silent) this.loading.refreshRequest = false;
      }
    },
    async handleCancelRequest() {
      if (!window.confirm('确定要取消当前充电订单吗？')) return;
      this.loading.cancel = true;
      try {
        this.activeRequest = await cancelChargingRequest(this.activeRequest.requestId, this.session.userId);
        this.dispatchResult = null;
        await this.loadOrders();
        this.notify('订单已取消', 'success');
      } catch (err) {
        this.notify(err.message || '取消失败', 'error');
      } finally {
        this.loading.cancel = false;
      }
    },
    async handleModifyRequest() {
      const input = window.prompt('新的目标电量 (kWh)', String(this.activeRequest.targetAmount));
      if (input === null) return;
      const targetAmount = Number(input);
      if (!Number.isFinite(targetAmount) || targetAmount <= 0) {
        this.notify('请输入有效电量', 'error');
        return;
      }
      this.loading.modify = true;
      try {
        this.activeRequest = await modifyChargingRequest(
          this.activeRequest.requestId,
          this.session.userId,
          { targetAmount }
        );
        this.requestForm.targetAmount = targetAmount;
        this.notify('目标电量已更新', 'success');
      } catch (err) {
        this.notify(err.message || '修改失败', 'error');
      } finally {
        this.loading.modify = false;
      }
    },
    async loadQueue() {
      this.loading.queue = true;
      try {
        this.queueInfo = await getSchedulerQueue(this.requestForm.mode);
      } catch (err) {
        this.notify(err.message || '加载队列失败', 'error');
      } finally {
        this.loading.queue = false;
      }
    },
    async handleDispatch() {
      this.loading.dispatch = true;
      try {
        this.dispatchResult = await dispatchScheduler({ mode: this.requestForm.mode });
        if (this.activeRequest) await this.refreshActiveRequest();
        await this.loadQueue();
        await this.loadOrders();
        this.notify(`已分配 ${this.dispatchResult.pileCode} 号充电桩`, 'success');
      } catch (err) {
        this.notify(err.message || '暂时无法分配，请稍后再试', 'error');
      } finally {
        this.loading.dispatch = false;
      }
    },
    async handleStartSession() {
      this.loading.start = true;
      try {
        this.currentSession = await startSession({
          requestId: this.activeRequest.requestId,
          pileId: this.activeRequest.assignedPileId
        });
        await this.refreshActiveRequest();
        this.startChargeSimulation();
        this.notify('充电已开始，充满后将自动结束', 'success');
      } catch (err) {
        this.notify(err.message || '启动失败，请确认已插枪', 'error');
      } finally {
        this.loading.start = false;
      }
    },
    async handleStopSession(chargedAmount, { manual = true } = {}) {
      if (this.loading.stop || !this.currentSession) return;
      this.loading.stop = true;
      this.stopChargeSimulation();
      const target = Number(this.currentSession.targetAmount) || 0;
      let amount = Number(chargedAmount ?? this.simulatedCharged);
      if (!Number.isFinite(amount) || amount < 0) amount = 0;
      amount = Math.min(target, Math.round(amount * 10) / 10);
      try {
        this.stopResult = await stopSession(this.currentSession.sessionId, {
          chargedAmount: amount
        });
        this.currentSession = null;
        this.simulatedCharged = 0;
        this.chargeRateKwhPerSec = 0;
        this.clearPersistedChargeState();
        await this.refreshActiveRequest();
        await this.loadOrders();
        await this.loadBills();
        this.pageTab = 'bills';
        const prefix = manual ? '充电已结束' : '已充满，充电自动结束';
        this.notify(`${prefix}，请支付 ¥${this.stopResult.bill?.totalFee}`, 'success');
      } catch (err) {
        this.notify(err.message || '结束充电失败', 'error');
      } finally {
        this.loading.stop = false;
        this.autoStopping = false;
      }
    },
    async loadBills() {
      if (!this.session.userId) return;
      this.loading.bills = true;
      try {
        this.bills = await getUserBills(this.session.userId);
      } catch (err) {
        this.notify(err.message || '加载账单失败', 'error');
      } finally {
        this.loading.bills = false;
      }
    },
    async loadOrders() {
      if (!this.session.userId) return;
      this.loading.orders = true;
      try {
        this.orders = await getUserChargingRequests(this.session.userId);
        this.syncActiveFromOrders();
      } catch (err) {
        this.notify(err.message || '加载订单失败', 'error');
      } finally {
        this.loading.orders = false;
      }
    },
    async handlePayBill(billId) {
      this.loading.pay = billId;
      try {
        const result = await payBill(billId, { paymentMethod: 'WECHAT' });
        await this.loadBills();
        this.notify(`支付成功 ¥${result.amount}`, 'success');
      } catch (err) {
        this.notify(err.message || '支付失败', 'error');
      } finally {
        this.loading.pay = null;
      }
    }
  }
};
</script>

<style scoped>
.user-portal {
  min-height: 100vh;
  padding: 40px 24px 64px;
  color: #172033;
  background:
    radial-gradient(circle at top left, rgba(163, 220, 180, 0.35), transparent 28%),
    linear-gradient(180deg, #f5fbf7 0%, #eef3fb 100%);
}

.hero,
.panel,
.highlights,
.page-tabs,
.step-track,
.banner,
.layout-main,
.auth-panel {
  max-width: 1120px;
  margin-left: auto;
  margin-right: auto;
}

.eyebrow {
  margin: 0 0 10px;
  font-size: 12px;
  letter-spacing: 0.22em;
  text-transform: uppercase;
  color: #1e6b3a;
}

.hero h1 {
  margin: 0;
  font-size: clamp(28px, 4vw, 44px);
  line-height: 1.15;
}

.hero.compact {
  margin-bottom: 8px;
}

.hero-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
}

.subtitle {
  margin: 14px 0 0;
  max-width: 640px;
  line-height: 1.7;
  color: #4a5670;
}

.multi-user-hint {
  margin: 10px 0 0;
  max-width: 640px;
  font-size: 13px;
  line-height: 1.6;
  color: #6b7a94;
}

.highlights {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 16px;
  margin-top: 28px;
}

.highlight-card {
  padding: 22px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.82);
  box-shadow: 0 12px 40px rgba(31, 44, 71, 0.06);
}

.highlight-icon {
  font-size: 28px;
}

.highlight-card h3 {
  margin: 10px 0 6px;
  font-size: 17px;
}

.highlight-card p {
  margin: 0;
  font-size: 14px;
  line-height: 1.6;
  color: #69748b;
}

.panel {
  margin-top: 24px;
  padding: 24px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 16px 48px rgba(31, 44, 71, 0.07);
}

.auth-panel {
  max-width: 440px;
}

.auth-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 20px;
  padding: 4px;
  border-radius: 12px;
  background: #f0f4f8;
}

.auth-tabs button {
  flex: 1;
  padding: 10px;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: #69748b;
  font-size: 14px;
  cursor: pointer;
}

.auth-tabs button.active {
  background: #fff;
  color: #172033;
  font-weight: 600;
  box-shadow: 0 2px 8px rgba(31, 44, 71, 0.08);
}

.auth-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.auth-form label span,
.field-label,
.bind-form label span {
  display: block;
  margin-bottom: 6px;
  font-size: 13px;
  color: #69748b;
}

.auth-form input,
.bind-form input,
.field-block input,
.stop-field input {
  width: 100%;
  padding: 12px 14px;
  border: 1px solid rgba(23, 32, 51, 0.12);
  border-radius: 12px;
  font-size: 15px;
}

.progress-caption .countdown {
  color: #1f5fbf;
  font-weight: 600;
}

.charging-live {
  margin: 0 0 12px;
  font-size: 14px;
  color: #1f5fbf;
  font-weight: 600;
}

.charging-live .muted {
  font-weight: 400;
  color: #6b7a94;
}

.stop-field input {
  width: 100%;
  padding: 12px 14px;
  border: 1px solid rgba(23, 32, 51, 0.12);
  border-radius: 12px;
  font-size: 15px;
}

.page-tabs {
  display: flex;
  gap: 8px;
  margin-top: 20px;
  flex-wrap: wrap;
}

.page-tabs button {
  position: relative;
  padding: 10px 18px;
  border: none;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.7);
  color: #69748b;
  font-size: 14px;
  cursor: pointer;
}

.page-tabs button.active {
  background: #172033;
  color: #fff;
}

.tab-badge {
  margin-left: 6px;
  padding: 1px 7px;
  border-radius: 999px;
  font-size: 11px;
  background: #bc3b2f;
  color: #fff;
}

.step-track {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 20px;
  margin: 20px auto 0;
  padding: 0;
  list-style: none;
}

.step-track li {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #69748b;
}

.step-num {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  font-size: 12px;
  background: #edf1f7;
}

.step-track li.done .step-num {
  background: rgba(30, 107, 58, 0.15);
  color: #1e6b3a;
}

.step-track li.current .step-text {
  color: #172033;
  font-weight: 600;
}

.step-track li.current .step-num {
  background: #172033;
  color: #fff;
}

.banner {
  margin-top: 16px;
  padding: 12px 16px;
  border-radius: 12px;
  font-size: 14px;
}

.banner.success { background: rgba(30, 107, 58, 0.1); color: #1e6b3a; }
.banner.error { background: rgba(188, 59, 47, 0.1); color: #bc3b2f; }
.banner.info { background: rgba(45, 93, 183, 0.1); color: #2d5db7; }

.inline-tip {
  margin: 0;
}

.assignment-timeout {
  margin: 0;
  padding: 10px 12px;
  border-radius: 8px;
  background: rgba(184, 134, 11, 0.12);
  color: #8a6a00;
  font-size: 0.92rem;
}

.layout-main {
  display: grid;
  grid-template-columns: 1fr 320px;
  gap: 20px;
  margin-top: 20px;
  align-items: start;
}

.layout-primary .panel {
  margin-top: 0;
}

.layout-side .panel {
  margin-top: 0;
}

.layout-side .panel + .panel {
  margin-top: 16px;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.panel-head h2,
.panel h2 {
  margin: 0;
  font-size: 20px;
}

.panel h3 {
  margin: 0 0 12px;
  font-size: 16px;
}

.order-id {
  font-size: 13px;
  color: #69748b;
}

.order-status {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  margin-bottom: 16px;
}

.status-pill {
  padding: 6px 14px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 600;
  background: #edf1f7;
  color: #4a5670;
}

.status-pill.waiting { background: #fff8e6; color: #b75d2d; }
.status-pill.assigned { background: #e8f4fd; color: #2d5db7; }
.status-pill.charging { background: #e6f4ea; color: #1e6b3a; }
.status-pill.unpaid { background: #fff8e6; color: #b75d2d; }
.status-pill.paid { background: #e6f4ea; color: #1e6b3a; }
.status-pill.cancelled { background: #f0f0f0; color: #69748b; }
.status-pill.small { font-size: 12px; padding: 4px 10px; }

.time-cell {
  font-size: 13px;
  color: #69748b;
  white-space: nowrap;
}

.pile-tag {
  font-size: 14px;
  color: #1e6b3a;
  font-weight: 600;
}

.order-progress {
  margin-bottom: 20px;
}

.progress-bar {
  height: 8px;
  border-radius: 999px;
  background: #edf1f7;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, #1e6b3a, #39c179);
  transition: width 0.25s linear;
}

.progress-caption {
  margin: 8px 0 0;
  font-size: 14px;
  color: #69748b;
}

.order-details {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin: 0 0 20px;
}

.order-details div {
  padding: 12px;
  border-radius: 12px;
  background: #f8fafc;
}

.order-details dt {
  margin: 0 0 4px;
  font-size: 12px;
  color: #69748b;
}

.order-details dd {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.order-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: flex-end;
}

.stop-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 140px;
}

.notice {
  margin-top: 16px;
  padding: 12px 14px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.6;
}

.notice.success {
  background: rgba(30, 107, 58, 0.08);
  color: #1e6b3a;
}

.field-label {
  margin: 16px 0 10px;
  font-size: 13px;
  color: #69748b;
}

.field-block {
  display: block;
  margin-top: 16px;
}

.vehicle-cards {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.vehicle-cards.wide {
  margin-bottom: 24px;
}

.vehicle-card {
  min-width: 140px;
  padding: 14px 16px;
  border: 2px solid rgba(23, 32, 51, 0.08);
  border-radius: 14px;
  background: #fff;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.2s;
}

.vehicle-card.static {
  cursor: pointer;
}

.vehicle-card.active {
  border-color: #1e6b3a;
  background: rgba(30, 107, 58, 0.04);
}

.vehicle-card .plate {
  display: block;
  font-size: 16px;
  font-weight: 700;
}

.vehicle-card .meta,
.vehicle-card .cap {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: #69748b;
}

.vehicle-card.static {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.vehicle-info {
  flex: 1;
  cursor: pointer;
}

.btn-delete {
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 8px;
  background: rgba(239, 68, 68, 0.08);
  color: #ef4444;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.btn-delete:hover {
  background: rgba(239, 68, 68, 0.15);
  transform: scale(1.1);
}

.mode-cards {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.mode-card {
  padding: 16px;
  border: 2px solid rgba(23, 32, 51, 0.08);
  border-radius: 14px;
  background: #fff;
  text-align: left;
  cursor: pointer;
}

.mode-card.active {
  border-color: #1e6b3a;
  background: rgba(30, 107, 58, 0.04);
}

.mode-card strong {
  display: block;
  margin-bottom: 4px;
  font-size: 16px;
}

.mode-card span {
  font-size: 12px;
  color: #69748b;
}

.queue-summary {
  margin: 0 0 12px;
  font-size: 14px;
  color: #69748b;
}

.tips-list {
  margin: 0;
  padding-left: 18px;
  font-size: 14px;
  line-height: 1.7;
  color: #69748b;
}

.tips-list li {
  margin-bottom: 8px;
}

.bind-section {
  margin-top: 24px;
  padding-top: 24px;
  border-top: 1px solid rgba(23, 32, 51, 0.08);
}

.bind-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 14px;
  margin-bottom: 16px;
}

.table-wrap {
  overflow-x: auto;
  margin-top: 8px;
}

.bill-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}

.bill-table th,
.bill-table td {
  padding: 12px 10px;
  text-align: left;
  border-bottom: 1px solid rgba(23, 32, 51, 0.08);
}

.bill-table th {
  font-size: 12px;
  font-weight: 600;
  color: #69748b;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.empty-block {
  padding: 24px;
  text-align: center;
  border-radius: 14px;
  background: #f8fafc;
}

.empty-block p {
  margin: 0 0 12px;
}

.muted {
  color: #69748b;
  font-size: 14px;
}

.btn-primary,
.btn-secondary,
.btn-outline,
.btn-pay {
  padding: 11px 18px;
  border: none;
  border-radius: 999px;
  font-size: 14px;
  cursor: pointer;
}

.btn-primary {
  background: #172033;
  color: #fff;
}

.btn-primary.wide {
  width: 100%;
  margin-top: 20px;
  padding: 14px;
  font-size: 15px;
}

.btn-secondary {
  background: #fff;
  color: #172033;
  border: 1px solid rgba(23, 32, 51, 0.15);
}

.btn-outline {
  flex-shrink: 0;
  background: transparent;
  color: #69748b;
  border: 1px solid rgba(23, 32, 51, 0.15);
}

.btn-text {
  padding: 8px 12px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #2d5db7;
  font-size: 13px;
  cursor: pointer;
}

.btn-text.danger {
  color: #bc3b2f;
}

.btn-pay {
  padding: 6px 14px;
  background: #07c160;
  color: #fff;
  font-size: 13px;
}

button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

@media (max-width: 900px) {
  .layout-main {
    grid-template-columns: 1fr;
  }

  .order-details {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .user-portal {
    padding: 28px 16px 48px;
  }

  .hero-row {
    flex-direction: column;
  }

  .mode-cards {
    grid-template-columns: 1fr;
  }
}
</style>
