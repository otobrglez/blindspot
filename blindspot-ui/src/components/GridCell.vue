<script lang="ts" setup>
import {ref, defineProps, onMounted, watch} from 'vue';
import {GridMode, type Platform, type Row, type ServerConfig} from "../services/BlindspotService.ts";

interface Props {
  row: Row,
  gridMode: GridMode,
  serverConfig: ServerConfig,
  flippedLookup: boolean
}

const {row, gridMode, serverConfig, flippedLookup} = defineProps<Props>();

function isAvailable(options: {
  country?: string,
  platform?: Platform,
}) {
  if (options.country) {
    return renderSymbol(row.providerTags.values().some(tag => tag.startsWith(options.country!)))
  } else if (options.platform) {
    const packages = options.platform.value
    return renderSymbol(row.providerTags.values().some(providerTag =>
      packages.values().some(packageTag => providerTag.endsWith(packageTag))
    ))
  } else return ""
}

function renderSymbol(ok: boolean) {
  if (!flippedLookup) {
    return ok ? "⚪️" : ""
  } else {
    return ok ? " " : "🟡"
  }
}

</script>
<template>
  <div class="col center"
       v-if="gridMode === GridMode.CountryPlatform || gridMode === GridMode.Country"
       v-for="c in serverConfig.countries">
    {{ isAvailable({country: c.value}) }}
  </div>
  <div class="col center"
       v-if="gridMode === GridMode.PlatformCountry || gridMode === GridMode.Platform"
       v-for="p in serverConfig.platforms">
    {{ isAvailable({platform: p}) }}
  </div>
</template>
