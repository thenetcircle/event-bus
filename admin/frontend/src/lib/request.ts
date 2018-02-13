import axios from "axios"
import bus from "../lib/bus"
import {StoryInfo, StoryUtils} from "./story-utils";

type StorableStoryInfo = {
  status: string;
  source: string;
  sink: string;
  transforms?: string;
  fallback?: string
}

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
      .catch(this.errorHandler)
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
      .catch(this.errorHandler)
  }

  createStory(storyName: string, storyInfo: StoryInfo): Promise<any> {
    bus.$emit('loading')

    return axios.post('/api/create_story', {
      'name': storyName,
      'info': this.createStorableStoryInfo(storyInfo)
    })
      .then(response => {
        let data = response.data
        if (data.code != '0') {
          throw new Error(data.message)
        }
        bus.$emit('loaded')
      })
      .catch(this.errorHandler)
  }

  updateStory(storyName: string, storyInfo: StoryInfo): Promise<any> {
    bus.$emit('loading')

    return axios.post('/api/update_story', {
      'name': storyName,
      'info': this.createStorableStoryInfo(storyInfo)
    })
      .then(response => {
        let data = response.data
        if (data.code != '0') {
          throw new Error(data.message)
        }
        bus.$emit('loaded')
      })
      .catch(this.errorHandler)
  }

  private errorHandler(error: Error): void {
    bus.$emit('loaded')
    bus.$emit('notify', error.message, 'is-danger')
    throw error
  }


  private createStorableStoryInfo(storyInfo: StoryInfo): StorableStoryInfo {
    let data: StorableStoryInfo = {
      'status': storyInfo.status.toString(),
      'source': `${storyInfo.source.type}#${storyInfo.source.settings}`,
      'sink': `${storyInfo.sink.type}#${storyInfo.sink.settings}`
    }
    if (storyInfo.transforms.length > 0) {
      data.transforms = storyInfo.transforms.map(trans => {
        return `${trans.type}#${trans.settings}`
      }).join('|||')
    }
    if (storyInfo.fallback) {
      data.fallback = `${storyInfo.fallback.type}#${storyInfo.fallback.settings}`
    }
    return data
  }

}

export default new Request()
