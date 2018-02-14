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
