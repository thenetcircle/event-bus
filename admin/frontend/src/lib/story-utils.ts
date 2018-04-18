export enum StoryStatus { INIT = 'INIT' }

export interface StoryData {
  source: string;
  sink: string;
  status: string;
  transforms?: string;
  fallback?: string;
}

export interface StoryTask {
  type: string;
  settings: string;
}

export interface StoryInfo {
  source: StoryTask;
  sink: StoryTask;
  status: StoryStatus;
  transforms: StoryTask[];
  fallback?: StoryTask;
}

function _createStoryTaskFromString(str: string): StoryTask {
  let _s = str.split('#')
  return { type: _s[0], settings: _s[1] }
}

export class StoryUtils {

  static getStoryStatus(str: string): StoryStatus {
    if (str === undefined) return StoryStatus.INIT

    switch (str.toUpperCase()) {
      case 'INIT':
      default:
        return StoryStatus.INIT
    }
  }

  static parseStory(data: StoryData): StoryInfo {

    let source: StoryTask = _createStoryTaskFromString(data.source)
    let sink: StoryTask = _createStoryTaskFromString(data.sink)

    let transforms: StoryTask[] = []
    if (data.transforms !== undefined && data.transforms.length > 0) {
      transforms = data.transforms.split('|||').map(trans => _createStoryTaskFromString(trans))
    }

    let fallback = undefined
    if (data.fallback !== undefined) {
      fallback = _createStoryTaskFromString(data.fallback)
    }

    return {
      source: source,
      sink: sink,
      status: StoryUtils.getStoryStatus(data.status),
      transforms: transforms,
      fallback: fallback
    }

  }

  static copyStoryInfo(info: StoryInfo): StoryInfo {
    let copied = <StoryInfo>{ ...info }
    copied.transforms = []
    info.transforms.forEach((trans: StoryTask) => copied.transforms.push({ ...trans }))
    return copied
  }

  static jsonPretty(json: string, space: number = 2): string {
    return JSON.stringify(JSON.parse(json), undefined, space);
  }

  static checkStoryName(name: string): true | string {
    if (name.length <= 0) {
      return 'story name can not be empty'
    }
    if (/^[a-z0-9]+(?:[\_\-][a-z0-9]+)*$/i.test(name)) {
      return true
    }
    else {
      return 'story name could only includes letters, number, and _, -'
    }
  }

  static checkStoryInfo(info: StoryInfo): true | string {
    if (info.source.type.length <= 0 || info.source.settings.length <= 0) {
      return 'story source is incorrect'
    }
    if (info.sink.type.length <= 0 || info.sink.settings.length <= 0) {
      return 'story sink is incorrect'
    }
    return true
  }
}


export enum TaskEditType { ADD, EDIT }
export class TaskEditAction {
  constructor(readonly type: TaskEditType, readonly taskCategory: string, readonly task: StoryTask) {}
}
