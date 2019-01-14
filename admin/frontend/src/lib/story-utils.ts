export enum StoryStatus { INIT = 'INIT' }
export enum OperatorPosition {PRE = 'pre', POST = 'post', BOTH = 'both' }

export interface StoryData {
  source: string;
  sink: string;
  status: string;
  operators?: string;
  transforms?: string;
}

export interface StoryTask {
  type: string;
  settings: string;
}

export interface StoryOperator extends StoryTask {
  position: OperatorPosition;
}

export interface StoryInfo {
  source: StoryTask;
  sink: StoryTask;
  status: StoryStatus;
  operators: StoryOperator[];
}

function _createStoryTaskFromString(str: string): StoryTask {
  let _s = str.split('#')
  return { type: _s[0], settings: _s[1] }
}

function _createStoryOperatorFromString(str: string): StoryOperator {
  if (str.indexOf(OperatorPosition.PRE + '#') !== 0 && str.indexOf(OperatorPosition.POST + '#') !== 0 && str.indexOf(OperatorPosition.BOTH + '#') !== 0 ) {
    str = OperatorPosition.PRE + '#' + str;
  }
  let _s = str.split('#', 3)
  return { position: <OperatorPosition>_s[0], type: _s[1], settings: _s[2] || '{}' }
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

    let operators: StoryOperator[] = []
    if (data.operators !== undefined && data.operators.length > 0) {
      operators = data.operators.split('|||').map(trans => _createStoryOperatorFromString(trans))
    }
    else if (data.transforms !== undefined && data.transforms.length > 0) { // for old format
      operators = data.transforms.split('|||').map(trans => _createStoryOperatorFromString(trans))
    }

    return {
      source: source,
      sink: sink,
      status: StoryUtils.getStoryStatus(data.status),
      operators: operators
    }

  }

  static copyStoryInfo(info: StoryInfo): StoryInfo {
    let copied = <StoryInfo>{ ...info }
    copied.operators = []
    info.operators.forEach((ops: StoryOperator) => copied.operators.push({ ...ops }))
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
