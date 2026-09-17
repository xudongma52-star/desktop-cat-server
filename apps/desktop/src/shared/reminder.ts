export type ReminderStatus = 'PENDING' | 'COMPLETED'

export interface Reminder {
  reminderId: number
  content: string
  remindAt: string
  status: ReminderStatus
  completedAt: string | null
  version: number
  createdAt: string
  updatedAt: string
}
