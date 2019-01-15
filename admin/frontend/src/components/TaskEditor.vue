import {OperatorPosition} from '../lib/story-utils';
import {OperatorPosition} from '../lib/story-utils';
<template>

  <div class="modal is-active">
    <div class="modal-background"></div>
    <div class="modal-card">
      <header class="modal-card-head">
        <p class="modal-card-title">{{ title }}</p>
        <button class="delete" @click.prevent="close()" aria-label="close"></button>
      </header>
      <section class="modal-card-body">
        <div class="select" style="margin-bottom: 1rem;">
          <select @change="onTypeChanged" v-model="currTaskType">
            <option value="">Choose Type</option>
            <option v-for="item in supportedTypes" :value="item">{{ item }}</option>
          </select>
        </div>

        <div id="editor"><!-- Content ... --></div>
      </section>
      <footer class="modal-card-foot">
        <button class="button is-success" @click.prevent="save()">Save changes</button>
        <button class="button" @click.prevent="close()">Cancel</button>
      </footer>
    </div>
  </div>

</template>

<script lang="ts">
  import Vue from "vue"
  import taskSchema from "../lib/task-schema"
  import {OpExecPos, StoryOperator, StoryTask, TaskEditAction} from '../lib/story-utils';

  export default Vue.extend({
    props: ['title', 'action'],

    data() {
      return {
        editor: {} as any,
        currTaskType: ''
      }
    },

    mounted() {
      let action: TaskEditAction = this.action
      let schema = taskSchema[this.action.taskCategory]
      if (action.task.type && schema[action.task.type]) {
        this.currTaskType = action.task.type
        this.renderJSONEditor(schema[action.task.type], JSON.parse(action.task.settings))
      }
    },

    computed: {
      supportedTypes(): string[] {
        return Object.keys(taskSchema[this.action.taskCategory])
      }
    },

    methods: {
      close() {
        this.$emit('close')
      },

      save() {
        let newTask: StoryTask
        if (this.action.taskCategory == 'operator') {
          newTask = <StoryOperator>{
            type: this.currTaskType,
            settings: JSON.stringify(this.editor.getValue()),
            execPos: taskSchema[this.action.taskCategory][this.currTaskType]['direction'] == 'bidi' ? OpExecPos.BiDi : (this.action.task.execPos || OpExecPos.Before)
          }
        }
        else {
          newTask = {
            type: this.currTaskType,
            settings: JSON.stringify(this.editor.getValue())
          }
        }
        this.$emit('save', this.action, newTask)
      },

      onTypeChanged() {
        this.renderJSONEditor(taskSchema[this.action.taskCategory][this.currTaskType])
      },

      renderJSONEditor(schema: any, startval?: any) {
        if (this.editor instanceof JSONEditor) {
          this.editor.destroy()
        }

        let ele = <HTMLElement>document.getElementById('editor')
        let options = {
          'theme': 'eventbus' as any,
          'disable_collapse': true,
          'startval': startval,
          /*'disable_edit_json': true,
          'disable_properties': true,*/
          'schema': schema || {},
          'display_required_only': true
        }
        this.editor = new JSONEditor(ele, options)
      }
    }
  })
</script>

<style>
  .editor {
    min-height: 100rem;
  }
</style>
