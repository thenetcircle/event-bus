<template>

  <div class="container box">
    <p class="title is-1 is-spaced story-name">{{ storyName }}</p>

    <p class="subtitle is-3">Runners:</p>
    <table class="table is-bordered is-striped is-narrow is-hoverable is-fullwidth">
      <thead>
      <tr>
        <th>Name</th>
        <th>Server</th>
        <th>Version</th>
      </tr>
      </thead>
      <tbody>
      <tr>
        <td>default-runner</td>
        <td>cloud-host-01</td>
        <td></td>
      </tr>
      </tbody>
    </table>

    <p class="subtitle is-3">Configuration:</p>
    <table class="table is-bordered is-striped is-narrow is-hoverable is-fullwidth">
      <thead>
      <tr>
        <td></td>
        <th>Type</th>
        <th>Settings</th>
        <th>Actions</th>
      </tr>
      </thead>
      <tbody>
      <tr>
        <th>Source</th>
        <td><span class="tag is-light">{{ storyInfo.source.type }}</span></td>
        <td>{{ storyInfo.source.settings }}</td>
        <td><a v-on:click="onEditTask" class="button is-rounded is-small">Update</a></td>
      </tr>
      <tr v-for="trans in storyInfo.transforms">
        <th>Transform</th>
        <td><span class="tag is-light">{{ trans.type }}</span></td>
        <td>{{ trans.settings }}</td>
        <td><a class="button is-rounded is-small">Update</a></td>
      </tr>
      <tr>
        <th>Sink</th>
        <td><span class="tag is-light">{{ storyInfo.sink.type }}</span></td>
        <td>{{ storyInfo.sink.settings }}</td>
        <td><a class="button is-rounded is-small">Update</a></td>
      </tr>
      <tr v-if="storyInfo.fallback">
        <th>Fallback</th>
        <td><span class="tag is-light">{{ storyInfo.fallback.type }}</span></td>
        <td>{{ storyInfo.fallback.settings }}</td>
        <td><a class="button is-rounded is-small">Update</a></td>
      </tr>
      </tbody>
    </table>

    <task-editor :show="showTaskEditor" title="test" />

    <p class="subtitle is-3">Monitor:</p>
    <iframe :src="grafanaLinkOfProcessedEvents" width="100%" height="200" frameborder="0"></iframe>

    <div class="columns is-gapless">
      <div class="column is-half"><iframe :src="grafanaLinkOfCompletion" width="100%" height="200" frameborder="0"></iframe></div>
      <div class="column"><iframe :src="grafanaLinkOfException" width="100%" height="200" frameborder="0"></iframe></div>
    </div>

  </div>

</template>

<script lang="ts">
  import Vue from "vue"
  import axios from "axios"
  import {StoryInfo, StoryUtils} from '../lib/story-utils';
  import TaskEditor from "./TaskEditor.vue"

  export default Vue.extend({
    data() {
      return {
        showTaskEditor: false,
        storyName: this.$route.params.storyName,
        storyInfo: <StoryInfo>{}
      }
    },

    components: {
      TaskEditor
    },

    created() {
      this.fetchData()
    },

    watch: {
      '$route': 'fetchData'
    },

    computed: {
      grafanaLinkOfProcessedEvents(): string {
        return `http://fat:3003/dashboard-solo/db/event-bus-final?from=1518160194700&to=1518161994700&var-Community=kauf&var-StoryName=${this.storyName}&var-Prefix=event-bus&theme=light&panelId=3`
      },

      grafanaLinkOfCompletion(): string {
        return `http://fat:3003/dashboard-solo/db/event-bus-final?from=1518167243055&to=1518169043055&var-Community=kauf&var-StoryName=${this.storyName}&var-Prefix=event-bus&theme=light&panelId=73`
      },

      grafanaLinkOfException(): string {
        return `http://fat:3003/dashboard-solo/db/event-bus-final?from=1518167346922&to=1518169146922&var-Community=kauf&var-StoryName=${this.storyName}&var-Prefix=event-bus&theme=light&panelId=74`
      }
    },

    methods: {
      fetchData() {
        axios.get('/api/story/' + this.storyName)
          .then(response => {
            let data = response.data
            if (data) {
              console.debug(data)
              this.storyInfo = StoryUtils.parseStory(data)
            }
          })
          .catch(error => {
            console.error(error)
          })
      },

      onEditTask() {
        this.showTaskEditor = true
      }
    }
  })
</script>

<style>
  .story-name {
    background: linear-gradient(to bottom, #efefef, white);
    padding: 15px 20px;
  }
</style>
