export interface ConferenceCalendarPlan {
  conferenceNo?: string
  theme?: string
  eventTime?: string
  eventLocation?: string
}

export interface ConferenceCalendarWorkItem {
  itemNo: string
  phase: 'PRE_EVENT' | 'ONSITE' | 'POST_EVENT'
  title: string
  detail?: string
  dueAt?: string
  status: string
}

export interface ConferenceCalendarInput {
  projectNo: string
  projectName: string
  conference?: ConferenceCalendarPlan
  workItems: ConferenceCalendarWorkItem[]
  generatedAt?: Date
}

export interface ConferenceCalendarFile {
  filename: string
  content: string
  eventCount: number
}

const phaseLabels: Record<ConferenceCalendarWorkItem['phase'], string> = {
  PRE_EVENT: '会前准备',
  ONSITE: '现场执行',
  POST_EVENT: '会后传播',
}

const statusLabels: Record<string, string> = {
  PENDING: '待处理',
  IN_PROGRESS: '进行中',
  NEEDS_INFO: '需补充',
  BLOCKED: '受阻',
  COMPLETED: '已完成',
}

function escapeCalendarText(value: string) {
  return value
    .replace(/\\/g, '\\\\')
    .replace(/\r?\n/g, '\\n')
    .replace(/,/g, '\\,')
    .replace(/;/g, '\\;')
}

function utcStamp(value: string | Date) {
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return date
    .toISOString()
    .replace(/[-:]/g, '')
    .replace(/\.\d{3}Z$/, 'Z')
}

function safeUid(value: string) {
  return value.replace(/[^a-zA-Z0-9._-]/g, '-').replace(/-+/g, '-')
}

function safeFilename(value: string) {
  const normalized = value
    .replace(/[<>:"/\\|?*\u0000-\u001f]/g, '-')
    .replace(/\s+/g, ' ')
    .trim()
  return (normalized || '云发布新闻发布会项目').slice(0, 80)
}

function addEvent(
  lines: string[],
  input: {
    uid: string
    stamp: string
    startsAt: string
    summary: string
    description: string
    location?: string
  },
) {
  const start = utcStamp(input.startsAt)
  if (!start) return false
  lines.push('BEGIN:VEVENT')
  lines.push(`UID:${safeUid(input.uid)}@winpress.cn`)
  lines.push(`DTSTAMP:${input.stamp}`)
  lines.push(`DTSTART:${start}`)
  lines.push(`SUMMARY:${escapeCalendarText(input.summary)}`)
  lines.push(`DESCRIPTION:${escapeCalendarText(input.description)}`)
  if (input.location) lines.push(`LOCATION:${escapeCalendarText(input.location)}`)
  lines.push('TRANSP:TRANSPARENT')
  lines.push('END:VEVENT')
  return true
}

/**
 * Builds a customer-shareable calendar file from dates already stored in the project.
 * Contact details, internal assignees, supplier data and operational notes are deliberately
 * excluded so exporting a schedule cannot widen the current user's data boundary.
 */
export function buildConferenceCalendar(input: ConferenceCalendarInput): ConferenceCalendarFile {
  const generatedAt = input.generatedAt || new Date()
  const stamp = utcStamp(generatedAt)
  const lines = [
    'BEGIN:VCALENDAR',
    'VERSION:2.0',
    'PRODID:-//WinPress//Conference Project Calendar//ZH-CN',
    'CALSCALE:GREGORIAN',
    'METHOD:PUBLISH',
    `X-WR-CALNAME:${escapeCalendarText(input.projectName)}`,
  ]
  let eventCount = 0

  if (
    input.conference?.eventTime &&
    addEvent(lines, {
      uid: `${input.projectNo}-${input.conference.conferenceNo || 'conference'}`,
      stamp,
      startsAt: input.conference.eventTime,
      summary: `云发布 · ${input.conference.theme || input.projectName}`,
      description: `项目编号：${input.projectNo}\n事项：新闻发布会`,
      location: input.conference.eventLocation,
    })
  ) {
    eventCount += 1
  }

  for (const item of input.workItems) {
    if (
      item.dueAt &&
      addEvent(lines, {
        uid: `${input.projectNo}-${item.itemNo}`,
        stamp,
        startsAt: item.dueAt,
        summary: `${phaseLabels[item.phase]} · ${item.title}`,
        description: [
          `项目编号：${input.projectNo}`,
          `进度：${statusLabels[item.status] || item.status}`,
          item.detail || '',
        ]
          .filter(Boolean)
          .join('\n'),
      })
    ) {
      eventCount += 1
    }
  }

  lines.push('END:VCALENDAR')
  return {
    filename: `${safeFilename(input.conference?.theme || input.projectName)}-项目日程.ics`,
    content: `${lines.join('\r\n')}\r\n`,
    eventCount,
  }
}
