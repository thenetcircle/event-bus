<template>
  <div>
    <scale-loader :loading="loading" color="#3273dc" height="25px"></scale-loader>
    <iframe @load="onLoaded" :src="src" width="100%" height="200" frameborder="0"></iframe>
  </div>
</template>

<script lang="ts">
  import Vue from "vue"
  import ScaleLoader from 'vue-spinner/src/ScaleLoader.vue'

  export default Vue.extend({
    props: ['src'],

    data() {
      return {
        loading: true
      }
    },

    components: { ScaleLoader },

    methods: {
      onLoaded() {
        this.loading = false
      }
    }
  })
</script>
