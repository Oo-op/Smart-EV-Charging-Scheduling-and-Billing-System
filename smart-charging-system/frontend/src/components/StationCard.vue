<template>
  <div class="station-card">
    <h3>{{ station.name }}</h3>
    <p>{{ station.address }}</p>
    <span class="status">{{ station.status }}</span>
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
  padding: 16px;
  border: 1px solid #eee;
  border-radius: 8px;
  margin-bottom: 12px;
}
.status {
  display: inline-block;
  padding: 4px 12px;
  background: #e3f2fd;
  color: #1976d2;
  border-radius: 4px;
}
</style>
