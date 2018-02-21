import axios from "axios"
import bus from "../lib/bus"
import {StoryInfo, StoryStatus, StoryUtils} from "./story-utils";
import {polyfill, Promise, Thenable} from "es6-promise";
import {RunnerInfo, RunnerStatus, RunnerUtils} from "./runner-utils";

// ES6 Promise Polyfill
polyfill()

type StorableStoryInfo = {
  source?: string
  sink?: string
  transforms?: string
  fallback?: string
  status?: string
}

interface Request {
  getStories(): Thenable<[string, StoryInfo][]>

  getStory(storyName: string): Thenable<StoryInfo>

  createStory(storyName: string, storyInfo: StoryInfo): Thenable<void>

  updateStory(storyName: string, storyInfo: StoryInfo): Thenable<void>

  getRunners(): Thenable<RunnerInfo[]>

  getStoryRunners(storyName: string): Thenable<RunnerInfo[]>

  getRunner(runnerName: string): Thenable<RunnerInfo>

  assignStory(runnerName: string, storyName: string): Thenable<void>

  unassignStory(runnerName: string, storyName: string): Thenable<void>

  getTopics(): Thenable<string>

  updateTopics(topics: string): Thenable<void>
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
      .catch(RequestImpl.errorHandler)
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
      .catch(RequestImpl.errorHandler)
  }

  createStory(storyName: string, storyInfo: StoryInfo): Thenable<any> {
    bus.$emit('loading')

    return axios.post(`${URL_PREFIX}/api/story/create`, {
      'name': storyName,
      ...RequestImpl.createStorableStoryInfo(storyInfo)
    })
      .then(RequestImpl.respHandler)
      .catch(RequestImpl.errorHandler)
  }

  updateStory(storyName: string, storyInfo: StoryInfo): Thenable<void> {
    bus.$emit('loading')

    return axios.post(`${URL_PREFIX}/api/story/update`, {
      'name': storyName,
      ...RequestImpl.createStorableStoryInfo(storyInfo)
    })
      .then(RequestImpl.respHandler)
      .catch(RequestImpl.errorHandler)
  }

  getRunners(): Thenable<any> {
    bus.$emit('loading')

    return axios.get(`${URL_PREFIX}/api/runners`)
      .then(response => {
        let data = response.data
        let result: RunnerInfo[] = []
        if (data) {
          for (let key in data) {
            if (data.hasOwnProperty(key)) {
              result.push(RunnerUtils.buildRunnerInfoFromData(key, data[key]))
            }
          }
        }
        bus.$emit('loaded')
        return result
      })
      .catch(RequestImpl.errorHandler);
  }

  getStoryRunners(storyName: string): Thenable<RunnerInfo[]> {
    return this.getRunners()
      .then((runners: RunnerInfo[]) => {
        return runners.filter(info => info.stories.indexOf(storyName) !== -1)
      })
  }

  getRunner(runnerName: string): Thenable<any> {
    bus.$emit('loading')

    return axios.get(`${URL_PREFIX}/api/runner/` + runnerName)
      .then(response => {
        let data = response.data
        let result = {} as RunnerInfo
        if (data) {
          result = RunnerUtils.buildRunnerInfoFromData(runnerName, data)
        }
        bus.$emit('loaded')
        return result
      })
      .catch(RequestImpl.errorHandler)
  }

  assignStory(runnerName: string, storyName: string): Thenable<void> {
    return Promise.resolve();
  }

  unassignStory(runnerName: string, storyName: string): Thenable<void> {
    return Promise.resolve();
  }

  getTopics(): Thenable<string> {
    return Promise.resolve('{}')
  }

  updateTopics(topics: string): Thenable<void> {
    return Promise.resolve()
  }

  private static respHandler(response: any): void {
    let data = response.data
    if (data.code != '0') {
      throw new Error(data.message)
    }
    bus.$emit('loaded')
  }

  private static errorHandler(error: any): void {
    bus.$emit('loaded')
    if (error instanceof Error) {
      bus.$emit('notify', error.message, 'is-danger')
      throw error
    }
  }

  private static createStorableStoryInfo(storyInfo: StoryInfo): StorableStoryInfo {
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
  testRunners: RunnerInfo[] = [
    {
      name: 'default-runner',
      status: RunnerStatus.RUNNING,
      server: 'test-server-01',
      stories: ['http-to-kafka-with-fallback'],
      version: '2.1.0'
    },
    {
      name: 'extra-runner',
      status: RunnerStatus.RUNNING,
      server: 'test-server-02',
      stories: ['kafka-to-http-without-fallback'],
      version: '2.1.0'
    }
  ]

  testStories: [string, StoryInfo][] = [
    ['http-to-kafka-with-fallback', {
      source: {
        type: 'http', settings: `{
  "interface": "0.0.0.0",
  "port": 8000
}`
      },
      sink: {
        type: 'kafka', settings: `{
  "bootstrap-servers": "kafka-server-01:9092,kafka-server-02:9092",
  "default-topic": "event-default",
  "use-event-group-as-topic": true,
  "parallelism": 100
}`
      },
      status: StoryStatus.INIT,
      transforms: [{
        type: 'tnc-topic-resolver', settings: `{
  "default-topic": "event-{app_name}-default",
  "use-cache": false
}`
      }],
      fallback: {
        type: 'cassandra', settings: `{
  "contact-points": [
    "cassandra-server-01",
    "cassandra-server-02"
  ],
  "port": 9042,
  "parallelism": 3
}`
      }
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
      sink: {
        type: 'http', settings: `{
  "default-request": {
    "method": "POST",
    "uri": "http://www.testurl.com"
  },
  "min-backoff": "1 s",
  "max-backoff": "30 s",
  "max-retrytime": "12 h",
  "concurrent-retries": 1
}`
      },
      status: StoryStatus.INIT,
      transforms: []
    }]
  ]

  testTopics: string = `[{
    "topic": "test-topic-01", 
    "patterns": ["user\\\\..*", "message\\\\..*"]
  }, {
    "topic": "test-topic-02", 
    "patterns": ["feature\\\\..*", "click\\\\..*"]
  }, {
    "topic": "test-default-topic", 
    "patterns": [".*"]
  }]`

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
    this.testStories.push([storyName, storyInfo])
    return Promise.resolve();
  }

  updateStory(storyName: string, storyInfo: StoryInfo): Thenable<void> {
    this.testStories.forEach((info, index) => {
      if (info[0] == storyName) {
        this.testStories[index][1] = storyInfo
      }
    })
    return Promise.resolve();
  }

  getRunners(): Thenable<RunnerInfo[]> {
    return Promise.resolve(this.testRunners)
  }

  getStoryRunners(storyName: string): Thenable<RunnerInfo[]> {
    let result: RunnerInfo[] = this.testRunners.filter((info) => {
      return info.stories.indexOf(storyName) !== -1
    })
    return Promise.resolve(result)
  }

  assignStory(runnerName: string, storyName: string): Thenable<void> {
    this.testRunners.forEach((info) => {
      if (info.name == runnerName && info.stories.indexOf(storyName) === -1) {
        info.stories.push(storyName)
      }
    })
    return Promise.resolve();
  }

  unassignStory(runnerName: string, storyName: string): Thenable<void> {
    this.testRunners.forEach((info) => {
      let index = info.stories.indexOf(storyName)
      if (info.name == runnerName && index !== -1) {
        info.stories.splice(index, 1)
      }
    })
    return Promise.resolve();
  }

  getRunner(runnerName: string): Thenable<RunnerInfo> {
    let result = {} as RunnerInfo
    this.testRunners.forEach(info => {
      if (runnerName == info.name) result = info
    })
    return Promise.resolve(result);
  }

  getTopics(): Thenable<string> {
    return Promise.resolve(this.testTopics)
  }

  updateTopics(topics: string): Thenable<void> {
    this.testTopics = topics
    return Promise.resolve()
  }
}

export default <Request>(IS_OFFLINE ? new OfflineRequest() : new RequestImpl())
