<template>

  <div class="container">
    <a class="story-item" v-for="story in stories"  :href="story.link">
      <section class="section">
        <p class="title is-3 is-spaced">
          {{ story.name }}
        </p>
        <p class="subtitle is-5"><span v-html="story.summary"></span></p>
        <table class="table is-narrow">
          <tbody>
          <tr>
            <th>Status:</th>
            <td>{{ story.status }}</td>
            <th v-if="story.fallback">Fallback:</th>
            <td v-if="story.fallback">{{ story.fallback }}</td>
          </tr>
          <tr>
            <th>Runners:</th>
            <td>Cassandra</td>
          </tr>
          </tbody>
        </table>
      </section>
    </a>
  </div>

</template>

<script lang="ts">
  import Vue from "vue"
  import axios from "axios"


  export default Vue.extend({
    data() {
      return {
        stories: [],
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
            let stories = []

            if (data) {
              for (let key in data) {
                if (data.hasOwnProperty(key)) {

                  let value = data[key]
                  let sourceType = value['source'].split('#')[0]
                  let sinkType = value['sink'].split('#')[0]

                  let summary = [];
                  summary.push(`from <span class="tag is-info is-medium">${sourceType}</span>`)
                  if (value['transforms'] && value['transforms'].length > 0) {
                    value['transforms'].split('|||').forEach(trans => {
                      let transType = trans.split('#')[0]
                      summary.push(`to <span class="tag is-light is-medium">${transType}</span>`)
                    })
                  }
                  summary.push(`to <span class="tag is-primary is-medium">${sinkType}</span>`)

                  let fallback = value['fallback'] && value['fallback'].split('#')[0]

                  stories.push({
                    name: key,
                    summary: summary.join(' -> '),
                    status: value['status'] || 'INIT',
                    fallback: fallback,
                    link: '#/story/' + key
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
  .story-item section {
    border: 1px solid #ddd;
    margin-bottom: 10px;
    padding: 2rem 1.5rem;
  }

  .story-item:hover section {
    background-color: #efefef;
  }
</style>
