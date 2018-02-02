<template>
  <el-container>

    <el-header>

      <el-menu :default-active="activeIndex" class="el-menu-demo" mode="horizontal" @select="handleSelect">
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

<script>
export default {
  name: 'app',
  data () {
    return {
      msg: 'Welcome to Your Vue.js App'
    }
  }
}
</script>

<style>
</style>
