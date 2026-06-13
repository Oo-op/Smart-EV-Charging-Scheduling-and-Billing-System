<template>
  <div class="stations">
    <h2>充电站列表</h2>
    <StationCard v-for="station in stations" :key="station.id" :station="station" />
  </div>
</template>

<script>
import StationCard from '../components/StationCard.vue';

export default {
  name: 'Stations',
  components: { StationCard },
  data() {
    return {
      stations: []
    };
  }
};
</script>

<style scoped>
.stations {
  padding: 40px;
}
</style>
