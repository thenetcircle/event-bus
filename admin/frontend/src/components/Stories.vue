<template>

  <div class="container">
    <div class="columns is-multiline">
      <div class="column is-half" v-for="story in stories">

        <a class="box" :href="story.link">
          <p class="title is-spaced">
            {{ story.name }}
          </p>

          <table class="table is-narrow">
            <tbody>
            <tr>
              <th>Status:</th>
              <td>{{ story.info.status.toString() }}</td>
              <th v-if="story.info.fallback">Fallback:</th>
              <td v-if="story.info.fallback">{{ story.info.fallback.type }}</td>
            </tr>
            <tr>
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
                  summary.push(`from <span class="tag is-info is-medium">${storyInfo.source.type}</span>`)
                  storyInfo.transforms.forEach(trans =>
                    summary.push(`to <span class="tag is-light is-medium">${trans.type}</span>`))
                  summary.push(`to <span class="tag is-primary is-medium">${storyInfo.sink.type}</span>`)

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
</style>
