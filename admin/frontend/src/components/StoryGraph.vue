<template>
  <div>

    <div class="field">
      <button class="button" @click="onAddTask('Add Transform', 'transform')">Add Transform</button>
      <button class="button">Add Fallback</button>
      <button class="button is-danger">Save Changes</button>
    </div>

    <div class="columns">

      <div class="column" :class="fatColumnWidthClass">
        <a class="box"
           @click="onEditTask('Source', 'source', storyInfo.source)">
          <h5 class="title is-5">{{ storyInfo.source.type }} <span
            class="tag is-success">source</span></h5>
          <div class="content">
            <pre class="settings">{{ storyInfo.source.settings | jsonPretty }}</pre>
          </div>
        </a>
      </div>

      <div class="column" :class="thinColumnWidthClass"
           v-for="(trans, index) in storyInfo.transforms">
        <a class="box"
           @click="onEditTask('Transform ' + (index + 1), 'transform', trans)">
          <h5 class="title is-5">{{ trans.type }} <span class="tag is-light">transform</span></h5>
          <div class="content">
            <pre class="settings">{{ trans.settings | jsonPretty }}</pre>
          </div>
        </a>
      </div>

      <div class="column" :class="fatColumnWidthClass">
        <a class="box"
           @click="onEditTask('Sink', 'sink', storyInfo.sink)">
          <h5 class="title is-5">{{ storyInfo.sink.type }} <span class="tag is-primary">sink</span>
          </h5>
          <div class="content">
            <pre class="settings">{{ storyInfo.sink.settings | jsonPretty }}</pre>
          </div>
        </a>
      </div>

      <div class="column" :class="thinColumnWidthClass" v-if="storyInfo.fallback">
        <a class="box"
           @click="onEditTask('Fallback', 'fallback', storyInfo.fallback)">
          <h5 class="title is-5">{{ storyInfo.fallback.type }} <span
            class="tag is-warning">fallback</span></h5>
          <div class="content">
            <pre class="settings">{{ storyInfo.fallback.settings | jsonPretty }}</pre>
          </div>
        </a>
      </div>

    </div>

    <transition>
      <task-editor v-if="taskEditor.show" v-bind="taskEditor" @close="onTaskEditorClose"
                   @save="onTaskEditorSave"/>
    </transition>
  </div>
</template>

<script lang="ts">
  import Vue from "vue"
  import {
    StoryInfo,
    StoryStatus,
    StoryTask,
    TaskEditAction,
    TaskEditType
  } from '../lib/story-utils';
  import TaskEditor from "./TaskEditor.vue"

  export default Vue.extend({
    props: ['info'],

    data() {
      return {
        changed: false,
        storyInfo: this.info,
        taskEditor: { show: false, title: '', action: {} as TaskEditAction }
      }
    },

    computed: {
      fatColumnWidthClass(): string {
        let eleNum = 2 + this.storyInfo.transforms.length + (this.storyInfo.fallback ? 1 : 0)
        let each = Math.ceil(12 / eleNum)
        return `is-${each}`
      },
      thinColumnWidthClass(): string {
        let eleNum = 2 + this.storyInfo.transforms.length + (this.storyInfo.fallback ? 1 : 0)
        let each = Math.floor(12 / eleNum)
        return `is-${each}`
      }
    },

    components: {
      TaskEditor,
    },

    filters: {
      jsonPretty: function (json: string): string {
        return JSON.stringify(JSON.parse(json), undefined, 2);
      }
    },

    methods: {
      onAddTask(title: string, category: string) {
        this.taskEditor = {
          ...this.taskEditor, ...{
            show: true,
            title: title,
            action: new TaskEditAction(TaskEditType.ADD, category, {} as StoryTask)
          }
        }
      },

      onEditTask(title: string, category: string, task: StoryTask) {
        this.taskEditor = {
          ...this.taskEditor, ...{
            show: true,
            title: title,
            action: new TaskEditAction(TaskEditType.EDIT, category, task)
          }
        }
      },

      onTaskEditorClose() {
        this.taskEditor = {...this.taskEditor, ...{show: false}}
      },

      onTaskEditorSave(action: TaskEditAction, newTask: StoryTask) {

        if (action.type == TaskEditType.ADD) {

          console.log(action.task)
          switch (action.taskCategory)
          {
            case 'transform':
              this.storyInfo.transforms.push(newTask)
          }
        }
        else {
          if (action.task) {
            action.task.type = newTask.type
            action.task.settings = newTask.settings
            this.changed = true

            console.log('updated storyInfo: ', JSON.stringify(this.storyInfo))
          }
        }

        this.onTaskEditorClose()

      }
    }
  })

</script>
