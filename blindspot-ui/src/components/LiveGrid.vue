<script setup lang="ts">
import {ref, defineProps, onMounted, watch} from 'vue';
import {type Grid, GridMode, type ServerConfig} from "../services/BlindspotService.ts";
import {BlindspotAPI, BlindspotSingletonService} from "../services/BlindspotService";
import {BLINDSPOT_ENDPOINT} from 'astro:env/client';
import GridCell from "./GridCell.vue";

BlindspotSingletonService.initService({endpoint: BLINDSPOT_ENDPOINT})

interface Props {
  initialGrid: Grid;
  initialConfig: ServerConfig
}

export interface GridConfig {
  showIMDB: boolean;
  showTMDB: boolean;
  showTomato: boolean;
  flippedLookup: boolean;
  gridMode: GridMode;
  showMovies: boolean;
  showShows: boolean;
  query: string;
}

const {initialGrid, initialConfig} = defineProps<Props>();
const config = ref<ServerConfig>(initialConfig);
const live = ref<Grid>(initialGrid);
const gridConfig = ref<GridConfig>({
  showIMDB: !true,
  showTMDB: !true,
  showTomato: !true,
  flippedLookup: false,
  gridMode: GridMode.PlatformCountry,
  showMovies: true,
  showShows: true,
  query: ""
})

async function fetchData(gridConfig: GridConfig) {
  live.value = await BlindspotAPI.grid(gridConfig.query, gridConfig.showMovies, gridConfig.showShows);
}

onMounted(() => {
  // fetchData(gridConfig.value)
})

watch(gridConfig, async (newConfig) => {
  await fetchData(newConfig);
}, {deep: true}); // Use deep: true to watch nested properties

</script>
<template>
  <div>
    <h1>Blindspot</h1>

    <div class="grid-tools">
      <div class="grid-block">
        <span>Stats</span>
        <label><input type="checkbox" v-model="gridConfig.showIMDB"/>IMDB</label>
        <label><input type="checkbox" v-model="gridConfig.showTMDB"/>TMDB</label>
        <label><input type="checkbox" v-model="gridConfig.showTomato"/>Tomato</label>
      </div>

      <div class="grid-block">
        <label>
          Kind
          <label><input type="checkbox" v-model="gridConfig.showMovies"/>Movies</label>
          <label><input type="checkbox" v-model="gridConfig.showShows"/>Shows</label>
        </label>
      </div>

      <div class="grid-block">
        <label>
          View
          <select v-model="gridConfig.gridMode">
            <option value="Platform">Platform</option>
            <option value="Country">Country</option>
            <option value="CountryPlatform">Country - Platform</option>
            <option value="PlatformCountry">Platform - Country</option>
          </select>
        </label>
      </div>
      <div class="grid-block">
        <span>Lookup</span>
        <label><input type="checkbox" v-model="gridConfig.flippedLookup"/>Flip</label>
      </div>

      <div class="grid-block">
        <label>Search
          <input type="search" v-model="gridConfig.query" placeholder="Title of movie or show,..."/>
        </label>
      </div>
    </div>

    <div class="grid">
      <div class="data-space">
        <div class="grid-item no-border no-hover">
          <div class="col offset-col" v-for="_ in (
            1 + 2 + (gridConfig.showIMDB ? 2 : 0) + (gridConfig.showTMDB ? 2: 0) + (gridConfig.showTomato ? 1: 0)
            )">
          </div>
          <div class="col center"
               v-if="gridConfig.gridMode === GridMode.CountryPlatform || gridConfig.gridMode === GridMode.Country"
               v-for="c in config.countries">
            {{ c.value }}
          </div>
          <div class="col center"
               v-if="gridConfig.gridMode === GridMode.PlatformCountry || gridConfig.gridMode === GridMode.Platform"
               v-for="c in config.platforms">
            {{ c.name }}
          </div>
        </div>
      </div>

      <div class="data-space">
        <div class="grid-item no-hover">
          <div class="col"></div>
          <div class="col">Kind</div>
          <div class="col">Year</div>
          <div class="col" v-if="gridConfig.showIMDB">IMDB V.</div>
          <div class="col" v-if="gridConfig.showIMDB">IMDB S.</div>
          <div class="col" v-if="gridConfig.showTMDB">TMDB S.</div>
          <div class="col" v-if="gridConfig.showTMDB">TMDB P.</div>
          <div class="col" v-if="gridConfig.showTomato">Tomato</div>
          <!-- TODO: add space here -->
        </div>
      </div>

      <div class="data-space">
        <div class="grid-item" v-for="row in live" :key="row.id">
          <!-- <div class="col">{{ item.id }}</div> -->
          <!-- <div class="col">{{ item.rank }}</div> -->
          <div class="col">{{ row.title }}</div>
          <div class="col">{{ row.kind }}</div>
          <div class="col">{{ row.releaseYear }}</div>
          <div class="col" v-if="gridConfig.showIMDB">{{ row.imdbVotes }}</div>
          <div class="col" v-if="gridConfig.showIMDB">{{ row.imdbScore }}</div>
          <div class="col" v-if="gridConfig.showTMDB">{{ row.tmdbScore }}</div>
          <div class="col" v-if="gridConfig.showTMDB">{{ row.tmdbPopularity }}</div>
          <div class="col" v-if="gridConfig.showTomato">{{ row.tomatoMeter }}</div>
          <GridCell :row="row" :gridMode="gridConfig.gridMode"
                    :flippedLookup="gridConfig.flippedLookup"
                    :serverConfig="config"/>
        </div>
      </div>
    </div>
  </div>
</template>
<style>
.grid {
  display: table;
  width: 100%;
  border-collapse: collapse;
}

.data-space {
  display: table-row-group;
}

.grid-item {
  display: table-row;
  border-bottom: 1px solid #dddddd;

  &.no-border{
    border-bottom: none !important;
  }
}

.grid-item:hover {
  background-color: #f5f5f5;
}

.grid-item.no-hover:hover {
  background-color: transparent !important;
}

.col {
  display: table-cell;
  padding: 6px;
  text-align: left;

  &.center {
    text-align: center;
  }
}
</style>
