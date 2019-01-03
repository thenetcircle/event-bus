<template>

  <div class="container">

    <nav class="breadcrumb has-succeeds-separator" aria-label="breadcrumbs">
      <ul>
        <li>
          <router-link :to="{ name: 'home' }">Home</router-link>
        </li>
        <li class="is-active"><a href="#" aria-current="page">Runners</a></li>
      </ul>
    </nav>

    <div class="columns is-multiline">

      <div class="column is-12" v-for="info in runners">
        <router-link class="box" :to="{ name: 'runner', params: { 'runnerName': info.name } }">
          <p class="title is-spaced">
            {{ info.name }}
          </p>
          <runner-summary :info="info"></runner-summary>
        </router-link>
      </div>

    </div>

  </div>

</template>

<script lang="ts">
  import Vue from "vue"
  import {RunnerInfo} from "../lib/runner-utils";
  import RunnerSummary from './RunnerSummary.vue'
  import request from "../lib/request";

  export default Vue.extend({
    data() {
      return {
        runners: <RunnerInfo[]>[]
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
        request.getRunners()
          .then(items => {
            this.runners = items
          })
      }
    }
  })

</script>
