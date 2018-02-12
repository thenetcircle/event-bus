<template>
  <div>
    <vue-loading type="barsCylon" v-show="loading"></vue-loading>
    <iframe @load="onLoaded" :src="src" width="100%" height="200" frameborder="0"></iframe>
  </div>
</template>

<script lang="ts">
  import Vue from "vue"
  import vueLoading from 'vue-loading-template'

  export default Vue.extend({
    props: ['storyname', 'type'],

    data() {
      return {
        loading: true,
        url: 'http://fat.thenetcircle.lab:3003/dashboard-solo/db/event-bus-final?from=1518167243055&to=1518169043055&var-Community=kauf&var-StoryName={storyName}&var-Prefix=event-bus&theme=light&panelId={panelId}'
      }
    },

    components: { vueLoading },

    computed: {
      panelId(): string {
        switch (this.type) {
          case 'completion':
            return '73'
          case 'exception':
            return '74'
          case 'processed':
          default:
            return '3'
        }
      },

      src(): string {
        return this.url.replace('{storyName}', this.storyname).replace('{panelId}', this.panelId)
      }
    },

    methods: {
      onLoaded() {
        this.loading = false
      }
    }
  })
</script>
