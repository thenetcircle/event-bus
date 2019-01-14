import axios from "axios"
import bus from "../lib/bus"
import {OperatorPosition, StoryInfo, StoryStatus, StoryUtils} from "./story-utils";
import {polyfill, Promise, Thenable} from "es6-promise";
import {RunnerInfo, RunnerStatus, RunnerUtils} from "./runner-utils";

// ES6 Promise Polyfill
polyfill()

type StorableStoryInfo = {
  source?: string
  sink?: string
  operators?: string
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

  assignStory(runnerName: string, storyName: string, amount: number): Thenable<void>

  unassignStory(runnerName: string, storyName: string): Thenable<void>

  rerunStory(runnerName: string, storyName: string): Thenable<void>

  getTopics(): Thenable<string>

  updateTopics(topics: string): Thenable<void>

  removeStory(storyName: string): Thenable<void>
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
        return runners.filter(info => storyName in info.stories)
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

  assignStory(runnerName: string, storyName: string, amount: number): Thenable<void> {
    bus.$emit('loading')

    return axios.post(`${URL_PREFIX}/api/runner/assign`, {
      'runnerName': runnerName,
      'storyName': storyName,
      'amount': amount
    })
      .then(RequestImpl.respHandler)
      .catch(RequestImpl.errorHandler)
  }

  unassignStory(runnerName: string, storyName: string): Thenable<void> {
    bus.$emit('loading')

    return axios.post(`${URL_PREFIX}/api/runner/unassign`, {
      'runnerName': runnerName,
      'storyName': storyName
    })
      .then(RequestImpl.respHandler)
      .catch(RequestImpl.errorHandler)
  }

  rerunStory(runnerName: string, storyName: string): Thenable<void> {
    bus.$emit('loading')

    return axios.post(`${URL_PREFIX}/api/runner/rerun`, {
      'runnerName': runnerName,
      'storyName': storyName
    })
      .then(RequestImpl.respHandler)
      .catch(RequestImpl.errorHandler)
  }

  getTopics(): Thenable<any> {
    bus.$emit('loading')

    return axios.get(`${URL_PREFIX}/api/topics`)
      .then(response => {
        bus.$emit('loaded')
        return response.data ? JSON.stringify(response.data) : '{}'
      })
      .catch(RequestImpl.errorHandler);
  }

  updateTopics(topics: string): Thenable<void> {
    bus.$emit('loading')

    return axios.post(`${URL_PREFIX}/api/topics`, topics)
      .then(RequestImpl.respHandler)
      .catch(RequestImpl.errorHandler)
  }

  removeStory(storyName: string): Thenable<void> {
    bus.$emit('loading')

    return axios.post(`${URL_PREFIX}/api/story/remove`, storyName)
      .then(RequestImpl.respHandler)
      .catch(RequestImpl.errorHandler)
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
    if (storyInfo.operators.length > 0) {
      data.operators = storyInfo.operators.map(trans => {
        return `${trans.position.toLowerCase()}#${trans.type}#${trans.settings}`
      }).join('|||')
    }
    return data
  }

}

class OfflineRequest implements Request {
  testRunners: RunnerInfo[] = [
    {
      name: 'default-runner',
      status: RunnerStatus.RUNNING,
      host: 'test-server-01',
      stories: {'http-to-kafka': '1'},
      version: '2.1.0',
      instances: ['_c_db771980-fe76-4b1c-bfac-e463fee0e930-latch-0000000024']
    },
    {
      name: 'extra-runner',
      status: RunnerStatus.RUNNING,
      host: 'test-server-02',
      stories: {'kafka-to-http': '1'},
      version: '2.1.0',
      instances: ['_c_db771980-fe76-4b1c-bfac-e463fee0e930-latch-0000000024']
    }
  ]

  testStories: [string, StoryInfo][] = [
    ['http-to-kafka', {
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
      operators: [{
        position: OperatorPosition.PRE,
        type: 'tnc-topic-resolver', settings: `{
  "default-topic": "event-{app_name}-default",
  "use-cache": false
}`
      }, {
        position: OperatorPosition.POST,
        type: 'cassandra', settings: `{
  "contact-points": [
    "cassandra-server-01",
    "cassandra-server-02"
  ],
  "port": 9042,
  "parallelism": 3
}`
      }]}],

    ['kafka-to-http', {
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
      operators: []
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
      return storyName in info.stories
    })
    return Promise.resolve(result)
  }

  assignStory(runnerName: string, storyName: string, amount: number): Thenable<void> {
    this.testRunners.forEach((info) => {
      if (info.name == runnerName) {
        info.stories[storyName] = amount + ''
      }
    })
    return Promise.resolve();
  }

  unassignStory(runnerName: string, storyName: string): Thenable<void> {
    this.testRunners.forEach((info) => {
      if (info.name == runnerName && storyName in info.stories) {
        delete info.stories[storyName]
      }
    })
    return Promise.resolve();
  }

  rerunStory(runnerName: string, storyName: string): Thenable<void> {
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

  removeStory(storyName: string): Thenable<void> {
    this.testStories.forEach((info, index) => {
      if (info[0] == storyName) {
        this.testStories.splice(index, 1)
      }
    })
    return Promise.resolve();
  }
}

export default <Request>(IS_OFFLINE ? new OfflineRequest() : new RequestImpl())
