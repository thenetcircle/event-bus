<template>
  <div v-if="grafanaUrl.length > 0">

    <grafana-graph :src="urlOfProcessedEvents"></grafana-graph>

    <div class="columns" style="margin-top: 6px;">
      <div class="column is-half">
        <grafana-graph :src="urlOfCompletedStreams"></grafana-graph>
      </div>
      <div class="column">
        <grafana-graph :src="urlOfExceptions"></grafana-graph>
      </div>
    </div>

  </div>

  <div v-else>

    Need to set up Grafana Url!

  </div>

</template>

<script lang="ts">
  import Vue from "vue"
  import GrafanaGraph from './GrafanaGraph.vue'

  export default Vue.extend({
    props: ['storyname'],

    data() {
      return {
        // for example: 'http://fat.thenetcircle.lab:3003/dashboard-solo/db/event-bus-final?var-Community=kauf&var-StoryName={storyName}&var-Prefix=event-bus&theme=light&panelId={panelId}'
        grafanaUrl: GRAFANA_URL
      }
    },

    components: {
      GrafanaGraph
    },

    computed: {
      urlOfProcessedEvents(): string {
        return this.grafanaUrl.replace('{storyName}', this.storyname).replace('{panelId}', '3')
      },

      urlOfCompletedStreams(): string {
        return this.grafanaUrl.replace('{storyName}', this.storyname).replace('{panelId}', '73')
      },

      urlOfExceptions(): string {
        return this.grafanaUrl.replace('{storyName}', this.storyname).replace('{panelId}', '74')
      }
    }
  })
</script>
