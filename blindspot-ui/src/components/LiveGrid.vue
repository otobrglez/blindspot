<script setup lang="ts">
import {ref, defineProps, onMounted} from 'vue';
import type {Grid} from "../services/BlindspotService.ts";
import {BlindspotAPI, BlindspotSingletonService} from "../services/BlindspotService";
import {BLINDSPOT_ENDPOINT} from 'astro:env/client';

BlindspotSingletonService.initService({endpoint: BLINDSPOT_ENDPOINT})

interface Props {
  initialData: Grid;
}

const {initialData} = defineProps<Props>();
const live = ref(initialData);

async function fetchData() {
  live.value = await BlindspotAPI.grid()
}

onMounted(() => {
  // fetchData()
})
</script>
<template>
  <div>
    <h1>Blindspot</h1>

    <div class="grid">
      <div class="grid-item" v-for="item in live" :key="item.id">
        <!-- <div class="col">{{ item.id }}</div> -->
        <!-- <div class="col">{{ item.rank }}</div> -->
        <div class="col">{{ item.title }}</div>
        <div class="col">{{ item.kind }}</div>
        <div class="col">{{ item.releaseYear }}</div>
        <div class="col">{{ item.imdbVotes }}</div>
        <div class="col">{{ item.imdbScore }}</div>
        <div class="col">{{ item.tmdbScore }}</div>
        <div class="col">{{ item.tmdbPopularity }}</div>
        <div class="col">{{ item.tomatoMeter }}</div>
      </div>
    </div>
  </div>
</template>
<style scoped>
.grid {
  display: table;
  width: 100%;
  border-collapse: collapse;
}

.grid-item {
  display: table-row;
  border-bottom: 1px solid #ddd;
}

.grid-item:hover {
  background-color: #f5f5f5;
}

.col {
  display: table-cell;
  padding: 6px;
  text-align: left;
}
</style>
