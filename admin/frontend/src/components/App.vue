<template>
  <el-container>

    <el-header>
      <el-menu :default-active="activeIndex" class="el-menu-demo" mode="horizontal" router="true">
        <el-menu-item index="/">Dashboard</el-menu-item>
        <el-menu-item index="/stories">Stories</el-menu-item>
        <el-menu-item index="/runners">Runners</el-menu-item>
      </el-menu>
    </el-header>

    <el-main>
      <router-view></router-view>
    </el-main>

  </el-container>
</template>

<script lang="ts">
  import Vue from "vue"
  import 'element-ui/lib/theme-chalk/index.css'

  export default Vue.extend({
    data() {
      return {
        name: "app",
        activeIndex: this.$route.path
      }
    }
  })
</script>

<style>
  body > .el-container {
    margin: 0px auto;
    width: 80%;
  }
</style>
