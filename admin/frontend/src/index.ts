import Vue from "vue"
import VueRouter from "vue-router"

import App from "./components/App.vue"
import Dashboard from "./components/Dashboard.vue"
import Stories from "./components/Stories.vue"
import Story from "./components/Story.vue"
import Runners from "./components/Runners.vue"

Vue.use(VueRouter);

const router = new VueRouter({
  mode: 'history',
  routes: [
    {path: '/', component: Dashboard},
    {path: '/stories', component: Stories},
    {path: '/runners', component: Runners},
    {path: '/story/:storyName', component: Story}
  ]
});

new Vue({
  el: '#app',
  render: h => h(App),
  router
});
