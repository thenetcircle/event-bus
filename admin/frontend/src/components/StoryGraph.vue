<template>
  <div>

    <div style="margin-bottom: 1rem;">

      <div class="level">
        <div class="level-left">
          <div class="level-item">
            <button class="button" @click="onAddTask('Add a Transform', 'transform')">
              Add Transform
            </button>
          </div>
          <div class="level-item" v-if="!storyInfo.fallback">
            <button class="button" @click="onAddTask('Add a Fallback', 'fallback')">Add Fallback
            </button>
          </div>
        </div>
        <div class="level-right">
          <div class="level-item" v-if="changed">
            <button class="button is-info" @click="onSaveStory">Save</button>
          </div>
          <div class="level-item" v-if="changed">
            <button class="button is-light" @click="onResetChanges">Reset</button>
          </div>
        </div>

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
              <a class="icon" @click.stop="onRemoveTask('transform', index)"><i
                class="fas fa-trash-alt"></i></a>
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

      <div class="arrow"><div></div><span></span></div>

      <div class="columns" v-if="storyInfo.fallback">
        <div class="column" style="text-align: right"><div style="margin-top: 5rem; color: #ccc;"> ------ Failed Events ----> </div></div>
        <div class="column" :class="fatColumnClass">
          <a class="box"
             @click="onEditTask('Fallback', 'fallback', storyInfo.fallback)">
            <h5 class="title is-5">
              {{ storyInfo.fallback.type }}
              <span class="tag is-warning">fallback</span>
              <a class="icon" @click.stop="onRemoveTask('fallback')"><i
                class="fas fa-trash-alt"></i></a>
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

    <div class="modal" :class="{ 'is-active': confirmation.show }">
      <div class="modal-background"></div>
      <div class="modal-card">
        <header class="modal-card-head">
          <p class="modal-card-title">{{ confirmation.title }}</p>
          <button class="delete" @click.prevent="onNotConfirm()" aria-label="close"></button>
        </header>
        <section class="modal-card-body" v-html="confirmation.message"></section>
        <footer class="modal-card-foot">
          <button class="button is-info" @click.prevent="onConfirm()">Yes</button>
          <button class="button" @click.prevent="onNotConfirm()">No</button>
        </footer>
      </div>
    </div>

  </div>
</template>

<script lang="ts">
  import Vue from "vue"
  import {StoryTask, StoryUtils, TaskEditAction, TaskEditType} from '../lib/story-utils';
  import TaskEditor from "./TaskEditor.vue"

  export default Vue.extend({
    props: ['info'],

    data() {
      return {
        changed: false,
        storyInfo: StoryUtils.copyStoryInfo(this.info),
        taskEditor: {show: false, title: '', action: {} as TaskEditAction},
        confirmation: {
          show: false,
          title: '',
          message: ''
        }
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
      jsonPretty: StoryUtils.jsonPretty
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
        if (!confirm(`Are you sure delete this ${category}?`)) {
          return;
        }

        switch (category) {
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
        if (!confirm('Are you sure to reset everything?')) {
          return;
        }
        this.storyInfo = StoryUtils.copyStoryInfo(this.info)
        this.changed = false
      },

      onSaveStory() {
        let message = StoryUtils.jsonPretty(JSON.stringify(this.storyInfo));
        this.confirmation = {
          ...this.confirmation, ...{
            show: true,
            title: 'Are you sure to save the Story?',
            message: `<article class="content"><pre>${message}</pre></article>`
          }
        }
      },

      onConfirm() {
        this.$emit('save', this.storyInfo)
        // this.changed = false
        this.onNotConfirm()
      },

      onNotConfirm() {
        this.confirmation = {...this.confirmation, ...{show: false}}
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

  .arrow {
    /*clear: both;
    border-color: #ccc;
    border-style: dashed;
    border-width: 0.05em;*/
    margin-bottom: 1.5rem;
    position: relative;
  }

  .arrow div {
    height: 5px;
    border-bottom: 2px dashed #ccc;
  }

  .arrow span {
    border-style: dashed;
    border-color: transparent;
    border-width: 0.08em;
    display: -moz-inline-box;
    display: inline-block;
    /* Use font-size to control the size of the arrow. */
    font-size: 80px;
    height: 0;
    line-height: 0;
    position: absolute;
    top: -2px;
    right: -10px;
    vertical-align: middle;
    width: 0;
    background-color: #fff; /* change background color acc to bg color */
    border-left-width: 0.15em;
    border-left-style: solid;
    border-left-color: #ccc;
  }

</style>
