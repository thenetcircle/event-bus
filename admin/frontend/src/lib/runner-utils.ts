export enum RunnerStatus {
  RUNNING = 'RUNNING',
  STOPPED = 'STOPPED'
}

export interface RunnerInfo {
  name: string
  status: RunnerStatus
  host: string
  stories: string[]
  version: string
  instances: string[]
}

export class RunnerUtils {
  static buildRunnerInfoFromData(runnerName: string, data: any): RunnerInfo {
    let status = typeof(data['latch']) == 'object' ? RunnerStatus.RUNNING : RunnerStatus.STOPPED
    let runnerInfo = JSON.parse(data['info'])

    return {
      name: runnerName,
      status: status,
      host: runnerInfo["host"],
      stories: Object.keys(data["stories"]),
      version: runnerInfo["version"] || "",
      instances: typeof(data['latch']) == 'object' ? Object.keys(data['latch']) : []
    }
  }
}
