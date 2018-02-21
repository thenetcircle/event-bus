<template>

  <div class="container">
    <div class="is-clearfix" style="margin-bottom: 1rem">
      <div class="is-pulled-right">
        <a class="button is-info" @click="onSave">Update</a>
        <a class="button" @click="onReset">Reset To Default</a>
      </div>
    </div>

    <div class="box" id="topics_box"></div>

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
          this.topics = topics
          this.renderJSONEditor()
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
        let message = StoryUtils.jsonPretty(JSON.stringify(this.editor.getValue()));
        this.confirmation = {
          ...this.confirmation, ...{
            show: true,
            title: 'Are you sure to update the topics?',
            message: `<article class="content"><pre>${message}</pre></article>`
          }
        }
      },

      onConfirm(): void {
        request.updateTopics(JSON.stringify(this.editor.getValue()))
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
