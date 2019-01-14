<template>
  <div>

    <div style="margin-bottom: 1rem;">

      <div class="level">
        <div class="level-left">
          <div class="level-item">
            <button class="button" @click="onAddTask('Add a Operator', 'operator')">
              Add a Operator
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

      <div class="box">

        <div class="columns is-vcentered" style="overflow-x: auto;">

          <div class="column" style="width: 22rem;">
            <a class="box"
               @click="onEditTask('Source', 'source', storyInfo.source)">
              <h5 class="title is-5">Source <span
                class="tag is-success">{{ storyInfo.source.type }}</span></h5>
              <div class="content">
                <pre class="settings">{{ storyInfo.source.settings | jsonPretty }}</pre>
              </div>
            </a>
          </div>

          <div class="column">

            <!-- pre operators -->
            <div class="columns">
              <div class="column" v-for="(ops, index) in storyInfo.operators" style="width: 20rem;">

                <a class="box" v-if="ops.position === 'pre' || ops.position === 'both'"
                   @click="onEditTask('Operator ' + (index + 1), 'operator', ops)">

                  <h5 class="title is-5">
                    {{ ops.type }}
                    <span class="tag is-light">[{{ops.position}}] Operator {{ index + 1 }}</span>
                  </h5>

                  <div class="columns">
                    <div class="column">
                      <a class="icon" v-show="index > 0" @click.stop="onChangeOperatorPos(index, 'left')"><i
                        class="fas fa-arrow-left"></i></a>
                      <a class="icon" v-show="ops.position === 'post'" @click.stop="onChangeOperatorPos(index, 'up')"><i
                        class="fas fa-arrow-up"></i></a>
                      <a class="icon" v-show="ops.position === 'pre'" @click.stop="onChangeOperatorPos(index, 'down')"><i
                        class="fas fa-arrow-down"></i></a>
                      <a class="icon" v-show="index + 1 < storyInfo.operators.length" @click.stop="onChangeOperatorPos(index, 'right')"><i
                        class="fas fa-arrow-right"></i></a>
                    </div>

                    <div class="column is-narrow">
                      <a class="icon" @click.stop="onRemoveTask('operator', index)"><i
                        class="fas fa-trash-alt"></i></a>
                    </div>
                  </div>

                  <div class="content">
                    <pre class="settings"
                         style="height: 6rem;">{{ ops.settings | jsonPretty }}</pre>
                  </div>

                </a>

              </div>
            </div>

            <div class="arrow-group">
              <div class="arrow-right">
                <div></div>
                <span></span></div>
              <div class="arrow-left"><span></span>
                <div></div>
              </div>
            </div>

            <!-- post operators -->
            <div class="columns">
              <div class="column" v-for="(ops, index) in storyInfo.operators" style="width: 20rem;">

                <a class="box" v-if="ops.position === 'post'"
                   @click="onEditTask('Operator ' + (index + 1), 'operator', ops)">

                  <h5 class="title is-5">
                    {{ ops.type }}
                    <span class="tag is-light">[{{ops.position}}] Operator {{ index + 1 }}</span>
                  </h5>

                  <div class="columns">
                    <div class="column">
                      <a class="icon" v-show="index > 0" @click.stop="onChangeOperatorPos(index, 'left')"><i
                        class="fas fa-arrow-left"></i></a>
                      <a class="icon" v-show="ops.position === 'post'" @click.stop="onChangeOperatorPos(index, 'up')"><i
                        class="fas fa-arrow-up"></i></a>
                      <a class="icon" v-show="ops.position === 'pre'" @click.stop="onChangeOperatorPos(index, 'down')"><i
                        class="fas fa-arrow-down"></i></a>
                      <a class="icon" v-show="index + 1 < storyInfo.operators.length" @click.stop="onChangeOperatorPos(index, 'right')"><i
                        class="fas fa-arrow-right"></i></a>
                    </div>

                    <div class="column is-narrow">
                      <a class="icon" @click.stop="onRemoveTask('operator', index)"><i
                        class="fas fa-trash-alt"></i></a>
                    </div>
                  </div>

                  <div class="content">
                    <pre class="settings"
                         style="height: 6rem;">{{ ops.settings | jsonPretty }}</pre>
                  </div>

                </a>

              </div>
            </div>

          </div>

          <div class="column" style="width: 22rem;">
            <a class="box"
               @click="onEditTask('Sink', 'sink', storyInfo.sink)">
              <h5 class="title is-5">Sink <span
                class="tag is-primary">{{ storyInfo.sink.type }}</span>
              </h5>
              <div class="content">
                <pre class="settings">{{ storyInfo.sink.settings | jsonPretty }}</pre>
              </div>
            </a>
          </div>

        </div>

      </div>

    </div>

    <transition>
      <task-editor v-if="taskEditor.show" v-bind="taskEditor" @close="onTaskEditorClose"
                   @save="onTaskEditorSave"/>
    </transition>

    <confirmation-box v-if="confirmation.show" v-bind="confirmation" @confirm="onConfirm"
                      @cancel="onNotConfirm"></confirmation-box>

  </div>
</template>

<script lang="ts">
  import Vue from "vue"
  import {
    OperatorPosition,
    StoryOperator,
    StoryTask,
    StoryUtils,
    TaskEditAction,
    TaskEditType
  } from '../lib/story-utils';
  import TaskEditor from "./TaskEditor.vue"
  import ConfirmationBox from './ConfirmationBox.vue'

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
    },

    components: {
      TaskEditor,
      ConfirmationBox
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
          case 'operator':
            if (index != undefined && this.storyInfo.operators[index]) {
              console.log(this.storyInfo.operators)
              this.storyInfo.operators.splice(index, 1)
              console.log(this.storyInfo.operators)
              this.changed = true
            }
            break;
        }
      },

      onChangeOperatorPos(index: number, direction: String) {
        let operator = this.storyInfo.operators[index]
        let newOperators: StoryOperator[] = []
        switch (direction) {
          case 'left':
            if (index > 0) {
              this.storyInfo.operators.forEach(ops => newOperators.push(ops))
              let movedOperator = newOperators[index - 1];
              newOperators[index - 1] = newOperators[index];
              newOperators[index] = movedOperator;
              this.storyInfo.operators = newOperators
            }
            break;
          case 'right':
            if (index + 1 < this.storyInfo.operators.length) {
              this.storyInfo.operators.forEach(ops => newOperators.push(ops))
              let movedOperator = newOperators[index + 1];
              newOperators[index + 1] = newOperators[index];
              newOperators[index] = movedOperator;
              this.storyInfo.operators = newOperators
            }
            break;
          case 'up':
            if (operator.position != OperatorPosition.BOTH) {
              operator.position = OperatorPosition.PRE;
            }
            break;
          case 'down':
            if (operator.position != OperatorPosition.BOTH) {
              operator.position = OperatorPosition.POST;
            }
            break;
        }
        this.changed = true
      },

      onTaskEditorClose() {
        this.taskEditor = {...this.taskEditor, ...{show: false}}
      },

      onTaskEditorSave(action: TaskEditAction, newTask: StoryTask) {

        if (action.type == TaskEditType.ADD) {
          switch (action.taskCategory) {
            case 'operator':
              this.storyInfo.operators.push(<StoryOperator>newTask)
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
        this.changed = false
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

  .arrow-gropu {
  }

  .arrow-right, .arrow-left {
    /*clear: both;
    border-color: #ccc;
    border-style: dashed;
    border-width: 0.05em;*/
    margin-bottom: 1rem;
    position: relative;
  }

  .arrow-right div, .arrow-left div {
    height: 5px;
    border-bottom: 2px dashed rgb(75, 192, 192);
  }

  .arrow-right span {
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
    border-left-color: rgb(75, 192, 192);
  }

  .arrow-left span {
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
    left: -10px;
    vertical-align: middle;
    width: 0;
    background-color: #fff; /* change background color acc to bg color */
    border-right-width: 0.15em;
    border-right-style: solid;
    border-right-color: rgb(75, 192, 192);
  }

</style>
