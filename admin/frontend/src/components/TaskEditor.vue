<template>

  <div class="modal" :class="{'is-active':show}">
    <div class="modal-background"></div>
    <div class="modal-card">
      <header class="modal-card-head">
        <p class="modal-card-title">Edit {{ title }}</p>
        <button class="delete" @click.prevent="close()" aria-label="close"></button>
      </header>
      <section class="modal-card-body" id="editor">
        <!-- Content ... -->
      </section>
      <footer class="modal-card-foot">
        <button class="button is-success">Save changes</button>
        <button class="button" @click.prevent="close()">Cancel</button>
      </footer>
    </div>
  </div>

</template>

<script lang="ts">
  import Vue from "vue"

  export default Vue.extend({
    props: ['show', 'title', 'type', 'settings'],

    methods: {
      close() {
        this.$emit('close')
      }
    },

    mounted() {
      let ele = <HTMLElement>document.getElementById('editor')
      let editor = new JSONEditor(ele, { theme: 'html', schema: {
          "title": "Person",
          "type": "object",
          "properties": {
            "name": {
              "type": "string",
              "description": "First and Last name",
              "minLength": 4,
              "default": "Jeremy Dorn"
            },
            "age": {
              "type": "integer",
              "default": 25,
              "minimum": 18,
              "maximum": 99
            },
            "favorite_color": {
              "type": "string",
              "format": "color",
              "title": "favorite color",
              "default": "#ffa500"
            },
            "gender": {
              "type": "string",
              "enum": [
                "male",
                "female"
              ]
            },
            "location": {
              "type": "object",
              "title": "Location",
              "properties": {
                "city": {
                  "type": "string",
                  "default": "San Francisco"
                },
                "state": {
                  "type": "string",
                  "default": "CA"
                },
                "citystate": {
                  "type": "string",
                  "description": "This is generated automatically from the previous two fields",
                  "template": "{{city}}, {{state}}",
                  "watch": {
                    "city": "location.city",
                    "state": "location.state"
                  }
                }
              }
            },
            "pets": {
              "type": "array",
              "format": "table",
              "title": "Pets",
              "uniqueItems": true,
              "items": {
                "type": "object",
                "title": "Pet",
                "properties": {
                  "type": {
                    "type": "string",
                    "enum": [
                      "cat",
                      "dog",
                      "bird",
                      "reptile",
                      "other"
                    ],
                    "default": "dog"
                  },
                  "name": {
                    "type": "string"
                  }
                }
              },
              "default": [
                {
                  "type": "dog",
                  "name": "Walter"
                }
              ]
            }
          }
        } });
    }
  })
</script>
