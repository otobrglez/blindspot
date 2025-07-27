<script setup lang="ts">
import {v4 as uuidv4} from 'uuid';
import {BLINDSPOT_ENDPOINT} from 'astro:env/client';
import {defineEmits, ref, onMounted, watch, defineProps, onUnmounted, toRef, nextTick} from 'vue';
import {buildAssistantMessage, buildUserMessage, type Message} from '../services/Messages.ts';
import type {GridConfig} from "./LiveGrid.vue";
import {SymbolKind} from "vscode-languageserver-types";

interface Props {
  visible: boolean;
  gridConfig: GridConfig
}

const {visible, gridConfig} = defineProps<Props>();

// Session and URL
const sessionID = uuidv4();
const streamURL: URL = new URL(BLINDSPOT_ENDPOINT + "assistant?sessionID=" + sessionID);
const buildStreamURL = (query?: string): URL => {
  const newUrl = new URL(streamURL.toString());
  if (query) newUrl.searchParams.set("input", query);
  for (const [key, value] of Object.entries(gridConfig)) {
    newUrl.searchParams.set(key, value.toString());
  }
  return newUrl;
}


// User inputs
const userInput = ref<string>("");
const userInputEnabled = ref<boolean>(true);
const emitClosed = defineEmits(['closed'])

const messages = ref<Message[]>([]);

function close() {
  emitClosed('closed');
}

const initialized = ref<boolean>(false);
var eventSource: EventSource | null = null;

function send() {
  if (eventSource !== null) return;

  const message = (userInput.value || '').trim()
  if (message.length === 0) return
  messages.value.push(buildUserMessage(message));
  userInput.value = "";
  scrollToBottom()

  eventSource = new EventSource(buildStreamURL(message), {withCredentials: false});
  eventSource.onmessage = (event) => {
    onAssistantMessage(event.data)
  };
  eventSource.onerror = (event) => {
    initialized.value = false
    eventSource?.close();
    eventSource = null;
    scrollToBottom();
  };

  eventSource.onopen = (event) => {
    initialized.value = true;
  }
}

function initializeAssistant() {
  console.log(`Initializing assistant... ${initialized.value ? "already initialized" : "not initialized"}`);

  if (eventSource) {
    console.log("Already connected to ES");
  } else {
    eventSource = new EventSource(buildStreamURL(), {withCredentials: false});
    eventSource.onmessage = (event) => {
      onAssistantMessage(event.data)
    };
    eventSource.onerror = (event) => {
      initialized.value = false
      eventSource?.close();
      eventSource = null;
      scrollToBottom()
    };

    eventSource.onopen = (event) => {
      initialized.value = true;
    }
  }
}

function onAssistantMessage(message: string) {
  const parsedMessage = buildAssistantMessage(message)
  const existingMessageIndex = messages.value.findIndex(m => m.id === parsedMessage.id);
  if (existingMessageIndex !== -1) {
    messages.value[existingMessageIndex].message += parsedMessage.message;
  } else {
    messages.value.push(parsedMessage);
  }
}

watch(() => visible, (newVisible) => {
  if (newVisible) {
    console.log(`Assistant visible (${visible} -> ${newVisible})`);
    initializeAssistant();
  } else {
    console.log(`Assistant hidden ${visible} -> ${newVisible}`);
  }
}, {immediate: true})

watch(messages, () => {
  scrollToBottom();
});

onMounted(() => {
  scrollToBottom();
});

const exchangeRef = ref<HTMLDivElement | null>(null);

function scrollToBottom() {
  nextTick(() => {
    if (exchangeRef.value) {
      exchangeRef.value.scrollTop = exchangeRef.value.scrollHeight;
    }
  });
}

</script>
<template>
  <div class="assistant" v-show="visible">
    <div class="toolbar">
      <h1>Blindspot Assistant</h1>
      <a class="close" @click.prevent="close">Hide</a>
    </div>

    <div class="content">
      <div class="exchange" ref="exchangeRef">
        <div class="message" v-for="message in messages"
             :class="{fromUser: message.fromUser,
             fromAssistant: !message.fromUser,
             last: message.id === messages[messages.length - 1].id}">
          {{ message.message }}
        </div>
      </div>

      <div class="input">
        <form @submit.prevent="send">
          <input :disabled="!userInputEnabled"
                 autofocus
                 type="text"
                 placeholder="Ask a question..."
                 v-model="userInput"/>
        </form>
      </div>
    </div>
  </div>
</template>
<style lang="scss" scoped>
.assistant {
  position: fixed;
  display: block;
  top: 250px;
  left: calc(50% - 250px);
  max-width: 500px;
  // min-height: 30px;
  z-index: 1000;
  padding: 10px;
  box-shadow: 0px 3px 4px 0px rgba(102, 102, 102, 0.49);
  background-color: #EEE;

  .toolbar {
    position: relative;

    h1 {
      font-size: 16pt;
      font-weight: bold;
    }

    a.close {
      position: absolute;
      top: 3px;
      right: 0px
    }
  }

  .input {
    display: block;
    width: 100%;

    text-align: center;

    input {
      text-align: left;
      width: 98%;
    }
  }

  .content {
    display: flex;
    flex-direction: column;
    height: 100%;

    .exchange {
      height: 300px;
      overflow-y: auto;
      display: flex;
      flex-direction: column;

      .message {
        display: flex;
        flex-direction: column;
        margin-top: 10px;
        margin-bottom: 10px;
        padding: 10px;
        border-radius: 5px;
        background-color: #FFF;

        &.fromUser {
          align-self: flex-end;
          background-color: #E0E0E0;
        }
      }
    }

    .input {
      display: block;
      width: 100%;
      text-align: center;
      margin-top: 10px;

      input {
        text-align: left;
        width: 98%;
      }
    }
  }

}
</style>
