<template>

  <div class="modal is-active">
    <div class="modal-background"></div>
    <div class="modal-card">
      <header class="modal-card-head">
        <p class="modal-card-title">{{ title }}</p>
        <button class="delete" @click.prevent="close()" aria-label="close"></button>
      </header>
      <section class="modal-card-body" id="editor">
        <!-- Content ... -->
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

  export default Vue.extend({
    props: ['title', 'tasktype', 'type', 'settings'],

    data() {
      return {
        editor: {} as any
      }
    },

    mounted() {
      let ele = <HTMLElement>document.getElementById('editor')

      let options = {
        'theme': 'html' as any,
        'startval': JSON.parse(this.settings),
        'disable_collapse': true,
        /*'disable_edit_json': true,
        'disable_properties': true,*/
        'schema': taskSchema[this.tasktype][this.type] || {},
        'display_required_only': false
      }
      this.editor = new JSONEditor(ele, options)
    },

    methods: {
      close() {
        this.$emit('close')
      },

      save() {
        this.$emit('save', JSON.stringify(this.editor.getValue()))
      }
    }
  })
</script>
