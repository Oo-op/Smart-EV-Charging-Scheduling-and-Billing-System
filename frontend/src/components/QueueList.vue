<template>
  <ul v-if="items.length" class="queue-list">
    <li v-for="item in items" :key="item.requestId">
      <div class="row-top">
        <strong>{{ item.plateNumber || `请求 #${item.requestId}` }}</strong>
        <span class="badge">{{ getStatusDesc('chargingRequestStatus', item.status) }}</span>
      </div>
      <div class="row-meta">
        <span>目标 {{ item.targetAmount }} kWh</span>
        <span>排队号 {{ item.queueNumber ?? '—' }}</span>
      </div>
    </li>
  </ul>
  <p v-else class="empty">当前无等待车辆</p>
</template>

<script>
import { getStatusDesc } from '../api/enums';

export default {
  name: 'QueueList',
  props: {
    items: {
      type: Array,
      default: () => []
    }
  },
  methods: {
    getStatusDesc
  }
};
</script>

<style scoped>
.queue-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.queue-list li {
  padding: 14px 0;
  border-bottom: 1px solid rgba(23, 32, 51, 0.08);
}

.row-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.row-meta {
  display: flex;
  gap: 16px;
  margin-top: 6px;
  font-size: 13px;
  color: #69748b;
}

.badge {
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: normal;
  background: #edf1f7;
  color: #4a5670;
}

.empty {
  margin: 0;
  color: #69748b;
}
</style>
