<template>
  <div>

    <div class="is-clearfix" style="margin-bottom: 1rem;">

      <div class="field is-pulled-right">
        <button class="button" @click="onAddTask('Add a Transform', 'transform')">Add Transform
        </button>
        <button class="button" v-if="!storyInfo.fallback"
                @click="onAddTask('Add a Fallback', 'fallback')">Add Fallback
        </button>
        <button class="button is-info" v-if="changed">Save Changes</button>
        <button class="button is-danger" v-if="changed" @click="onResetChanges">Reset</button>
      </div>

    </div>

    <div class="box">

      <div class="columns">

        <div class="column" :class="fatColumnClass">
          <a class="box"
             @click="onEditTask('Source', 'source', storyInfo.source)">
            <h5 class="title is-5">{{ storyInfo.source.type }} <span
              class="tag is-success">source</span></h5>
            <div class="content">
              <pre class="settings">{{ storyInfo.source.settings | jsonPretty }}</pre>
            </div>
          </a>
        </div>

        <div class="column" :class="thinColumnClass"
             v-for="(trans, index) in storyInfo.transforms">
          <a class="box"
             @click="onEditTask('Transform ' + (index + 1), 'transform', trans)">

            <h5 class="title is-5">
              {{ trans.type }}
              <span class="tag is-light">transform</span>
              <a class="icon" @click.stop="onRemoveTask('transform', index)"><i class="fas fa-trash-alt"></i></a>
            </h5>

            <div class="content">
              <pre class="settings">{{ trans.settings | jsonPretty }}</pre>
            </div>
          </a>
        </div>

        <div class="column" :class="fatColumnClass">
          <a class="box"
             @click="onEditTask('Sink', 'sink', storyInfo.sink)">
            <h5 class="title is-5">{{ storyInfo.sink.type }} <span
              class="tag is-primary">sink</span>
            </h5>
            <div class="content">
              <pre class="settings">{{ storyInfo.sink.settings | jsonPretty }}</pre>
            </div>
          </a>
        </div>

      </div>

      <hr/>

      <div class="columns" v-if="storyInfo.fallback">
        <div class="column"></div>
        <div class="column" :class="fatColumnClass">
          <a class="box"
             @click="onEditTask('Fallback', 'fallback', storyInfo.fallback)">
            <h5 class="title is-5">
              {{ storyInfo.fallback.type }}
              <span class="tag is-warning">fallback</span>
              <a class="icon" @click.stop="onRemoveTask('fallback')"><i class="fas fa-trash-alt"></i></a>
            </h5>

            <div class="content">
              <pre class="settings">{{ storyInfo.fallback.settings | jsonPretty }}</pre>
            </div>
          </a>
        </div>
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
    TaskEditType,
    StoryUtils
  } from '../lib/story-utils';
  import TaskEditor from "./TaskEditor.vue"

  export default Vue.extend({
    props: ['info'],

    data() {
      return {
        changed: false,
        storyInfo: StoryUtils.copyStoryInfo(this.info),
        taskEditor: {show: false, title: '', action: {} as TaskEditAction}
      }
    },

    computed: {
      fatColumnClass(): string {
        let eleNum = 2 + this.storyInfo.transforms.length
        let each = Math.ceil(12 / eleNum)
        return `is-${each}`
      },
      thinColumnClass(): string {
        let eleNum = 2 + this.storyInfo.transforms.length
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

      onRemoveTask(category: string, index?: number) {
        switch (category)
        {
          case 'transform':
            if (index != undefined && this.storyInfo.transforms[index]) {
              console.log(this.storyInfo.transforms)
              this.storyInfo.transforms.splice(index, 1)
              console.log(this.storyInfo.transforms)
              this.changed = true
            }
            break;

          case 'fallback':
            this.storyInfo.fallback = undefined
            this.changed = true
            break;
        }
      },

      onTaskEditorClose() {
        this.taskEditor = {...this.taskEditor, ...{show: false}}
      },

      onTaskEditorSave(action: TaskEditAction, newTask: StoryTask) {

        if (action.type == TaskEditType.ADD) {
          switch (action.taskCategory) {
            case 'transform':
              this.storyInfo.transforms.push(newTask)
              break;
            case 'fallback':
              this.storyInfo.fallback = newTask
              break;
          }
        }
        else {
          if (action.task) {
            action.task.type = newTask.type
            action.task.settings = newTask.settings
          }
        }

        this.changed = true
        console.log('updated storyInfo: ', JSON.stringify(this.storyInfo))

        this.onTaskEditorClose()

      },

      onResetChanges() {
        this.storyInfo = StoryUtils.copyStoryInfo(this.info)
        this.changed = false
      }

    }
  })

</script>

<style>
  a.icon {
    color: black;
  }
  a.icon:hover {
    color: red;
  }

</style>
