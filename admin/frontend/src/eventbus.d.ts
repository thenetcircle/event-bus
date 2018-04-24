declare module "*.vue" {
  import Vue from "vue";
  export default Vue;
}

declare const APP_NAME: string
declare const URL_PREFIX: string
declare const IS_OFFLINE: boolean
declare const GRAFANA_URL: string

declare module "vue-loading-template" {
  import Vue from "vue";
  export default Vue;
}
