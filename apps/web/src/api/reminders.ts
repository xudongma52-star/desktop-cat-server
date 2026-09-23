import { apiRequest } from './http'

export type ReminderStatus = 'PENDING' | 'COMPLETED'
export type ReminderScope = 'TODAY' | 'PENDING'

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

export interface ReminderDraft {
  content: string
  remindAt: string
}

export function getReminders(scope: ReminderScope): Promise<Reminder[]> {
  return apiRequest<Reminder[]>(`/api/reminders?scope=${scope}`)
}

export function createReminder(draft: ReminderDraft): Promise<Reminder> {
  return apiRequest<Reminder>('/api/reminders', {
    method: 'POST',
    body: JSON.stringify(draft),
  })
}

export function updateReminder(reminder: Reminder, draft: ReminderDraft): Promise<Reminder> {
  return apiRequest<Reminder>(`/api/reminders/${reminder.reminderId}`, {
    method: 'PUT',
    body: JSON.stringify({ ...draft, version: reminder.version }),
  })
}

export function completeReminder(reminder: Reminder): Promise<Reminder> {
  return apiRequest<Reminder>(`/api/reminders/${reminder.reminderId}/complete`, {
    method: 'PATCH',
    body: JSON.stringify({ version: reminder.version }),
  })
}

export function deleteReminder(reminder: Reminder): Promise<void> {
  return apiRequest<void>(`/api/reminders/${reminder.reminderId}?version=${reminder.version}`, {
    method: 'DELETE',
  })
}
