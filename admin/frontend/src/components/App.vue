<template>
  <div>

    <section class="hero is-link" style="margin-bottom: 1.3rem;">
      <div class="hero-head">
        <nav class="navbar" role="navigation" aria-label="main navigation">
          <div class="container">

            <div class="navbar-brand">
              <router-link class="navbar-item" :to="{ name: 'home' }">
                <img :src="logoImg" style="max-height:46px;"/>
                <span class="tag is-white">{{ appName }}</span>
              </router-link>
            </div>

            <div class="navbar-menu">
              <router-link :to="{ name: 'home' }" class="navbar-item" exact-active-class="is-active">Home
              </router-link>
              <router-link :to="{ name: 'stories' }" class="navbar-item" exact-active-class="is-active">
                Stories
              </router-link>
              <router-link :to="{ name: 'runners' }" class="navbar-item" exact-active-class="is-active">
                Runners
              </router-link>
              <router-link :to="{ name: 'topics' }" class="navbar-item" exact-active-class="is-active">
                Topics
              </router-link>
              <a href="https://thenetcircle.github.io/event-bus/" target="_blank" class="navbar-item">
                Docs
              </a>
            </div>

            <bounce-loader :loading="isLoading" color="#FFFFFF"></bounce-loader>

          </div>
        </nav>
      </div>
    </section>

    <div class="container" style="margin-bottom: 1.3rem;" v-if="notification.show">
      <div class="notification" :class="notification.classname">
        <button class="delete" @click="onCloseNotification"></button>
        {{ notification.message }}
      </div>
    </div>

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
        appName: APP_NAME,
        logoImg: URL_PREFIX + '/assets/logo.png',
        isLoading: false,
        notification: {
          show: false,
          classname: 'is-success',
          message: ''
        }
      }
    },

    created() {
      bus.$on('loading', () => {
        this.isLoading = true
      })
      bus.$on('loaded', () => {
        this.isLoading = false
      })
      bus.$on('notify', (message: string, classname: string = 'is-success') => {
        this.notification = { ...this.notification, ...{ show: true, classname: classname, message: message } }
      })
    },

    watch: {
      '$route': 'onCloseNotification'
    },

    components: {
      BounceLoader
    },

    methods: {
      onCloseNotification() {
        this.notification = { ...this.notification, ...{ show: false } }
      }
    }
  })
</script>

<style>
</style>
