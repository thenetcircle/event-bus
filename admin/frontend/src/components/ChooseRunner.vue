<template>

  <div class="modal is-active">
    <div class="modal-background"></div>
    <div class="modal-card">
      <header class="modal-card-head">
        <p class="modal-card-title">Choose a runner</p>
        <button class="delete" @click.prevent="close()" aria-label="close"></button>
      </header>
      <section class="modal-card-body">

        <div v-for="info in runners" style="margin-bottom: 1rem">
          <a class="box" @click.prevent="choose(info)">
            <p class="title is-spaced">
              {{ info.name }}
            </p>

            <runner-summary :info="info"></runner-summary>
          </a>
        </div>

      </section>
    </div>
  </div>

</template>

<script lang="ts">
  import Vue from "vue"
  import {RunnerInfo} from "../lib/runner-utils"
  import request from "../lib/request"
  import RunnerSummary from './RunnerSummary.vue'

  export default Vue.extend({
    props: ['excludes'],

    data() {
      return {
        runners: <RunnerInfo[]>[]
      }
    },

    components: {
      RunnerSummary
    },

    mounted() {
      this.fetchData()
    },

    methods: {
      fetchData() {
        let excludes: string[] = this.excludes || []
        request.getRunners()
          .then(infos => {
            this.runners = infos.filter(info => {
              return excludes.indexOf(info.name) === -1
            })
          })
      },

      close() {
        this.$emit('close')
      },

      choose(runnerInfo: RunnerInfo) {
        this.$emit('choose', runnerInfo)
      }
    }
  })
</script>

<style>
  .editor {
    min-height: 100rem;
  }
</style>
