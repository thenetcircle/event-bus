<template>
  <div class="modal is-active">
    <div class="modal-background"></div>
    <div class="modal-card">
      <header class="modal-card-head">
        <p class="modal-card-title">{{ title }}</p>
        <button class="delete" @click.prevent="$emit('cancel')" aria-label="close"></button>
      </header>
      <section class="modal-card-body" v-html="message"></section>
      <footer class="modal-card-foot">
        <button class="button is-info" @click.prevent="$emit('confirm')">Yes</button>
        <button class="button" @click.prevent="$emit('cancel')">No</button>
      </footer>
    </div>
  </div>
</template>

<script lang="ts">
  import Vue from "vue"

  export default Vue.extend({
    props: ['title', 'message']
  })
</script>
