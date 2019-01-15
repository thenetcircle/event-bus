import {OpExecPos} from "../lib/story-utils";
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

      <div class="box" style="overflow-x: auto;">

        <div class="columns is-vcentered">

          <div class="column is-narrow">
            <a class="box"
               @click="onEditTask('Source', 'source', storyInfo.source)">
              <h5 class="title is-5" id="source">{{ storyInfo.source.type }} <span class="tag is-success">Source</span>
              </h5>
              <div class="content">
                <pre class="settings" style="overflow: auto; max-width: 18rem; height: 15rem;">{{ storyInfo.source.settings | jsonPretty }}</pre>
              </div>
            </a>
          </div>

          <div class="column">

            <div class="columns">
              <div class="column is-narrow" :class="op.execPos" v-for="(op, index) in storyInfo.operators">

                <a class="box"
                   @click="onEditTask('Operator ' + (index + 1), 'operator', op)">

                  <h5 class="title is-5" :id="'operator' + index">
                    {{ op.type }}
                    <span class="tag is-light">Operator {{index + 1}}</span>
                  </h5>

                  <div class="columns">
                    <div class="column">
                      <span class="tag" :class="isBiDiOp(op) ? 'is-link' : (isAfterOp(op) ? 'is-warning' : 'is-dark')">{{op.execPos}}</span>
                    </div>

                    <div class="column is-narrow">
                      <a class="icon" v-show="index > 0"
                         @click.stop="onChangeOperatorPos(index, 'left')"><i
                        class="fas fa-arrow-left"></i></a>
                      <a class="icon" v-show="isAfterOp(op)"
                         @click.stop="onChangeOperatorPos(index, 'up')"><i
                        class="fas fa-arrow-up"></i></a>
                      <a class="icon" v-show="isBeforeOp(op)"
                         @click.stop="onChangeOperatorPos(index, 'down')"><i
                        class="fas fa-arrow-down"></i></a>
                      <a class="icon" v-show="index + 1 < storyInfo.operators.length"
                         @click.stop="onChangeOperatorPos(index, 'right')"><i
                        class="fas fa-arrow-right"></i></a>
                    </div>

                    <div class="column is-narrow">
                      <a class="icon" @click.stop="onRemoveTask('operator', index)"><i
                        class="fas fa-trash-alt"></i></a>
                    </div>
                  </div>

                  <div class="content">
                    <pre class="settings">{{ op.settings | jsonPretty }}</pre>
                  </div>

                </a>

              </div>
            </div>

          </div>

          <div class="column is-narrow">
            <a class="box"
               @click="onEditTask('Sink', 'sink', storyInfo.sink)">
              <h5 class="title is-5" id="sink">{{ storyInfo.sink.type }} <span class="tag is-primary">Sink</span></h5>
              <div class="content">
                <pre class="settings" style="overflow: auto; max-width: 18rem; height: 15rem;">{{ storyInfo.sink.settings | jsonPretty }}</pre>
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
  import {OpExecPos, StoryOperator, StoryTask, StoryUtils, TaskEditAction, TaskEditType} from '../lib/story-utils';
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

    mounted() {
      /*let lines: LeaderLine[] = [];
      let preLineOption = {dash: {animation: true}, color: 'rgba(75, 192, 192)'}
      let postLineOption = {dash: {animation: true}, color: 'red'}
      let previousTask = 'source';

      this.storyInfo.operators.forEach((ops, index) => {
        if (ops.position == 'pre' || ops.position == 'both') {
          let nextTask = 'operator' + index
          lines.push(new LeaderLine(
            document.getElementById(previousTask),
            document.getElementById(nextTask),
            preLineOption
          ))
          previousTask = nextTask
        }
      })

      lines.push(new LeaderLine(
        document.getElementById(previousTask),
        document.getElementById('sink'),
        preLineOption
      ))
      previousTask = 'sink'

      for (let i = this.storyInfo.operators.length - 1; i >= 0; i--) {
        let ops = this.storyInfo.operators[i]
        if (ops.position == 'post' || ops.position == 'both') {
          let nextTask = 'operator' + i
          lines.push(new LeaderLine(
            document.getElementById(previousTask),
            document.getElementById(nextTask),
            postLineOption
          ))
          previousTask = nextTask
        }
      }
      lines.push(new LeaderLine(
        document.getElementById(previousTask),
        document.getElementById('source'),
        postLineOption
      ))*/
    },

    computed: {},

    components: {
      TaskEditor,
      ConfirmationBox
    },

    filters: {
      jsonPretty: StoryUtils.jsonPretty
    },

    methods: {
      isBeforeOp(op: StoryOperator): boolean {
        return op.execPos == OpExecPos.Before
      },
      isAfterOp(op: StoryOperator): boolean {
        return op.execPos == OpExecPos.After
      },
      isBiDiOp(op: StoryOperator): boolean {
        return op.execPos == OpExecPos.BiDi
      },
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
        let operator: StoryOperator = this.storyInfo.operators[index]
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
            if (operator.execPos != OpExecPos.BiDi) {
              operator.execPos = OpExecPos.Before;
            }
            break;
          case 'down':
            if (operator.execPos != OpExecPos.BiDi) {
              operator.execPos = OpExecPos.After;
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
        } else {
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

  .column.before {
    /*padding-top: 20px;*/
  }
  .column.before a.box .content .settings
  {
    overflow: auto; max-width: 16rem; height: 3rem;
  }


  /*a.box.bidi {
    margin-top: 50px;
  }*/
  .column.bidi a.box .content .settings
  {
    overflow: auto; max-width: 16rem; height: 10rem;
  }

  .column.after {
    padding-top: 7.8rem;
  }
  .column.after a.box .content .settings
  {
    overflow: auto; max-width: 16rem; height: 3rem;
  }

</style>
