<template>
  <div class="station-card">
    <div class="top">
      <div>
        <p class="label">站点名称</p>
        <h3>{{ station.name }}</h3>
      </div>
      <span class="status">{{ station.status }}</span>
    </div>
    <p class="address">{{ station.address }}</p>
    <p class="meta">站点 ID: {{ station.id ?? '-' }}</p>
  </div>
</template>

<script>
export default {
  name: 'StationCard',
  props: {
    station: {
      type: Object,
      required: true
    }
  }
};
</script>

<style scoped>
.station-card {
  padding: 20px;
  border: 1px solid rgba(24, 36, 58, 0.08);
  border-radius: 20px;
  margin-bottom: 12px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 12px 40px rgba(31, 44, 71, 0.08);
}
.top {
  display: flex;
  align-items: start;
  justify-content: space-between;
  gap: 16px;
}
.label,
.meta {
  margin: 0;
  color: #657189;
  font-size: 13px;
}
h3 {
  margin: 6px 0 0;
  color: #18243a;
}
.address {
  margin: 16px 0 10px;
  color: #36445f;
  line-height: 1.6;
}
.status {
  display: inline-block;
  padding: 6px 12px;
  background: #e6f3ea;
  color: #216c3e;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
}
</style>
