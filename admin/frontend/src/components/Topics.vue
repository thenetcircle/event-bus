<template>

  <div class="container">
    <div class="is-clearfix" style="margin-bottom: 1rem">
      <div class="is-pulled-right">
        <a class="button is-info" @click="onSave">Update</a>
        <a class="button" @click="onReset">Reset To Default</a>
      </div>
    </div>

    <div class="box" id="topics_box">
      <textarea class="textarea" rows="25" v-model="topics"></textarea>
    </div>

    <confirmation-box v-if="confirmation.show" v-bind="confirmation" @confirm="onConfirm"
                      @cancel="onNotConfirm"></confirmation-box>
  </div>

</template>

<script lang="ts">
  import Vue from "vue"
  import topicSchema from '../lib/topic-schema'
  import request from '../lib/request'
  import ConfirmationBox from './ConfirmationBox.vue'
  import bus from '../lib/bus'
  import {StoryUtils} from "../lib/story-utils";

  export default Vue.extend({
    data() {
      return {
        topics: '{}',
        editor: {} as any,
        confirmation: {
          show: false,
          title: '',
          message: ''
        }
      }
    },

    watch: {
      '$route': 'fetchData'
    },

    components: {
      ConfirmationBox
    },

    created() {
      this.fetchData()
    },

    methods: {
      fetchData(): void {
        request.getTopics().then(topics => {
          if (topics) {
            this.topics = StoryUtils.jsonPretty(topics, 4)
          }
          // this.renderJSONEditor()
        })
      },

      renderJSONEditor(): void {
        if (this.editor instanceof JSONEditor) {
          this.editor.destroy()
        }

        let ele = <HTMLElement>document.getElementById('topics_box')
        let options = {
          'theme': 'eventbus' as any,
          // 'disable_collapse': true,
          'startval': JSON.parse(this.topics),
          // 'disable_edit_json': true,
          'disable_properties': true,
          // 'display_required_only': true,
          'schema': topicSchema || {}
        }
        this.editor = new JSONEditor(ele, options)
      },

      onSave(): void {
        try {
          let message = StoryUtils.jsonPretty(this.topics);

          this.confirmation = {
            ...this.confirmation, ...{
              show: true,
              title: 'Are you sure to update the topics?',
              message: `<article class="content"><pre>${message}</pre></article>`
            }
          }
        }
        catch(e) {
          alert('Save topics failed! Reason: ' + e.message)
        }
      },

      onConfirm(): void {
        let topics = JSON.stringify(JSON.parse(this.topics))

        request.updateTopics(topics)
          .then(() => {
            bus.$emit('notify', 'The topics are updated!')
            this.onNotConfirm()
          })
      },

      onNotConfirm(): void {
        this.confirmation = {...this.confirmation, ...{show: false}}
      },

      onReset(): void {
        if (confirm('Are you sure to reset to default?')) {
          this.fetchData()
        }
      }
    }
  })

</script>
