import Vue from "vue"
import VueRouter from "vue-router"

import App from "./components/App.vue"
import Home from "./components/Home.vue"
import Stories from "./components/Stories.vue"
import NewStory from "./components/NewStory.vue"
import Story from "./components/Story.vue"
import Runners from "./components/Runners.vue"

Vue.use(VueRouter);

const router = new VueRouter({
  mode: 'history',
  routes: [
    {path: '/', component: Home},
    {path: '/runners', component: Runners},
    {path: '/stories', component: Stories},
    {path: '/newstory', component: NewStory},
    {path: '/story/:storyName', component: Story}
  ]
});

new Vue({
  el: '#app',
  render: h => h(App),
  router
});
