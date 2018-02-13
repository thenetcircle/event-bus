import axios from "axios"
import bus from "../lib/bus"
import {StoryInfo, StoryUtils} from "./story-utils";

class Request {

  getStories(): Promise<any> {

    bus.$emit('loading')

    return axios.get('/api/stories')
      .then(response => {
        let data = response.data
        let result: [string, StoryInfo][] = []
        if (data) {
          for (let key in data) {
            if (data.hasOwnProperty(key)) {
              result.push([key, StoryUtils.parseStory(data[key])])

            }
          }
        }
        bus.$emit('loaded')
        return result
      })
      .catch(error => {
        bus.$emit('loaded')
        console.error(error)
      })
  }

  getStory(storyName: string): Promise<any> {

    bus.$emit('loading')

    return axios.get('/api/story/' + storyName)
      .then(response => {
        let data = response.data
        let result = {} as StoryInfo
        if (data) {
          result = StoryUtils.parseStory(data)
        }
        bus.$emit('loaded')
        return result
      })
      .catch(error => {
        bus.$emit('loaded')
        console.error(error)
      })
  }

}

export default new Request()
