<template>

  <div class="container box story">
    <p class="title is-1 is-spaced story-name">
      {{ storyName }}

      <span class="tag is-info is-medium">{{ storyInfo.status }}</span>
    </p>

    <div class="tabs is-medium">
      <ul>
        <li :class="{ 'is-active': isOverview }" @click="changeTab('overview')"><a>Overview</a></li>
        <li :class="{ 'is-active': isFallback }" @click="changeTab('fallback')"><a>Failed Events</a></li>
        <li :class="{ 'is-active': isMonitoring }" @click="changeTab('monitoring')"><a>Statistics</a></li>
      </ul>
    </div>

    <div v-show="isOverview">

      <div class="columns">

        <div class="column">
          <a class="box" @click="onEditTask('Source', 'source', storyInfo.source.type, storyInfo.source.settings)">
            <h5 class="title is-5">Source <span class="tag is-link">{{ storyInfo.source.type }}</span></h5>
            <div class="content">
              {{ storyInfo.source.settings }}
            </div>
          </a>
        </div>

        <div class="delimiter">-></div>

        <div class="column" v-for="(trans, index) in storyInfo.transforms">
          <a class="box" @click="onEditTask('Transform ' + (index + 1), 'transform', trans.type, trans.settings)">
            <h5 class="title is-5">Transform {{ index + 1 }} <span class="tag is-light">{{ trans.type }}</span></h5>
            <div class="content">
              {{ trans.settings }}
            </div>
          </a>
        </div>

        <div class="delimiter" v-if="storyInfo.transforms.length > 0">-></div>


        <div class="column">
          <a class="box" @click="onEditTask('Sink', 'sink', storyInfo.sink.type, storyInfo.sink.settings)">
            <h5 class="title is-5">Sink <span class="tag is-primary">{{ storyInfo.sink.type }}</span></h5>
            <div class="content">
              {{ storyInfo.sink.settings }}
            </div>
          </a>
        </div>

        <div class="delimiter" v-if="storyInfo.fallback">-></div>

        <div class="column" v-if="storyInfo.fallback">
          <a class="box" @click="onEditTask('Fallback', 'fallback', storyInfo.fallback.type, storyInfo.fallback.settings)">
            <h5 class="title is-5">Fallback <span class="tag is-warning">{{ storyInfo.fallback.type }}</span></h5>
            <div class="content">
              {{ storyInfo.fallback.settings }}
            </div>
          </a>
        </div>

      </div>

      <p class="subtitle is-4">Runners:</p>
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

      <transition>
        <task-editor v-if="taskEditor.show" v-bind="taskEditor" @close="onTaskEditorClose" @save="onTaskEditorSave"/>
      </transition>

    </div>

    <div v-show="isFallback"></div>

    <div v-if="isMonitoring">
      <graph :storyname="storyName" type="processed"></graph>

      <div class="columns" style="margin-top: 6px;">
        <div class="column is-half">
          <graph :storyname="storyName" type="completion"></graph>
        </div>
        <div class="column">
          <graph :storyname="storyName" type="exception"></graph>
        </div>
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
          tasktype: 'source',
          type: 'http',
          settings: ''
        },
        storyName: this.$route.params.storyName,
        storyInfo: <StoryInfo>{},
        currTab: 'overview'
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

      onEditTask(title: string, tasktype: string, type: string, settings: string) {
        this.taskEditor = {
          ...this.taskEditor, ...{
            show: true,
            title: title,
            tasktype: tasktype,
            type: type,
            settings: settings
          }
        }
      },

      onTaskEditorClose() {
        this.taskEditor = {...this.taskEditor, ...{show: false}}
      },

      onTaskEditorSave(newSettings: string) {
        console.log(newSettings)
        this.storyInfo.source.settings = newSettings
        this.onTaskEditorClose()
      },

      changeTab(tab: string) {
        this.currTab = tab
      }
    },

    computed: {
      isOverview(): boolean {
        return this.currTab == 'overview'
      },
      isFallback(): boolean {
        return this.currTab == 'fallback'
      },
      isMonitoring(): boolean {
        return this.currTab == 'monitoring'
      }
    }
  })
</script>

<style>
  .story {
    background-color: white;
    background-image: -webkit-linear-gradient(top, white, #efefef, #fefefe, #fefefe, #fefefe, white);
    background-image: -moz-linear-gradient(top, white, #efefef, #fefefe, #fefefe, #fefefe, white);
    background-image: -o-linear-gradient(top, white, #efefef, #fefefe, #fefefe, #fefefe, white);
    background-image: linear-gradient(to bottom, #fefefe, #efefef, #fefefe, #fefefe, #fefefe, white);
  }

  .story-name {
    padding: 15px 20px;
  }

  .delimiter {
    padding-top: 50px;
  }

  .content {
    font-size: 0.9rem;
  }
</style>
