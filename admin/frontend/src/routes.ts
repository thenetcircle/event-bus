import Dashboard from "./components/Dashboard.vue"
import Stories from "./components/Stories.vue"
import Runners from "./components/Runners.vue"

export default [
  { path: '/', component: Dashboard },
  { path: '/stories', component: Stories },
  { path: '/runners', component: Runners }
];
