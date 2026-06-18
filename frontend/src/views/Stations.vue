<template>
  <div class="stations">
    <div class="header">
      <div>
        <p class="eyebrow">Station Overview</p>
        <h2>充电站列表</h2>
      </div>
      <button :disabled="loading" @click="loadStations">
        {{ loading ? '刷新中...' : '刷新列表' }}
      </button>
    </div>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-else-if="!stations.length && !loading" class="empty">
      暂无充电站数据，系统正在准备中...
    </p>

    <div v-else class="list">
      <StationCard v-for="station in stations" :key="station.id" :station="station" />
    </div>
  </div>
</template>

<script>
import { getStations } from '../api';
import StationCard from '../components/StationCard.vue';

export default {
  name: 'Stations',
  components: { StationCard },
  data() {
    return {
      stations: [],
      loading: false,
      error: ''
    };
  },
  mounted() {
    this.loadStations();
  },
  methods: {
    async loadStations() {
      this.loading = true;
      this.error = '';
      try {
        this.stations = await getStations();
      } catch (error) {
        this.error = error.message || '加载充电站失败';
      } finally {
        this.loading = false;
      }
    }
  }
};
</script>

<style scoped>
.stations {
  min-height: 100vh;
  padding: 40px 24px 64px;
  background: linear-gradient(180deg, #fbfcfe 0%, #eef5ff 100%);
}
.header,
.list,
.empty,
.error {
  max-width: 960px;
  margin: 0 auto;
}
.header {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 16px;
}
.eyebrow {
  margin: 0 0 8px;
  color: #a25731;
  font-size: 12px;
  letter-spacing: 0.2em;
  text-transform: uppercase;
}
h2 {
  margin: 0;
  font-size: 36px;
  color: #18243a;
}
button {
  padding: 10px 16px;
  border: none;
  border-radius: 999px;
  background: #18243a;
  color: #fff;
  cursor: pointer;
}
button:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}
.list {
  margin-top: 24px;
}
.empty,
.error {
  margin-top: 24px;
  color: #5b6780;
}
.error {
  color: #bc3b2f;
}
@media (max-width: 720px) {
  .stations {
    padding: 32px 16px 48px;
  }
  .header {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
