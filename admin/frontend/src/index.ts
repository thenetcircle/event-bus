import Vue from "vue"
import VueRouter from "vue-router"

import App from "./components/App.vue"
import Home from "./components/Home.vue"
import Stories from "./components/Stories.vue"
import NewStory from "./components/NewStory.vue"
import Story from "./components/Story.vue"
import Runners from "./components/Runners.vue"
import Runner from "./components/Runner.vue"
import Topics from "./components/Topics.vue"

Vue.use(VueRouter);

const router = new VueRouter({
  mode: IS_OFFLINE ? 'hash' : 'history',
  routes: [
    {path: `${URL_PREFIX}/`, component: Home, name: 'home'},
    {path: `${URL_PREFIX}/runners`, component: Runners, name: 'runners'},
    {path: `${URL_PREFIX}/runner/:runnerName`, component: Runner, name: 'runner'},
    {path: `${URL_PREFIX}/stories`, component: Stories, name: 'stories'},
    {path: `${URL_PREFIX}/newstory`, component: NewStory, name: 'newstory'},
    {path: `${URL_PREFIX}/story/:storyName`, component: Story, name: 'story'},
    {path: `${URL_PREFIX}/topics`, component: Topics, name: 'topics'}
  ]
});

new Vue({
  el: '#app',
  render: h => h(App),
  router
});
