<template>

  <div class="container">

    <div class="columns is-multiline">

      <div class="column is-12" v-for="info in runners">
        <router-link class="box" :to="{ name: 'runner', params: { 'runnerName': info.name } }">
          <p class="title is-spaced">
            {{ info.name }}
          </p>

          <table class="table is-narrow">
            <tbody>
            <tr>
              <th>Status:</th>
              <td>{{ info.status }}</td>
            </tr>
            <tr>
              <th>Server:</th>
              <td>{{ info.server }}</td>
            </tr>
            <tr>
              <th>Assigned Stories:</th>
              <td>{{ info.stories.join(', ') }}</td>
            </tr>
            <tr>
              <th>Version:</th>
              <td>{{ info.version }}</td>
            </tr>
            </tbody>
          </table>
        </router-link>
      </div>

    </div>

  </div>

</template>

<script lang="ts">

  import Vue from "vue"
  import request from "../lib/request"
  import {RunnerInfo, RunnerStatus} from "../lib/runner-utils";

  let testRunners = [
    {
      name: 'default-runner',
      status: RunnerStatus.RUNNING,
      server: 'thin_thenetcircle_lab',
      stories: ['http-to-benn'],
      version: 'master'
    },

    {
      name: 'test-runner',
      status: RunnerStatus.IDLE,
      server: 'thin_thenetcircle_lab',
      stories: ['http-to-kafka', 'kafka-to-master-benn'],
      version: 'master'
    }
  ]

  export default Vue.extend({
    data() {
      return {
        runners: <RunnerInfo[]>testRunners
      }
    }
  })

</script>
