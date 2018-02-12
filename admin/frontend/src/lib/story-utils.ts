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

}


export enum TaskEditType { ADD, EDIT }
export class TaskEditAction {
  readonly type: TaskEditType
  readonly taskCategory: string
  readonly task: StoryTask
  constructor(type: TaskEditType, taskCategory: string, task: StoryTask) {
    this.type = type
    this.taskCategory = taskCategory
    this.task = task
  }
}
