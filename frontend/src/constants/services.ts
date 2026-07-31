export const ONSITE_WRITING_DAILY_RATE = 980

export const SERVICE_TYPES = [
  'ONSITE_WRITING',
  'MEDIA_PR',
  'DIRECT_PUBLISHING',
  'NEWS_CONFERENCE',
] as const

export type ServiceType = (typeof SERVICE_TYPES)[number]

export const SERVICE_LABELS: Readonly<Record<ServiceType, string>> = {
  ONSITE_WRITING: '现场采写',
  MEDIA_PR: '媒体邀请',
  DIRECT_PUBLISHING: '直编发稿',
  NEWS_CONFERENCE: '举办新闻发布会',
}

const ONSITE_SERVICE_TYPES: ReadonlySet<ServiceType> = new Set(['ONSITE_WRITING'])

export function isServiceType(value: string): value is ServiceType {
  return SERVICE_TYPES.some((serviceType) => serviceType === value)
}

export function isOnsiteService(value: ServiceType): boolean {
  return ONSITE_SERVICE_TYPES.has(value)
}

export function calculateOnsiteWritingAmount(serviceDays: number, writerCount: number): number {
  const days = Math.max(1, Number(serviceDays) || 1)
  const writers = Math.max(1, Number(writerCount) || 1)
  return ONSITE_WRITING_DAILY_RATE * days * writers
}
