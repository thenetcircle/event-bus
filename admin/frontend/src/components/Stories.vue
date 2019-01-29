import {RunnerStatus} from '../lib/runner-utils';
<template>

  <div class="container">

    <div class="tabs">
      <ul>
        <li :class="currTab == 'all' ? 'is-active' : null" @click="changeTab('all')"><a>ALL</a></li>
        <li :class="currTab == 'running' ? 'is-active' : null" @click="changeTab('running')"><a>RUNNING</a></li>
        <li :class="currTab == 'stopped' ? 'is-active' : null" @click="changeTab('stopped')"><a>STOPPED</a></li>
      </ul>
    </div>

    <div style="margin-bottom: 1.5rem;">
      <input class="input" type="text" placeholder="Filter By Story Name" v-model="filterValue"/>
    </div>

    <div class="columns is-multiline">

      <div class="column is-6" v-for="story in currStories">
        <router-link class="box" :to="{ name: 'story', params: { 'storyName': story.name } }">

          <div class="columns" style="margin-bottom: 0px;">
            <div class="column is-narrow">
              <h4 class="subtitle is-4">
                {{ story.name }}
              </h4>
            </div>
            <div class="column is-narrow">
              <a class="icon" @click.stop.prevent="onRemoveStory(story)"><i
                class="fas fa-trash-alt"></i></a>
            </div>
          </div>

          <div style="margin-bottom: 1.2rem;">
            <span v-html="story.summary"></span>
          </div>

          <div v-if="story.runners.length">
            <table class="table is-bordered" style="margin-bottom: 0;">
              <thead>
              <tr>
                <th>name</th>
                <th>host</th>
                <th>version</th>
                <th>status</th>
              </tr>
              </thead>
              <tbody>
              <tr v-for="runner in story.runners">
                <td>{{ runner.name }}</td>
                <td>{{ runner.host }}</td>
                <td>{{ runner.version }}</td>
                <td>{{ runner.status }}</td>
              </tr>
              </tbody>
            </table>
          </div>
          <div v-else>Not assign to a runner yet</div>
        </router-link>
      </div>

    </div>
  </div>

</template>

<script lang="ts">
  import Vue from "vue"
  import request from "../lib/request"
  import {StoryInfo} from '../lib/story-utils';
  import {RunnerInfo, RunnerStatus} from "../lib/runner-utils";

  interface StorySummary {
    name: string,
    summary: string,
    info: StoryInfo,
    runners: RunnerInfo[]
  }

  export default Vue.extend({
    data() {
      return {
        stories: <StorySummary[]>[],
        fullCurrStories: <StorySummary[]>[],
        currStories: <StorySummary[]>[],
        currTab: 'all',
        filterValue: ''
      }
    },

    created() {
      this.fetchData()
    },

    watch: {
      '$route': 'fetchData',
      filterValue: function () {
        this.filterCurrStoriesByFilterValue()
      }
    },

    methods: {
      filterCurrStoriesByFilterValue() {
        let filterValue = this.filterValue
        if (filterValue == '') {
          this.currStories = this.fullCurrStories
        }
        else {
          this.currStories = this.fullCurrStories.filter(ssu => ssu.name.indexOf(filterValue) !== -1)
        }
      },

      fetchData() {
        request.getStories()
          .then(result => {

            let stories: StorySummary[] = []

            result.forEach((item: [string, StoryInfo]) => {

              let [storyName, storyInfo] = item

              let summary: string[] = [];
              summary.push(`<span class="tag is-success">${storyInfo.source.type} Source</span>`)

              storyInfo.operators.forEach(trans =>
                summary.push(`<span class="tag is-light">${trans.type} Transform</span>`))

              summary.push(`<span class="tag is-primary">${storyInfo.sink.type} Sink</span>`)

              request.getStoryRunners(storyName).then(runners => {
                stories.push({
                  name: storyName,
                  summary: summary.join(' -> '),
                  info: storyInfo,
                  runners: runners
                })
              })

            })

            this.stories = stories
            this.changeTab(this.currTab)
          })
      },

      changeTab(tab: string) {
        this.currTab = tab
        this.filterValue = ''
        if (tab == 'all') {
          this.fullCurrStories = this.sortStories(this.stories);
        }
        else {
          this.fullCurrStories = this.sortStories(this.stories.filter(storySummary => {
            let runningRunners = this.getStoryRunningRunnerNum(storySummary)
            if (tab == 'running')
              return runningRunners > 0
            else
              return runningRunners === 0
          }))
        }
        this.filterCurrStoriesByFilterValue()
      },

      sortStories(stories: StorySummary[]) {
        return stories
        /*return stories
          // .sort((item1, item2) => item1.name > item2.name ? -1 : 1)
          .sort((item1, item2) => item1.info.source.type > item2.info.source.type ? -1 : 1)*/
      },

      getStoryRunningRunnerNum(story: StorySummary) {
        let runningRunners = 0
        story.runners.forEach(runner => {
          if (runner.status == RunnerStatus.RUNNING)
            runningRunners++
        })
        return runningRunners
      },

      onRemoveStory(story: StorySummary) {
        /*if (this.getStoryRunningRunnerNum(story) > 0) {
          alert(`story ${story.name} is still running, can not be removed.`);
          return;
        }*/
        if (story.runners.length > 0) {
          alert(`story ${story.name} has been assigned to ${story.runners.length} runners, need to dismiss them before remove the story.`);
          return;
        }

        if (!confirm(`Are you sure delete story ${story.name}?`)) {
          return;
        }

        request.removeStory(story.name)
          .then(() => {
            this.stories.forEach((_story, _index) => {
              if (_story.name == story.name) {
                this.stories.splice(_index, 1)
                console.log(this.stories)
              }
            })
            this.changeTab(this.currTab)
          })
      }
    }
  })
</script>

<style>
  .story {
    background-color: white;
    background-image: -webkit-linear-gradient(top, #fefefe, #efefef, #fefefe, #fefefe, white);
    background-image: -moz-linear-gradient(top, #fefefe, #efefef, #fefefe, #fefefe, white);
    background-image: -o-linear-gradient(top, #fefefe, #efefef, #fefefe, #fefefe, white);
    background-image: linear-gradient(to bottom, #fefefe, #efefef, #fefefe, #fefefe, white);
  }
</style>
