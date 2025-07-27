<script setup lang="ts">
import {ref, defineProps, onMounted, watch} from 'vue';
import {type Grid, GridMode, ItemKind, type ServerConfig} from "../services/BlindspotService.ts";
import {BlindspotAPI, BlindspotSingletonService} from "../services/BlindspotService";
import {BLINDSPOT_ENDPOINT} from 'astro:env/client';
import GridCell from "./GridCell.vue";
import Assistant from "./Assistant.vue";

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
  live.value = await BlindspotAPI.grid({
      query: gridConfig.query,
      showMovies: gridConfig.showMovies,
      showShows: gridConfig.showShows
    }
  );
}

onMounted(() => {
  // fetchData(gridConfig.value)
})

watch(gridConfig, async (newConfig) => {
  await fetchData(newConfig);
}, {deep: true}); // Use deep: true to watch nested properties

// Assistant-related code here.
const showAssistant = ref(false);

// Function to toggle Assistant visibility
function toggleAssistant() {
  showAssistant.value = !showAssistant.value;
}

</script>
<template>

  <div v-show="showAssistant" class="assistant-container">
    <!-- Use transition for smooth effect -->
    <transition name="fade">
      <!--
      <Assistant #assistant :grid="live" :config="config" :gridConfig="gridConfig" @closed="showAssistant = false"/>
      -->

      <!-- <Assistant v-if="showAssistant" @closed="showAssistant = false"/> -->
      <Assistant
        :config="config" :gridConfig="gridConfig"
        :visible="showAssistant" v-show="showAssistant" @closed="showAssistant = false"/>


    </transition>
  </div>


  <div>
    <div class="grid-tools">
      <div class="tool-block">
        <label>Search
          <input
            autofocus
            style="width: 230px; padding-left: 10px; padding-right: 10px;
            border-radius: 3px; border: 1px solid #818181;"
            type="search" v-model="gridConfig.query" placeholder="Title of movie or show,..."/>
        </label>
      </div>

      <div class="tool-block kind">
        <label>
          Kind
          <label><input type="checkbox" v-model="gridConfig.showMovies"/>
            Movies
            <img src="/film.png" alt="Movie" class="icon"/>

          </label>
          <label><input type="checkbox" v-model="gridConfig.showShows"/>Shows
            <img src="/show.png" alt="Show" class="icon"/>
          </label>
        </label>
      </div>

      <div class="tool-block">
        <label>
          Show stats
          <input type="checkbox" v-model="gridConfig.showIMDB"/>IMDB</label>
        <label><input type="checkbox" v-model="gridConfig.showTMDB"/>TMDB</label>
        <label><input type="checkbox" v-model="gridConfig.showTomato"/>Rotten Tomatoes</label>
      </div>

      <div class="tool-block">
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
      <div class="tool-block">
        <label><input type="checkbox" v-model="gridConfig.flippedLookup"/>Flip View</label>
      </div>
      <div class="tool-block ai">
        <a class="assistant-button" @click.prevent="toggleAssistant">
          <img src="/assistant.png" alt="Blindspot Logo" class="icon"/>
          AI Assistant
        </a>
      </div>
    </div>

    <div class="grid">
      <div class="data-space">
        <div class="grid-item no-border no-hover">
          <div class="col offset-col" v-for="_ in (
            1 + 2 + (gridConfig.showIMDB ? 2 : 0) + (gridConfig.showTMDB ? 2: 0) + (gridConfig.showTomato ? 1: 0)
            )">
          </div>
          <div class="col center fix-one" style="vertical-align: bottom"
               v-if="gridConfig.gridMode === GridMode.CountryPlatform || gridConfig.gridMode === GridMode.Country"
               v-for="c in config.countries">
            {{ c.value }}
          </div>
          <div class="col center fix-one" style="vertical-align: bottom"
               v-if="gridConfig.gridMode === GridMode.PlatformCountry || gridConfig.gridMode === GridMode.Platform"
               v-for="c in config.platforms">
            {{ c.name }}
          </div>
        </div>
      </div>

      <div class="data-space">
        <div class="grid-item no-hover">
          <div class="col center" style="width: 20px"><!-- icon --></div>
          <div class="col" style="width: 350px"><!-- title --></div>
          <div class="col fix-one">Year</div>
          <div class="col" v-if="gridConfig.showIMDB">IMDB V.</div>
          <div class="col" v-if="gridConfig.showIMDB">IMDB S.</div>
          <div class="col" v-if="gridConfig.showTMDB">TMDB S.</div>
          <div class="col" v-if="gridConfig.showTMDB">TMDB P.</div>
          <div class="col" v-if="gridConfig.showTomato">Tomato</div>
          <!-- TODO: add space here -->
        </div>
      </div>

      <div class="data-space">
        <div class="grid-item magic-row" v-for="row in live" :key="row.id">
          <!-- <div class="col">{{ item.id }}</div> -->
          <!-- <div class="col">{{ item.rank }}</div> -->

          <div class="col">
            <img v-if="row.kind === ItemKind.Movie" src="/film.png" alt="Movie" class="icon"/>
            <img v-if="row.kind === ItemKind.Show" src="/show.png" alt="Show" class="icon"/>
          </div>
          <div class="col">{{ row.title }}</div>
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
<style lang="scss">
.assistant-button {
  text-decoration: none;
  color: black;
  border: 1px solid #cdcdcd;
  background-color: #d8d8d8;
  padding: 2px;
  padding-left: 10px;
  padding-right: 15px;
  border-radius: 5px;
}

.grid {
  display: table;
  width: 100%;
  border-collapse: collapse;
}

.data-space {
  display: table-row-group;

  .magic-row:last-child {
    border-bottom: none !important;
  }
}

.grid-item {
  display: table-row;
  border-bottom: 1px solid #dddddd;
  vertical-align: middle;

  &.no-border {
    border-bottom: none !important;
  }


  img.icon {
    height: 18px;
    width: 18px;
    margin-right: 5px;
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
  vertical-align: middle;

  &.fix-one {
    width: 60px;
  }

  &.center {
    text-align: center;
  }

  &.right {
    text-align: right;
  }
}

.grid-tools {
  vertical-align: middle;
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  margin-top: 10px;
  padding: 10px;
  box-shadow: 0px 2px 3px 0px rgba(102, 102, 102, 0.49);
  background-color: #EEE;

  border-radius: 5px 5px 0px 0px;

  .tool-block {
    display: flex;
    flex-direction: row;
    margin-right: 10px;

    &.kind, &.ai {
      vertical-align: middle;

      img {
        height: 18px;
      }
    }

    label {
      margin-right: 10px;
    }
  }
}

.magic-row {
  &:hover {
    .tools {
      display: block;
    }
  }

  .tools {
    /* display: none; */
    vertical-align: middle;

    a {

    }
  }
}


</style>
