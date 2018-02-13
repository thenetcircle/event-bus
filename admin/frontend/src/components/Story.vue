<template>

  <div class="container box story">
    <div class="level">
      <div class="level-left">
        <div class="level-item">
          <p class="title is-1 is-spaced story-name">{{ storyName }}</p>
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
        <li :class="{ 'is-active': isFallback }" @click="changeTab('fallback')"><a>Failed Events</a>
        </li>
        <li :class="{ 'is-active': isMonitoring }" @click="changeTab('monitoring')">
          <a>Statistics</a></li>
      </ul>
    </div>

    <div v-show="isOverview">

      <story-graph v-if="storyInfo.source !== undefined" :info="storyInfo"
                   @save="onSaveStory"></story-graph>

      <section style="margin-top: 3rem;">
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
      </section>

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
  import request from '../lib/request';
  import {StoryInfo, StoryUtils} from '../lib/story-utils';
  import StoryGraph from './StoryGraph.vue'
  import GrafanaGraph from './GrafanaGraph.vue'


  export default Vue.extend({
    data() {
      return {
        storyName: this.$route.params.storyName,
        storyInfo: {} as StoryInfo,
        currTab: 'overview'
      }
    },

    components: {
      StoryGraph,
      GrafanaGraph
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
      },

      changeTab(tab: string) {
        this.currTab = tab
      },

      onSaveStory(newStoryInfo: StoryInfo) {
        /*if (confirm('are you sure to save?')) {
          this.storyInfo = StoryUtils.copyStoryInfo(newStoryInfo)
          this.storyInfo.source.settings = '{}'
          console.log()
        }*/
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

  pre.settings {
    width: 100%;
  }
</style>
