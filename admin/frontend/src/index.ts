import Vue from "vue";
import Element from "element-ui";
import App from "./components/App.vue";
import 'element-ui/lib/theme-chalk/index.css'

Vue.use(Element)

let v = new Vue({
  el: "#app",
  render: h => h(App)
});
