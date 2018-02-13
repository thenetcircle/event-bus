<template>

  <div class="container box story">

    <p class="title is-2 is-spaced">Create New Story</p>

    <div class="field">
      <label class="label">Story Name:</label>
      <div class="control">
        <input class="input is-primary" type="text" placeholder="StoryName" v-model="storyName">
      </div>
    </div>

    <div class="field">
      <label class="label">Story Graph:</label>
    </div>

    <story-graph :info="storyInfo"
                 @save="onSaveStory"></story-graph>

  </div>

</template>

<script lang="ts">
  import Vue from "vue"
  import {StoryInfo, StoryUtils} from '../lib/story-utils';
  import StoryGraph from './StoryGraph.vue'

  export default Vue.extend({
    data() {
      return {
        storyName: '',
        storyInfo: {
          source: {type: 'http', settings: '{}'},
          sink: {type: 'kafka', settings: '{}'},
          status: 'INIT',
          transforms: [],
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

        // save new story
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
