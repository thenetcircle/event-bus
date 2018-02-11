<template>

  <div class="container">
    <div class="columns is-multiline">
      <div class="column is-half" v-for="story in stories">

        <a class="box story" :href="story.link">
          <p class="title is-spaced">
            {{ story.name }}
          </p>

          <table class="table is-narrow">
            <tbody>
            <tr>
              <th>Status:</th>
              <td>{{ story.info.status }}</td>
              <th>Runners:</th>
              <td>default-runner</td>
            </tr>
            </tbody>
          </table>

          <span v-html="story.summary"></span>
        </a>

      </div>
    </div>
  </div>

</template>

<script lang="ts">
  import Vue from "vue"
  import axios from "axios"
  import {StoryInfo, StoryUtils} from '../lib/story-utils';

  interface StorySummary {
    name: string,
    summary: string,
    info: StoryInfo,
    link: string
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
        axios.get('/api/stories')
          .then(response => {
            let data = response.data
            let stories: StorySummary[] = []

            if (data) {
              for (let key in data) {
                if (data.hasOwnProperty(key)) {

                  let storyInfo = StoryUtils.parseStory(data[key])

                  let summary: string[] = [];
                  summary.push(`<span class="tag is-link">${storyInfo.source.type} Source</span>`)

                  storyInfo.transforms.forEach(trans =>
                    summary.push(`<span class="tag is-light">${trans.type} Transform</span>`))

                  summary.push(`<span class="tag is-primary">${storyInfo.sink.type} Sink</span>`)

                  if (storyInfo.fallback) {
                    summary.push(`<span class="tag is-warning">${storyInfo.fallback.type} Fallback</span>`)
                  }

                  stories.push({
                    name: key,
                    summary: summary.join(' -> '),
                    info: storyInfo,
                    link: '/story/' + key
                  })

                }
              }
            }

            this.stories = stories
          })
          .catch(error => {
            console.error(error)
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
