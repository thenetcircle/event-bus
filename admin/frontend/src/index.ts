import Vue from "vue"
import VueRouter from "vue-router"
import Element from "element-ui"
import App from "./components/App.vue"
import routes from "./routes"

Vue.use(VueRouter);
Vue.use(Element);

const router = new VueRouter({ routes });

new Vue({
  el: '#app',
  render: h => h(App),
  router
});
