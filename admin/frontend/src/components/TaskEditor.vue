<template>

  <div :class="modalClass">
    <div class="modal-background"></div>
    <div class="modal-card">
      <header class="modal-card-head">
        <p class="modal-card-title">{{ title }}</p>
        <button class="delete" aria-label="close"></button>
      </header>
      <section class="modal-card-body">
        <!-- Content ... -->
      </section>
      <footer class="modal-card-foot">
        <button class="button is-success">Save changes</button>
        <button class="button">Cancel</button>
      </footer>
    </div>
  </div>

</template>

<script lang="ts">
  import Vue from "vue"

  export default Vue.extend({
    props: ['show', 'title', 'type', 'data'],

    computed: {
      modalClass(): string {
        return this.show ? 'modal is-active' : 'modal'
      }
    }
  })
</script>
