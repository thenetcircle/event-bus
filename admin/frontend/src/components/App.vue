<template>
  <el-container>
    <el-header>
      <el-menu class="el-menu-demo" mode="horizontal">
        <el-menu-item index="1">Dashboard</el-menu-item>
        <el-menu-item index="2">Stories</el-menu-item>
        <el-menu-item index="3">Runners</el-menu-item>
      </el-menu>
    </el-header>

    <el-main>
      <el-steps align-center>
        <el-step title="步骤1" description="这是一段很长很长很长的描述性文字" status="process"></el-step>
        <el-step title="步骤2" description="这是一段很长很长很长的描述性文字" status="process"></el-step>
        <el-step title="步骤3" description="这是一段很长很长很长的描述性文字" status="process"></el-step>
        <el-step title="步骤4" description="这是一段很长很长很长的描述性文字" status="process"></el-step>
      </el-steps>
    </el-main>

  </el-container>
</template>

<script lang="ts">
  import Vue from "vue";
  import HelloComponent from "./Hello.vue";

  export default {
    data() {
      return {
        name: "app"
      }
    }
  }
</script>

<style></style>
