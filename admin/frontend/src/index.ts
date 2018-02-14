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
    {path: '/', component: Home, name: 'home'},
    {path: '/runners', component: Runners, name: 'runners'},
    {path: '/runner/:runnerName', component: Story, name: 'runner'},
    {path: '/stories', component: Stories, name: 'stories'},
    {path: '/newstory', component: NewStory, name: 'newstory'},
    {path: '/story/:storyName', component: Story, name: 'story'}
  ]
});

new Vue({
  el: '#app',
  render: h => h(App),
  router
});
