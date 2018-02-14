<template>

  <div class="container">

    <div style="margin-bottom: 1rem;">
      <div class="field">
        <a class="button is-info" href="/newstory">Create New Story</a>
      </div>
    </div>

    <div class="columns is-multiline">

      <div class="column is-12" v-for="story in stories">
        <router-link class="box" :to="{ name: 'story', params: { 'storyName': story.name } }">
          <p class="title is-spaced">
            {{ story.name }}
          </p>

          <table class="table is-narrow">
            <tbody>
            <tr>
              <th>Status:</th>
              <td>{{ story.info.status }}</td>
            </tr>
            <tr>
              <th>Runners:</th>
              <td>default-runner</td>
            </tr>
            </tbody>
          </table>

          <span v-html="story.summary"></span>
        </router-link>
      </div>

    </div>
  </div>

</template>

<script lang="ts">
  import Vue from "vue"
  import request from "../lib/request"
  import {StoryInfo, StoryUtils} from '../lib/story-utils';

  interface StorySummary {
    name: string,
    summary: string,
    info: StoryInfo
  }

  export default Vue.extend({
    data() {
      return {
        stories: <StorySummary[]>[]
      }
    },

    created() {
      this.fetchData()
    },

    watch: {
      '$route': 'fetchData'
    },

    methods: {
      fetchData() {
        request.getStories()
          .then(result => {

            let stories: StorySummary[] = []

            result.forEach((item: [string, StoryInfo]) => {

              let [storyName, storyInfo] = item

              let summary: string[] = [];
              summary.push(`<span class="tag is-success">${storyInfo.source.type} Source</span>`)

              storyInfo.transforms.forEach(trans =>
                summary.push(`<span class="tag is-light">${trans.type} Transform</span>`))

              summary.push(`<span class="tag is-primary">${storyInfo.sink.type} Sink</span>`)

              if (storyInfo.fallback) {
                summary.push(`<span class="tag is-warning">${storyInfo.fallback.type} Fallback</span>`)
              }

              stories.push({
                name: storyName,
                summary: summary.join(' -> '),
                info: storyInfo
              })

            })

            this.stories = stories

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
