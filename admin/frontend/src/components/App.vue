<template>
  <div>

    <section class="hero is-link" style="margin-bottom: 10px;">
      <div class="hero-head">
        <nav class="navbar" role="navigation" aria-label="main navigation">
          <div class="container">

            <div class="navbar-brand">
              <a class="navbar-item" href="/"><img src="/logo.png" style="max-height:46px;" /></a>
            </div>

            <div class="navbar-menu">
              <router-link to="/" class="navbar-item" exact-active-class="is-active">Dashboard
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
            </div>

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

  export default Vue.extend({
    data() {
      return {
        name: "app"
      }
    }
  })
</script>

<style>
</style>
