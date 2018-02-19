import axios from "axios"
import bus from "../lib/bus"
import {StoryInfo, StoryStatus, StoryUtils} from "./story-utils";
import {polyfill, Promise, Thenable} from "es6-promise";

// ES6 Promise Polyfill
polyfill()

type StorableStoryInfo = {
  status: string;
  source: string;
  sink: string;
  transforms?: string;
  fallback?: string
}

interface Request {
  getStories(): Thenable<[string, StoryInfo][]>

  getStory(storyName: string): Thenable<StoryInfo>

  createStory(storyName: string, storyInfo: StoryInfo): Thenable<void>

  updateStory(storyName: string, storyInfo: StoryInfo): Thenable<void>
}

class RequestImpl implements Request {

  getStories(): Thenable<any> {

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

  getStory(storyName: string): Thenable<any> {

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

  createStory(storyName: string, storyInfo: StoryInfo): Thenable<any> {
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

  updateStory(storyName: string, storyInfo: StoryInfo): Thenable<void> {
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
    ['http-to-kafka-with-fallback', {
      source: {type: 'http', settings: `{
  "interface": "0.0.0.0",
  "port": 8000
}`},
      sink: {type: 'kafka', settings: `{
  "bootstrap-servers": "kafka-server-01:9092,kafka-server-02:9092",
  "default-topic": "event-default",
  "use-event-group-as-topic": true,
  "parallelism": 100
}`},
      status: StoryStatus.INIT,
      transforms: [{type: 'tnc-topic-resolver', settings: `{
  "default-topic": "event-{app_name}-default",
  "use-cache": false
}`}],
      fallback: {type: 'cassandra', settings: `{
  "contact-points": [
    "cassandra-server-01",
    "cassandra-server-02"
  ],
  "port": 9042,
  "parallelism": 3
}`}
    }],
    ['kafka-to-http-without-fallback', {
      source: {
        type: 'kafka', settings: `{
  "bootstrap-servers": "kafka-server-01:9092,kafka-server-02:9092",
  "topics": [
    "test-topic-01",
    "test-topic-02"
  ],
  "topic-pattern": "",
  "max-concurrent-partitions": 100,
  "max-connections": 1024
}`
      },
      sink: {type: 'http', settings: `{
  "default-request": {
    "method": "POST",
    "uri": "http://www.testurl.com"
  },
  "min-backoff": "1 s",
  "max-backoff": "30 s",
  "max-retrytime": "12 h",
  "concurrent-retries": 1
}`},
      status: StoryStatus.INIT,
      transforms: [],
      fallback: {type: 'cassandra', settings: `{
  "contact-points": [
    "cassandra-server-01",
    "cassandra-server-02"
  ],
  "port": 9042,
  "parallelism": 3
}`}
    }]
  ]

  getStories(): Thenable<[string, StoryInfo][]> {
    return Promise.resolve(this.testStories)
  }

  getStory(storyName: string): Thenable<StoryInfo> {
    let result = {} as StoryInfo
    this.testStories.forEach(s => {
      if (storyName == s[0]) result = s[1]
    })
    return Promise.resolve(result);
  }

  createStory(storyName: string, storyInfo: StoryInfo): Thenable<void> {
    return Promise.resolve();
  }

  updateStory(storyName: string, storyInfo: StoryInfo): Thenable<void> {
    return Promise.resolve();
  }
}

export default IS_OFFLINE ? new OfflineRequest() : new RequestImpl()
