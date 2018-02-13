<template>
  <div>

    <section class="hero is-link" style="margin-bottom: 28px;">
      <div class="hero-head">
        <nav class="navbar" role="navigation" aria-label="main navigation">
          <div class="container">

            <div class="navbar-brand">
              <a class="navbar-item" href="/"><img src="/assets/logo.png" style="max-height:46px;"/></a>
            </div>

            <div class="navbar-menu">
              <router-link to="/" class="navbar-item" exact-active-class="is-active">Home
              </router-link>
              <router-link to="/stories" class="navbar-item" exact-active-class="is-active">
                Stories
              </router-link>
              <router-link to="/runners" class="navbar-item" exact-active-class="is-active">
                Runners
              </router-link>
              <router-link to="/topics" class="navbar-item" exact-active-class="is-active">
                Topics
              </router-link>
              <router-link to="/topics" class="navbar-item" exact-active-class="is-active">
                Docs
              </router-link>
            </div>

            <bounce-loader :loading="isLoading" color="#FFFFFF"></bounce-loader>

          </div>
        </nav>
      </div>
    </section>

    <transition name="fade">
      <router-view></router-view>
    </transition>

  </div>
</template>

<script lang="ts">
  import Vue from "vue"
  import bus from "../lib/bus"
  import BounceLoader from 'vue-spinner/src/BounceLoader.vue'

  export default Vue.extend({
    data() {
      return {
        name: "app",
        isLoading: false
      }
    },

    created() {
      bus.$on('loading', () => {
        this.isLoading = true
      })
      bus.$on('loaded', () => {
        this.isLoading = false
      })
    },

    components: {
      BounceLoader
    }
  })
</script>

<style>
</style>
