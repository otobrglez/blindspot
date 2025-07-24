// @ts-check
import {defineConfig, envField} from 'astro/config';

import vue from '@astrojs/vue';
import mdx from '@astrojs/mdx';

// https://astro.build/config
export default defineConfig({
  experimental: {
    // assets: true
  },

  integrations: [vue({
      devtools: false,
      jsx: true,
    }
  ), mdx()],

  env: {
    schema: {
      'BLINDSPOT_ENV': envField.string({context: "client", access: "public", optional: false}),
      'BLINDSPOT_VERSION': envField.string({context: "client", access: "public", optional: false, default: 'dev'}),
      'BLINDSPOT_ENDPOINT': envField.string({
        context: "client", access: "public",
        optional: false, default: "http://localhost:7779/"
      }),
    },
    validateSecrets: true
  },

  vite: {
    css: {
      preprocessorOptions: {
        scss: {
          silenceDeprecations: ["legacy-js-api"],
        },
      },
    },
  },
});
