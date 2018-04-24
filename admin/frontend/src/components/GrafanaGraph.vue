<template>
  <div>
    <iframe @load="onLoaded" :src="src" width="100%" height="200" frameborder="0"></iframe>
  </div>
</template>

<script lang="ts">
  import Vue from "vue"

  export default Vue.extend({
    props: ['src'],

    data() {
      return {
        loading: true
      }
    },

    methods: {
      onLoaded() {
        this.loading = false
      }
    }
  })
</script>
