export enum RunnerStatus {
  IDLE = 'IDLE',
  RUNNING = 'RUNNING',
  STOPPED = 'STOPPED'
}

export interface RunnerInfo {
  name: string
  status: RunnerStatus
  server: string
  stories: string[]
  version: string
}

export class RunnerUtils {
  static buildRunnerInfoFromData(runnerName: string, data: any): RunnerInfo {
    return {
      name: runnerName,
      status: data["status"],
      server: data["server"],
      stories: Object.keys(data["stories"]),
      version: data["version"] || ""
    }
  }
}
