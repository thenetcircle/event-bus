<template>

  <div class="container box runner" style="margin-bottom: 1rem">

    <p class="title is-2 is-spaced">{{ runnerInfo.name }}</p>

    <hr/>

    <p class="subtitle is-spaced">Summary</p>
    <runner-summary :info="runnerInfo"></runner-summary>


  </div>

</template>

<script lang="ts">
  import Vue from "vue"
  import request from '../lib/request'
  import {RunnerInfo} from '../lib/runner-utils'
  import RunnerSummary from './RunnerSummary.vue'

  export default Vue.extend({
    data() {
      return {
        runnerInfo: <RunnerInfo>{}
      }
    },

    components: {
      RunnerSummary
    },

    created() {
      this.fetchData()
    },

    watch: {
      '$route': 'fetchData'
    },

    methods: {
      fetchData() {
        let runnerName = this.$route.params.runnerName
        request.getRunner(runnerName)
          .then(info => {
            this.runnerInfo = info
          })
      }
    }
  })
</script>

<style>
  .runner {
    background-color: white;
    background-image: -webkit-linear-gradient(top, white, #efefef, #fefefe, #fefefe, #fefefe, white);
    background-image: -moz-linear-gradient(top, white, #efefef, #fefefe, #fefefe, #fefefe, white);
    background-image: -o-linear-gradient(top, white, #efefef, #fefefe, #fefefe, #fefefe, white);
    background-image: linear-gradient(to bottom, #fefefe, #efefef, #fefefe, #fefefe, #fefefe, white);
  }
</style>
