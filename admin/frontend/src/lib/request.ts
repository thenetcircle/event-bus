import axios from "axios"
import bus from "../lib/bus"
import {StoryInfo, StoryStatus, StoryUtils} from "./story-utils";
import {Promise as ES6Promise} from "es6-promise";

type StorableStoryInfo = {
  status: string;
  source: string;
  sink: string;
  transforms?: string;
  fallback?: string
}

interface Request {
  getStories(): Promise<[string, StoryInfo][]>
  getStory(storyName: string): Promise<StoryInfo>
  createStory(storyName: string, storyInfo: StoryInfo): Promise<void>
  updateStory(storyName: string, storyInfo: StoryInfo): Promise<void>
}

class RequestImpl implements Request {

  getStories(): Promise<any> {

    bus.$emit('loading')

    return axios.get(`${URL_PREFIX}/api/stories`)
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

    return axios.get(`${URL_PREFIX}/api/story/` + storyName)
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

    return axios.post(`${URL_PREFIX}/api/create_story`, {
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

  updateStory(storyName: string, storyInfo: StoryInfo): Promise<void> {
    bus.$emit('loading')

    return axios.post(`${URL_PREFIX}/api/update_story`, {
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

  private errorHandler(error: any): void {
    bus.$emit('loaded')
    if (error instanceof Error) {
      bus.$emit('notify', error.message, 'is-danger')
      throw error
    }
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

class OfflineRequest implements Request {
  testStories: [string, StoryInfo][] = [
    ['teststory', {
      source: { type: 'http', settings: '{}' },
      sink: { type: 'kafka', settings: '{}' },
      status: StoryStatus.INIT,
      transforms: [{ type: 'tnc-topic-resolver', settings: '{}' }],
      fallback: { type: 'cassandra', settings: '{}' }
    } as StoryInfo]
  ]

  getStories(): Promise<[string, StoryInfo][]> {
    return Promise.resolve(this.testStories)
  }

  getStory(storyName: string): Promise<StoryInfo> {
    let result = {} as StoryInfo
    this.testStories.forEach(s => {
      if (storyName == s[0]) result = s[1]
    })
    return Promise.resolve(result);
  }

  createStory(storyName: string, storyInfo: StoryInfo): Promise<void> {
    return Promise.resolve();
  }

  updateStory(storyName: string, storyInfo: StoryInfo): Promise<void> {
    return Promise.resolve();
  }
}

export default IS_OFFLINE ? new OfflineRequest() : new RequestImpl()
