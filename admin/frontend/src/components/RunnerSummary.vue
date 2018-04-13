<template>
  <table class="table is-narrow">
    <tbody>
    <tr>
      <th>Status:</th>
      <td>{{ info.status }}</td>
    </tr>
    <tr>
      <th>Host:</th>
      <td>{{ info.host }}</td>
    </tr>
    <tr>
      <th>Assigned Stories:</th>
      <td>{{ Object.keys(info.stories).join(', ') }}</td>
    </tr>
    <tr>
      <th>Version:</th>
      <td>{{ info.version }}</td>
    </tr>
    <tr>
      <th>Instances:</th>
      <td>{{ info.instances.join(', ') }}</td>
    </tr>
    </tbody>
  </table>
</template>

<script lang="ts">
  import Vue from "vue"

  export default Vue.extend({
    props: ['info']
  })
</script>
