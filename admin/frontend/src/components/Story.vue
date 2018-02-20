<template>

  <div class="container box story" style="margin-bottom: 1rem">
    <div class="level">
      <div class="level-left">
        <div class="level-item">
          <p class="title is-2 is-spaced story-name">{{ storyName }}</p>
        </div>
        <div class="level-item">
          <div class="tags has-addons">
            <span class="tag is-dark">Status:</span>
            <span class="tag is-info">{{ storyInfo.status }}</span>
          </div>
        </div>
      </div>
    </div>

    <div class="tabs is-medium">
      <ul>
        <li :class="{ 'is-active': isOverview }" @click="changeTab('overview')"><a>Overview</a></li>
        <li :class="{ 'is-active': isRunners }" @click="changeTab('runners')"><a>Runners</a></li>
        <li :class="{ 'is-active': isMonitoring }" @click="changeTab('monitoring')">
          <a>Statistics</a></li>
        <li :class="{ 'is-active': isFallback }" @click="changeTab('fallback')"
            v-if="storyInfo.fallback !== undefined"><a>Failed Events</a>
        </li>
      </ul>
    </div>

    <div v-show="isOverview">

      <story-graph v-if="storyInfo.source !== undefined" :info="storyInfo"
                   @save="onSaveStory"></story-graph>

    </div>

    <div v-show="isRunners">

      <div class="level">
        <div class="level-left">
        </div>
        <div class="level-right">
          <div class="level-item">
            <a class="button" @click="onOpenChooseRunner">Assign to a new runner</a>
          </div>
        </div>
      </div>

      <table class="table is-bordered is-striped is-narrow is-hoverable is-fullwidth">
        <thead>
        <tr>
          <th>Name</th>
          <th>Server</th>
          <th>Version</th>
          <th>Action</th>
        </tr>
        </thead>
        <tbody>
        <tr v-for="item in runners">
          <td>
            <router-link :to="{ name: 'runner', params: { 'runnerName': item.name } }">{{
              item.name }}
            </router-link>
          </td>
          <td>{{ item.server }}</td>
          <td>{{ item.version }}</td>
          <td><a class="button is-danger" @click="onDismissRunner(item)">Dismiss</a></td>
        </tr>
        </tbody>
      </table>

      <choose-runner v-if="chooseRunner" @close="onCloseChooseRunner" @choose="onAssignRunner"
                     :excludes="runners.map(info => info.name)"></choose-runner>

    </div>

    <div v-show="isFallback"></div>

    <div v-if="isMonitoring">
      <grafana-graph :storyname="storyName" type="processed"></grafana-graph>

      <div class="columns" style="margin-top: 6px;">
        <div class="column is-half">
          <grafana-graph :storyname="storyName" type="completion"></grafana-graph>
        </div>
        <div class="column">
          <grafana-graph :storyname="storyName" type="exception"></grafana-graph>
        </div>
      </div>
    </div>


  </div>

</template>

<script lang="ts">
  import Vue from "vue"
  import request from '../lib/request'
  import {StoryInfo, StoryUtils} from '../lib/story-utils'
  import StoryGraph from './StoryGraph.vue'
  import GrafanaGraph from './GrafanaGraph.vue'
  import {RunnerInfo} from '../lib/runner-utils'
  import ChooseRunner from './ChooseRunner.vue'

  export default Vue.extend({
    data() {
      return {
        storyName: this.$route.params.storyName,
        storyInfo: {} as StoryInfo,
        currTab: 'overview',
        runners: <RunnerInfo[]>[],
        chooseRunner: false
      }
    },

    components: {
      StoryGraph,
      GrafanaGraph,
      ChooseRunner
    },

    created() {
      this.fetchData()
    },

    watch: {
      '$route': 'fetchData'
    },

    methods: {
      fetchData() {
        request.getStory(this.storyName)
          .then(storyInfo => {
            this.storyInfo = storyInfo
          })
        request.getStoryRunners(this.storyName)
          .then(infos => {
            this.runners = infos
          })
      },

      changeTab(tab: string) {
        this.currTab = tab
      },

      onSaveStory(newStoryInfo: StoryInfo) {
        request.updateStory(this.storyName, newStoryInfo)
          .then(() => {
            this.storyInfo = StoryUtils.copyStoryInfo(newStoryInfo)
          })
      },

      onOpenChooseRunner(): void {
        this.chooseRunner = true
      },
      onCloseChooseRunner(): void {
        this.chooseRunner = false
      },
      onAssignRunner(runnerInfo: RunnerInfo): void {
        if (confirm(`Are you sure to assign story "${this.storyName}" to runner "${runnerInfo.name}"?`)) {
          this.onCloseChooseRunner()
          request.assignRunner(this.storyName, runnerInfo.name)
            .then(() => {
              this.runners.push(runnerInfo)
            })
        }
      },
      onDismissRunner(runnerInfo: RunnerInfo): void {
        if (confirm(`Are you sure to dismiss story "${this.storyName}" from runner "${runnerInfo.name}"?`)) {
          request.dismissRunner(this.storyName, runnerInfo.name)
            .then(() => {
              let index = -1
              this.runners.forEach((info, _index) => {
                if (info.name == runnerInfo.name) {
                  index = _index
                  return;
                }
              })
              if (index !== -1) {
                this.runners.splice(index, 1)
              }
            })
        }
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
      },
      isRunners(): boolean {
        return this.currTab == 'runners'
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

  pre.settings {
    width: 100%;
  }
</style>
