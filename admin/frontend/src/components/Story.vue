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
        <td><a @click="onEditTask('Source', storyInfo.source.type, storyInfo.source.settings)" class="button is-rounded is-small">Update</a></td>
      </tr>
      <tr v-for="(trans, index) in storyInfo.transforms">
        <th>Transform {{ index + 1 }}</th>
        <td><span class="tag is-light">{{ trans.type }}</span></td>
        <td>{{ trans.settings }}</td>
        <td><a @click="onEditTask('Transform ' + (index + 1), trans.type, trans.settings)" class="button is-rounded is-small">Update</a></td>
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

    <transition>
      <task-editor v-bind="taskEditor" @close="onTaskEditorClose" />
    </transition>

    <p class="subtitle is-3">Monitoring:</p>
    <graph :storyname="storyName" type="processed"></graph>

    <div class="columns is-gapless">
      <div class="column is-half">
        <graph :storyname="storyName" type="completion"></graph>
      </div>
      <div class="column">
        <graph :storyname="storyName" type="exception"></graph>
      </div>
    </div>

  </div>

</template>

<script lang="ts">
  import Vue from "vue"
  import axios from "axios"
  import {StoryInfo, StoryUtils} from '../lib/story-utils';
  import TaskEditor from "./TaskEditor.vue"
  import Graph from './Graph.vue'

  export default Vue.extend({
    data() {
      return {
        taskEditor: {
          show: false,
          title: '',
          type: '',
          settings: ''
        },
        storyName: this.$route.params.storyName,
        storyInfo: <StoryInfo>{}
      }
    },

    components: {
      TaskEditor,
      Graph
    },

    created() {
      this.fetchData()
    },

    watch: {
      '$route': 'fetchData'
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

      onEditTask(title: string, type: string, settings: string) {
        this.taskEditor = { ...this.taskEditor, ...{show: true, title: title, type: type, settings: settings} }
      },

      onTaskEditorClose() {
        this.taskEditor = { ...this.taskEditor, ...{show: false} }
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
