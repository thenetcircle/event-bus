<template>

  <div class="container">
    <nav class="breadcrumb has-succeeds-separator" aria-label="breadcrumbs">
      <ul>
        <li>
          <router-link :to="{ name: 'home' }">Home</router-link>
        </li>
        <li>
          <router-link :to="{ name: 'stories' }">Stories</router-link>
        </li>
        <li class="is-active"><a href="#" aria-current="page">New Story</a></li>
      </ul>
    </nav>

    <div class="box story">

      <p class="title is-2 is-spaced">Create New Story</p>

      <div class="field">
        <label class="label">Story Name:</label>
        <div class="control">
          <input class="input is-primary" type="text" placeholder="StoryName" v-model="storyName">
        </div>
      </div>

      <div class="field">
        <label class="label">Story Workflow:</label>
      </div>

      <story-graph :info="storyInfo"
                   @save="onSaveStory"></story-graph>

    </div>
  </div>

</template>

<script lang="ts">
  import Vue from "vue"
  import {StoryInfo, StoryUtils} from '../lib/story-utils'
  import StoryGraph from './StoryGraph.vue'
  import request from '../lib/request'
  import bus from '../lib/bus'

  export default Vue.extend({
    data() {
      return {
        storyName: '',
        storyInfo: {
          source: {type: 'click to set', settings: '{}'},
          sink: {type: 'click to set', settings: '{}'},
          status: 'INIT',
          operators: [],
          fallback: undefined
        } as StoryInfo
      }
    },

    components: {
      StoryGraph
    },

    methods: {
      onSaveStory(newStoryInfo: StoryInfo) {
        let storyNameCheck = StoryUtils.checkStoryName(this.storyName)
        if (storyNameCheck !== true) {
          alert(storyNameCheck)
          return;
        }

        let storyInfoCheck = StoryUtils.checkStoryInfo(newStoryInfo)
        if (storyInfoCheck !== true) {
          alert(storyInfoCheck)
          return;
        }

        request.createStory(this.storyName, newStoryInfo)
          .then(() => {
            this.$router.push({name: 'story-runners', params: {storyName: this.storyName}}, () => {
              bus.$emit('notify', `new story ${this.storyName} has been created.`)
            })
          })
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
</style>
