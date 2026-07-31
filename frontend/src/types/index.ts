import type { ServiceType } from '@/constants/services'

export type Role = 'CUSTOMER' | 'PUBLISH_OPERATOR' | 'PLATFORM_ADMIN'

export interface User {
  id: number
  userNo: string
  organizationId: number
  organizationName: string
  username: string
  displayName: string
  mobile: string
  email: string
  role: Role
  permissions: string[]
}

export interface ApiResponse<T> {
  success: boolean
  code: string
  message: string
  data: T
  timestamp: string
}

export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  pageSize: number
}

export interface ProjectSummary {
  id: number
  projectNo: string
  projectName: string
  status: string
  customerName: string
  organizationName: string
  operatorName?: string
  manuscriptStatus?: string
  hasApprovedManuscript?: boolean
  taskCount: number
  resultCount: number
  plannedEndAt?: string
}

export interface PublicChannel {
  id: number
  channelName: string
  channelType: 'MEDIA_PR' | 'DIRECT_PUBLISHING' | 'LEGACY_OWNED_CHANNEL'
  category?: string
  region?: string
  publishForm?: string
  expectedDays?: number
  linkSupport: boolean
  linkType?: string
  newsSource?: string
  entryLevel?: string
  specialIndustry?: string
  weekendPolicy?: string
  publicNotes?: string
  customerPrice?: number
  currency?: string
  validUntil?: string
  publicTerms?: string
  status: string
}

export interface Channel extends PublicChannel {
  channelNo: string
}

export type QuoteState = 'ACTIVE' | 'EXPIRING' | 'EXPIRED' | 'UNQUOTED'

export interface PricingChannel {
  id: number
  channelNo: string
  channelName: string
  category?: string
  region?: string
  publishForm?: string
  expectedDays?: number
  channelStatus: string
  quoteId?: number
  supplierId?: number
  supplierName?: string
  costPrice?: number
  customerPrice?: number
  currency?: string
  validFrom?: string
  validUntil?: string
  publicTerms?: string
  quoteStatus?: string
  quoteCreatedAt?: string
  quoteState: QuoteState
}

export interface SupplierOption {
  id: number
  supplierNo: string
  supplierName: string
  supplierType: string
}

export interface Supplier {
  id: number
  supplierNo: string
  supplierName: string
  supplierType: string
  contactName?: string
  contactPhone?: string
  contactEmail?: string
  serviceScope?: string
  internalNote?: string
  status: string
  channelCount: number
  activeOrderCount: number
  updatedAt: string
}

export interface SupplierOrder {
  id: number
  supplierOrderNo: string
  supplierId?: number
  supplierNo?: string
  supplierName: string
  publishPlanId: number
  planNo: string
  publishTaskId: number
  taskNo: string
  projectNo: string
  projectName: string
  channelId: number
  channelNo: string
  channelName: string
  channelType: string
  customerPrice?: number
  costPrice?: number
  grossMargin?: number
  articleTitle?: string
  plannedPublishAt?: string
  externalOrderNo?: string
  submissionNote?: string
  fulfillmentMode: 'UNCONFIRMED' | 'MANUAL' | 'API'
  submissionEvidenceReference?: string
  exceptionReason?: string
  status: string
  operatorName?: string
  updatedAt: string
}

export interface BusinessInquiry {
  id: number
  inquiryNo: string
  inquiryType: string
  companyName: string
  contactName: string
  mobile: string
  email?: string
  message: string
  status: string
  handlerName?: string
  handledAt?: string
  handlingNote?: string
  createdAt: string
}

export interface QuoteAdjustment {
  adjustmentNo: string
  previousCustomerPrice?: number
  currentCustomerPrice: number
  previousCostPrice?: number
  currentCostPrice?: number
  adjustmentMode: 'MANUAL' | 'BATCH_PERCENT'
  reason: string
  createdAt: string
  adjustedBy?: string
}

export interface MediaCandidate {
  candidateKey: string
  candidateType: 'MEDIA' | 'REPORTER' | 'MANUAL'
  displayName: string
  reporterName?: string
  attribute?: string
  province?: string
  city?: string
  channelForm?: string
  category?: string
  coverageTags: string[]
  available: boolean
  score?: number
  newsCount?: number
  fansCount?: number
  logoUrl?: string
  avatarUrl?: string
  updatedAt?: string
}

export interface MediaSearchResult {
  items: MediaCandidate[]
  total: number
  page: number
  pageSize: number
  updatedAt?: string
  stale: boolean
  notice?: string
}

export interface MediaLookupOption {
  id: number
  name: string
}

export interface MediaRegionOption {
  code: string
  name: string
  children: MediaRegionOption[]
}

export interface MediaDiscoveryTaxonomy {
  mediaTypes: MediaLookupOption[]
  mediaForms: MediaLookupOption[]
  regions: MediaRegionOption[]
  updatedAt?: string
}

export interface WritingAssignment {
  id: number
  assignmentNo: string
  projectId: number
  projectNo: string
  projectName: string
  eventTime?: string
  serviceLocation?: string
  matchingMode: string
  serviceDays: number
  writerCount: number
  unitPrice: number
  estimatedAmount: number
  status:
    | 'WAITING_MATCH'
    | 'OFFERED'
    | 'PARTIALLY_ACCEPTED'
    | 'ACCEPTED'
    | 'DECLINED'
    | 'CANCELLED'
    | 'COMPLETED'
  acceptedWriterCount: number
  offeredWriterCount: number
  openWriterSlots: number
  writerNames?: string
  memberStatus?: 'OFFERED' | 'ACCEPTED' | 'DECLINED' | 'CANCELLED' | 'COMPLETED'
  memberDistanceKm?: number
  offeredAt?: string
  respondedAt?: string
}

export interface WriterProfile {
  id: number
  writerNo: string
  displayName: string
  province?: string
  city?: string
  serviceRadiusKm?: number
  expertiseTags?: string
  availabilityStatus: 'AVAILABLE' | 'BUSY' | 'OFFLINE'
  status: string
}

export interface PublishTask {
  /** Only returned to authorised operator/admin task workspaces; customer APIs use taskNo. */
  id?: number
  taskNo: string
  projectId: number
  projectNo: string
  projectName: string
  manuscriptId?: number
  manuscriptTitle?: string
  channelType: Channel['channelType']
  channelName: string
  operatorName?: string
  plannedPublishAt?: string
  actualPublishAt?: string
  executionNote?: string
  exceptionReason?: string
  mediaInvitationStatus?: string
  mediaInvitedAt?: string
  mediaRespondedAt?: string
  status: string
  updatedAt: string
}

export interface WorkItem {
  itemType?: string
  itemId?: number
  recordNo: string
  projectId?: number
  projectName?: string
  title: string
  status: string
  updatedAt: string
  itemLabel: string
}

export interface TaskRecord {
  recordNo: string
  projectId: number
  projectName: string
  title: string
  serviceType: ServiceType
  itemLabel: string
  status: string
  ownerName?: string
  dueAt?: string
  completedAt?: string
  note?: string
  updatedAt: string
}

export interface OrderRecord {
  recordNo: string
  projectId: number
  projectNo: string
  projectName: string
  serviceType: 'ONSITE_WRITING' | 'MEDIA_PR' | 'DIRECT_PUBLISHING' | 'NEWS_CONFERENCE'
  serviceLabel: string
  amount?: number
  currency?: string
  status: string
  itemDetail?: string
  ownerName?: string
  createdAt: string
  updatedAt: string
}

export interface SettlementRecord {
  settlementNo: string
  projectId: number
  projectNo: string
  projectName: string
  serviceType: string
  serviceLabel: string
  archiveOnly: boolean
  amount: number
  paidAmount: number
  adjustmentAmount: number
  outstandingAmount: number
  currency: string
  dueAt?: string
  paidAt?: string
  invoiceNo?: string
  status: 'PENDING' | 'CONFIRMED' | 'PAID' | 'CANCELLED'
  updatedAt: string
}

export type SettlementTransactionType =
  'PAYMENT' | 'REFUND' | 'CREDIT_ADJUSTMENT' | 'DEBIT_ADJUSTMENT' | 'WRITE_OFF'

export interface SettlementTransactionRecord {
  transactionNo: string
  settlementNo: string
  projectId: number
  projectNo: string
  projectName: string
  serviceType: string
  serviceLabel: string
  archiveOnly: boolean
  transactionType: SettlementTransactionType
  transactionLabel: string
  amount: number
  currency: string
  occurredAt: string
  referenceNo?: string
  customerNote?: string
  status: 'CONFIRMED' | 'VOIDED'
  createdAt: string
  updatedAt: string
}
