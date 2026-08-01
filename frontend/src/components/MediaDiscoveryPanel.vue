<script setup lang="ts">
import {
  Building2,
  Check,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  ChevronUp,
  ListFilter,
  MapPin,
  Plus,
  Search,
  SlidersHorizontal,
  UserRoundSearch,
  X,
} from 'lucide-vue-next'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import http, { apiError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import type {
  ApiResponse,
  MediaCandidate,
  MediaDiscoveryTaxonomy,
  MediaSearchResult,
} from '@/types'

const props = withDefaults(
  defineProps<{
    workflow: 'MEDIA_PR' | 'NEWS_CONFERENCE'
    actionLabel: string
    selectedKeys?: string[]
    maxSelection?: number
    busy?: boolean
  }>(),
  {
    selectedKeys: () => [],
    maxSelection: 50,
    busy: false,
  },
)

const emit = defineEmits<{
  select: [candidate: MediaCandidate]
  submit: [candidates: MediaCandidate[]]
}>()

const auth = useAuthStore()
const configured = ref(false)
const capabilities = reactive({
  mediaSearch: false,
  reporterSearch: false,
  taxonomy: false,
  temporarilyUnavailable: false,
})
const checking = ref(true)
const searching = ref(false)
const searched = ref(false)
const error = ref('')
const taxonomyNotice = ref('')
const result = ref<MediaSearchResult | null>(null)
const taxonomy = ref<MediaDiscoveryTaxonomy>({
  mediaTypes: [],
  mediaForms: [],
  regions: [],
})
const target = ref<'MEDIA' | 'REPORTER'>('MEDIA')
const activeMedia = ref<MediaCandidate | null>(null)
const queryScope = ref<'TOPIC' | 'NAME'>('TOPIC')
const pending = ref<Record<string, MediaCandidate>>({})
const showManual = ref(false)
const showAdvanced = ref(false)
const showAllProvinces = ref(false)
const manual = reactive({
  mediaName: '',
  reporterName: '',
  province: '',
  city: '',
})
const filters = reactive({
  keyword: '',
  province: '',
  city: '',
  mediumType: '',
  mediaKind: '',
  mediaForm: '',
  sort: 'score',
})
const page = ref(1)
const pageSize = 20
const beatGroups = [
  { name: '政经', beats: ['时政', '财经', '资本市场', '宏观经济', '区域经济'] },
  { name: '科技', beats: ['人工智能', '半导体', '互联网', '通信', '数码'] },
  { name: '产业', beats: ['制造业', '能源', '化工', '工业设备', '供应链'] },
  { name: '汽车', beats: ['汽车', '新能源汽车', '智能驾驶', '出行'] },
  { name: '消费', beats: ['商业', '零售', '食品', '酒业', '旅游'] },
  { name: '地产', beats: ['房地产', '建筑', '家居', '园区'] },
  { name: '健康', beats: ['医疗', '医药', '健康', '生物科技'] },
  { name: '文教', beats: ['教育', '文化', '出版', '体育'] },
]
const activeBeatGroup = ref(beatGroups[0].name)
const beatOptions = computed(
  () => beatGroups.find((group) => group.name === activeBeatGroup.value)?.beats || [],
)
const preferredProvinces = [
  '中央',
  '北京',
  '广东',
  '上海',
  '浙江',
  '江苏',
  '山东',
  '四川',
  '河南',
  '湖北',
  '湖南',
  '河北',
  '福建',
  '安徽',
]

const selectedKeySet = computed(() => new Set(props.selectedKeys))
const pendingItems = computed(() => Object.values(pending.value))
const selectedCount = computed(() =>
  props.workflow === 'NEWS_CONFERENCE'
    ? props.selectedKeys.length + pendingItems.value.length
    : props.selectedKeys.length,
)
const provinceOptions = computed(() =>
  taxonomy.value.regions.filter((item) => item.name !== '全国'),
)
const quickProvinceOptions = computed(() => {
  const byName = new Map(provinceOptions.value.map((item) => [item.name, item]))
  const ordered = [
    ...preferredProvinces.map((name) => byName.get(name)).filter(Boolean),
    ...provinceOptions.value.filter((item) => !preferredProvinces.includes(item.name)),
  ] as typeof provinceOptions.value
  return showAllProvinces.value ? ordered : ordered.slice(0, preferredProvinces.length)
})
const cityOptions = computed(
  () => taxonomy.value.regions.find((item) => item.name === filters.province)?.children || [],
)
const mediaFormOptions = computed(() =>
  taxonomy.value.mediaForms.filter(
    (item) => !['社交', '问答', '论坛'].some((label) => item.name.includes(label)),
  ),
)
const currentTargetAvailable = computed(() =>
  target.value === 'MEDIA' ? capabilities.mediaSearch : capabilities.reporterSearch,
)
const activeFilterCount = computed(
  () =>
    [
      filters.keyword,
      filters.province,
      filters.city,
      filters.mediumType,
      filters.mediaKind,
      filters.mediaForm,
    ].filter(Boolean).length,
)
const searchPlaceholder = computed(() => {
  if (target.value === 'REPORTER' && activeMedia.value) {
    return `在${activeMedia.value.displayName}中搜索记者姓名或线口`
  }
  if (queryScope.value === 'NAME') {
    return target.value === 'MEDIA' ? '输入媒体机构名称' : '输入记者姓名或所在媒体'
  }
  return target.value === 'MEDIA'
    ? '输入活动议题或行业，例如：新能源汽车发布'
    : '输入行业或线口，例如：芯片、科技'
})
const searchAriaLabel = computed(() => {
  if (queryScope.value === 'NAME') {
    return target.value === 'MEDIA' ? '按媒体名称搜索' : '按记者或媒体名称搜索'
  }
  return target.value === 'MEDIA' ? '按活动议题或行业搜索媒体' : '按行业或线口搜索记者'
})
const canGoNext = computed(() => {
  if (!result.value || result.value.items.length < pageSize) return false
  return result.value.total <= 0 || page.value * pageSize < result.value.total
})
const panelTitle = computed(() => (props.workflow === 'NEWS_CONFERENCE' ? '' : '媒体与记者筛选'))
const panelIntro = computed(() =>
  props.workflow === 'NEWS_CONFERENCE'
    ? ''
    : '先筛选媒体机构，再从该媒体的记者名单中选择邀请对象。',
)
const temporaryUnavailableNotice = computed(() => {
  if (!capabilities.temporarilyUnavailable) return ''
  return '媒体资料暂时不可用，请稍后再试；也可以先人工补充拟邀对象。'
})

function applyStatus(status: {
  available: boolean
  mediaSearch: boolean
  reporterSearch: boolean
  taxonomy: boolean
  temporarilyUnavailable?: boolean
}) {
  capabilities.mediaSearch = status.mediaSearch
  capabilities.reporterSearch = status.reporterSearch
  capabilities.taxonomy = status.taxonomy
  capabilities.temporarilyUnavailable = Boolean(status.temporarilyUnavailable)
  // Reporter lookup is available only after a media candidate has been selected.
  // The selected candidate key is an opaque, short-lived reference resolved by the server.
  configured.value = status.mediaSearch
  if (!status.mediaSearch) {
    activeMedia.value = null
    target.value = 'MEDIA'
  } else if (!currentTargetAvailable.value) {
    target.value = 'MEDIA'
  }
}

async function loadStatus() {
  checking.value = true
  try {
    const response = await http.get<
      ApiResponse<{
        available: boolean
        mediaSearch: boolean
        reporterSearch: boolean
        taxonomy: boolean
        temporarilyUnavailable?: boolean
      }>
    >('/media-discovery/status')
    const status = response.data.data
    applyStatus(status)
    if (configured.value && status.taxonomy) await loadTaxonomy()
  } catch (requestError) {
    error.value = apiError(requestError)
  } finally {
    checking.value = false
  }
}

async function refreshStatus() {
  try {
    const response = await http.get<
      ApiResponse<{
        available: boolean
        mediaSearch: boolean
        reporterSearch: boolean
        taxonomy: boolean
        temporarilyUnavailable?: boolean
      }>
    >('/media-discovery/status')
    applyStatus(response.data.data)
  } catch {
    // Preserve the original search error. This background refresh only updates availability.
  }
}

async function loadTaxonomy() {
  taxonomyNotice.value = ''
  try {
    const response = await http.get<ApiResponse<MediaDiscoveryTaxonomy>>(
      '/media-discovery/taxonomy',
    )
    taxonomy.value = response.data.data
  } catch {
    taxonomyNotice.value = '筛选字典暂未加载，仍可使用关键词检索。'
  }
}

async function search(nextPage = 1) {
  if (capabilities.temporarilyUnavailable) {
    error.value = temporaryUnavailableNotice.value || '媒体资料暂时不可用，请稍后再试。'
    return
  }
  if (!currentTargetAvailable.value) {
    error.value =
      target.value === 'MEDIA' ? '媒体检索暂未启用。' : '记者检索暂未启用，可先按媒体筛选。'
    return
  }
  searching.value = true
  searched.value = true
  error.value = ''
  page.value = nextPage
  try {
    const response = await http.get<ApiResponse<MediaSearchResult>>('/media-discovery', {
      params: {
        target: target.value,
        keyword: queryScope.value === 'TOPIC' ? filters.keyword || undefined : undefined,
        name: queryScope.value === 'NAME' ? filters.keyword || undefined : undefined,
        province: filters.province || undefined,
        city: filters.city || undefined,
        medium_type: filters.mediumType ? Number(filters.mediumType) : undefined,
        media_type: target.value === 'MEDIA' && filters.mediaKind ? filters.mediaKind : undefined,
        mp_types: target.value === 'REPORTER' && filters.mediaForm ? filters.mediaForm : undefined,
        mp_type_group:
          target.value === 'MEDIA' && filters.mediaForm ? filters.mediaForm : undefined,
        media_ref:
          target.value === 'REPORTER' && activeMedia.value
            ? activeMedia.value.candidateKey
            : undefined,
        sort: filters.sort || undefined,
        workflow: props.workflow,
        page: page.value,
        pageSize,
      },
    })
    result.value = response.data.data
  } catch (requestError) {
    error.value = apiError(requestError)
    result.value = null
    void refreshStatus()
  } finally {
    searching.value = false
  }
}

function resetSearchResult() {
  page.value = 1
  result.value = null
  searched.value = false
  error.value = ''
}

async function openReporters(candidate: MediaCandidate) {
  if (!capabilities.reporterSearch) {
    error.value = '记者资料暂不可用，可人工补充记者姓名，或稍后重试。'
    return
  }
  if (!candidate.candidateKey) {
    error.value = '媒体筛选状态已更新，请返回媒体列表重新筛选。'
    return
  }
  activeMedia.value = candidate
  target.value = 'REPORTER'
  queryScope.value = 'TOPIC'
  filters.keyword = ''
  resetSearchResult()
  await search(1)
}

function backToMedia() {
  activeMedia.value = null
  target.value = 'MEDIA'
  queryScope.value = 'TOPIC'
  filters.keyword = ''
  resetSearchResult()
}

function changeQueryScope(next: 'TOPIC' | 'NAME') {
  if (queryScope.value === next) return
  queryScope.value = next
  page.value = 1
  result.value = null
  searched.value = false
  error.value = ''
}

function chooseFilter(field: 'province' | 'mediumType' | 'mediaKind' | 'mediaForm', value: string) {
  filters[field] = filters[field] === value ? '' : value
  page.value = 1
  result.value = null
  searched.value = false
  error.value = ''
}

function chooseBeat(value: string) {
  queryScope.value = 'TOPIC'
  filters.keyword = filters.keyword === value ? '' : value
  page.value = 1
  result.value = null
  searched.value = false
  error.value = ''
}

function resetFilters() {
  Object.assign(filters, {
    keyword: '',
    province: '',
    city: '',
    mediumType: '',
    mediaKind: '',
    mediaForm: '',
    sort: 'score',
  })
  page.value = 1
  result.value = null
  searched.value = false
  error.value = ''
  showAdvanced.value = false
}

function candidateSelected(candidate: MediaCandidate) {
  return (
    selectedKeySet.value.has(candidate.candidateKey) ||
    Boolean(pending.value[candidate.candidateKey])
  )
}

function toggleCandidate(candidate: MediaCandidate) {
  if (props.busy) return
  if (!candidate.available) {
    error.value = '该候选当前不可邀约，请更换对象或由平台核验后人工补充。'
    return
  }
  if (target.value === 'MEDIA' && candidate.candidateType === 'MEDIA') {
    void openReporters(candidate)
    return
  }
  if (props.workflow === 'NEWS_CONFERENCE') {
    if (selectedKeySet.value.has(candidate.candidateKey)) return
    const next = { ...pending.value }
    if (next[candidate.candidateKey]) {
      delete next[candidate.candidateKey]
    } else {
      if (selectedKeySet.value.size + pendingItems.value.length >= props.maxSelection) {
        error.value = `拟邀名单最多保留 ${props.maxSelection} 个对象。`
        return
      }
      next[candidate.candidateKey] = candidate
    }
    pending.value = next
    return
  }
  emit('select', candidate)
}

function submitPending() {
  if (!pendingItems.value.length || props.busy) return
  emit('submit', pendingItems.value)
}

function addManualCandidate() {
  const mediaName = manual.mediaName.trim()
  if (!mediaName) {
    error.value = '请填写拟邀媒体名称。'
    return
  }
  const reporterName = manual.reporterName.trim()
  const keySeed = `${mediaName}-${reporterName || 'media'}-${Date.now()}`
  const candidate: MediaCandidate = {
    candidateKey: `MANUAL:${keySeed}`,
    candidateType: 'MANUAL',
    displayName: mediaName,
    reporterName: reporterName || undefined,
    attribute: '人工补充',
    province: manual.province.trim() || undefined,
    city: manual.city.trim() || undefined,
    coverageTags: [],
    available: true,
  }
  toggleCandidate(candidate)
  Object.assign(manual, { mediaName: '', reporterName: '', province: '', city: '' })
  showManual.value = false
  error.value = ''
}

function hideBrokenImage(event: Event) {
  ;(event.currentTarget as HTMLImageElement).style.display = 'none'
}

function formatNumber(value?: number) {
  if (value == null) return ''
  if (value >= 10000) return `${(value / 10000).toFixed(value >= 100000 ? 0 : 1)}万`
  return value.toLocaleString('zh-CN')
}

watch(
  () => filters.province,
  () => {
    if (!cityOptions.value.some((item) => item.name === filters.city)) filters.city = ''
  },
)

watch(
  () => props.selectedKeys,
  (keys) => {
    if (props.workflow !== 'NEWS_CONFERENCE') return
    const next = { ...pending.value }
    for (const key of keys) delete next[key]
    pending.value = next
  },
)

onMounted(loadStatus)
</script>

<template>
  <section class="media-discovery-panel">
    <header v-if="workflow !== 'NEWS_CONFERENCE' || selectedCount" class="media-discovery-heading">
      <div>
        <span v-if="workflow !== 'NEWS_CONFERENCE'" class="eyebrow">媒体邀请</span>
        <h3 v-if="panelTitle">{{ panelTitle }}</h3>
        <p v-if="panelIntro">{{ panelIntro }}</p>
      </div>
      <div v-if="selectedCount" class="media-selection-count">
        <Check :size="15" />已选 {{ selectedCount }}
      </div>
    </header>

    <p v-if="checking" class="muted">正在连接媒体资料库…</p>
    <div v-else-if="!configured" class="media-discovery-unavailable">
      <template v-if="auth.user?.role === 'CUSTOMER'">
        在线筛选暂不可用，可人工补充候选名单，由项目负责人核验。
      </template>
      <template v-else>媒体检索服务尚未启用；记者检索依赖先选定媒体，请检查后台连接配置。</template>
    </div>

    <template v-else>
      <div class="media-selection-path" aria-label="媒体邀请筛选步骤">
        <span :class="{ active: target === 'MEDIA', complete: target === 'REPORTER' }">
          <b>1</b>筛选媒体
        </span>
        <span :class="{ active: target === 'REPORTER' }"><b>2</b>选择记者</span>
        <button v-if="activeMedia" class="button ghost" type="button" @click="backToMedia">
          <ChevronLeft :size="16" />更换媒体
        </button>
      </div>

      <div v-if="activeMedia" class="selected-media-context">
        <Building2 :size="18" />
        <span>
          <small>当前媒体</small>
          <strong>{{ activeMedia.displayName }}</strong>
        </span>
      </div>

      <p v-if="temporaryUnavailableNotice" class="form-error media-rate-limit-notice">
        {{ temporaryUnavailableNotice }}
      </p>

      <form class="media-discovery-filters" @submit.prevent="search(1)">
        <div class="media-search-bar">
          <div class="media-query-mode" role="tablist" aria-label="搜索方式">
            <button
              type="button"
              role="tab"
              :aria-selected="queryScope === 'TOPIC'"
              :class="{ active: queryScope === 'TOPIC' }"
              @click="changeQueryScope('TOPIC')"
            >
              按议题 / 线口
            </button>
            <button
              type="button"
              role="tab"
              :aria-selected="queryScope === 'NAME'"
              :class="{ active: queryScope === 'NAME' }"
              @click="changeQueryScope('NAME')"
            >
              按名称
            </button>
          </div>
          <label class="media-filter-search">
            <span class="visually-hidden">{{ searchAriaLabel }}</span>
            <span class="input-icon search-input">
              <Search :size="18" />
              <input
                v-model="filters.keyword"
                :aria-label="searchAriaLabel"
                :placeholder="searchPlaceholder"
              />
            </span>
          </label>
          <button
            class="button primary media-search-submit"
            type="submit"
            :disabled="searching || capabilities.temporarilyUnavailable"
          >
            <Search :size="17" />{{
              capabilities.temporarilyUnavailable ? '暂不可用' : searching ? '正在查找' : '查找候选'
            }}
          </button>
        </div>

        <div class="media-facet-rows">
          <div v-if="queryScope === 'TOPIC'" class="media-facet-row">
            <span class="media-facet-label">领域</span>
            <div class="media-facet-options">
              <button
                v-for="group in beatGroups"
                :key="group.name"
                class="media-facet-chip"
                type="button"
                :class="{ active: activeBeatGroup === group.name }"
                :aria-pressed="activeBeatGroup === group.name"
                @click="activeBeatGroup = group.name"
              >
                {{ group.name }}
              </button>
            </div>
          </div>

          <div v-if="queryScope === 'TOPIC'" class="media-facet-row">
            <span class="media-facet-label">线口</span>
            <div class="media-facet-options">
              <button
                v-for="option in beatOptions"
                :key="option"
                class="media-facet-chip"
                type="button"
                :class="{ active: filters.keyword === option }"
                :aria-pressed="filters.keyword === option"
                @click="chooseBeat(option)"
              >
                {{ option }}
              </button>
            </div>
          </div>

          <div v-if="provinceOptions.length" class="media-facet-row">
            <span class="media-facet-label">地区</span>
            <div class="media-facet-options">
              <button
                class="media-facet-chip"
                type="button"
                :class="{ active: !filters.province }"
                :aria-pressed="!filters.province"
                @click="chooseFilter('province', '')"
              >
                全国
              </button>
              <button
                v-for="item in quickProvinceOptions"
                :key="item.code || item.name"
                class="media-facet-chip"
                type="button"
                :class="{ active: filters.province === item.name }"
                :aria-pressed="filters.province === item.name"
                @click="chooseFilter('province', item.name)"
              >
                {{ item.name }}
              </button>
              <button
                v-if="provinceOptions.length > preferredProvinces.length"
                class="media-facet-expand"
                type="button"
                :aria-expanded="showAllProvinces"
                @click="showAllProvinces = !showAllProvinces"
              >
                {{ showAllProvinces ? '收起' : '全部地区' }}
                <ChevronUp v-if="showAllProvinces" :size="14" />
                <ChevronDown v-else :size="14" />
              </button>
            </div>
          </div>

          <div v-if="filters.province && cityOptions.length" class="media-facet-row">
            <span class="media-facet-label">城市</span>
            <div class="media-facet-options">
              <button
                class="media-facet-chip"
                type="button"
                :class="{ active: !filters.city }"
                :aria-pressed="!filters.city"
                @click="filters.city = ''"
              >
                全省
              </button>
              <button
                v-for="item in cityOptions"
                :key="item.code || item.name"
                class="media-facet-chip"
                type="button"
                :class="{ active: filters.city === item.name }"
                :aria-pressed="filters.city === item.name"
                @click="filters.city = filters.city === item.name ? '' : item.name"
              >
                {{ item.name }}
              </button>
            </div>
          </div>

          <div v-if="taxonomy.mediaTypes.length" class="media-facet-row">
            <span class="media-facet-label">媒体属性</span>
            <div class="media-facet-options">
              <button
                class="media-facet-chip"
                type="button"
                :class="{ active: !filters.mediumType }"
                :aria-pressed="!filters.mediumType"
                @click="chooseFilter('mediumType', '')"
              >
                不限
              </button>
              <button
                v-for="item in taxonomy.mediaTypes.slice(0, 10)"
                :key="item.id"
                class="media-facet-chip"
                type="button"
                :class="{ active: filters.mediumType === String(item.id) }"
                :aria-pressed="filters.mediumType === String(item.id)"
                @click="chooseFilter('mediumType', String(item.id))"
              >
                {{ item.name }}
              </button>
            </div>
          </div>

          <div v-if="mediaFormOptions.length" class="media-facet-row">
            <span class="media-facet-label">渠道形态</span>
            <div class="media-facet-options">
              <button
                class="media-facet-chip"
                type="button"
                :class="{ active: !filters.mediaForm }"
                :aria-pressed="!filters.mediaForm"
                @click="chooseFilter('mediaForm', '')"
              >
                不限
              </button>
              <button
                v-for="item in mediaFormOptions.slice(0, 10)"
                :key="item.id"
                class="media-facet-chip"
                type="button"
                :class="{ active: filters.mediaForm === String(item.id) }"
                :aria-pressed="filters.mediaForm === String(item.id)"
                @click="chooseFilter('mediaForm', String(item.id))"
              >
                {{ item.name }}
              </button>
            </div>
          </div>

          <div v-if="target === 'MEDIA'" class="media-facet-row">
            <span class="media-facet-label">来源</span>
            <div class="media-facet-options">
              <button
                v-for="option in [
                  { value: '', label: '不限' },
                  { value: 'media', label: '新闻媒体' },
                  { value: 'self_media', label: '自媒体' },
                ]"
                :key="option.value || 'all'"
                class="media-facet-chip"
                type="button"
                :class="{ active: filters.mediaKind === option.value }"
                :aria-pressed="filters.mediaKind === option.value"
                @click="chooseFilter('mediaKind', option.value)"
              >
                {{ option.label }}
              </button>
            </div>
          </div>
        </div>

        <div class="media-filter-toolbar">
          <button
            class="button ghost"
            type="button"
            :aria-expanded="showAdvanced"
            @click="showAdvanced = !showAdvanced"
          >
            <SlidersHorizontal :size="16" />
            {{ showAdvanced ? '收起高级条件' : '高级条件' }}
            <span v-if="activeFilterCount">{{ activeFilterCount }}</span>
          </button>
          <button class="button ghost" type="button" @click="resetFilters">
            <X :size="16" />清除条件
          </button>
        </div>

        <div v-if="showAdvanced" class="media-advanced-filters">
          <label>
            <span>省份</span>
            <select v-model="filters.province" aria-label="按省份筛选">
              <option value="">全国</option>
              <option v-for="item in provinceOptions" :key="item.code" :value="item.name">
                {{ item.name }}
              </option>
            </select>
          </label>
          <label>
            <span>城市</span>
            <select v-model="filters.city" aria-label="按城市筛选" :disabled="!filters.province">
              <option value="">全部城市</option>
              <option v-for="item in cityOptions" :key="item.code" :value="item.name">
                {{ item.name }}
              </option>
            </select>
          </label>
          <label>
            <span>完整媒体属性</span>
            <select v-model="filters.mediumType" aria-label="按媒体属性筛选">
              <option value="">全部属性</option>
              <option v-for="item in taxonomy.mediaTypes" :key="item.id" :value="String(item.id)">
                {{ item.name }}
              </option>
            </select>
          </label>
          <label>
            <span>完整渠道形态</span>
            <select v-model="filters.mediaForm" aria-label="按媒体形态筛选">
              <option value="">全部形态</option>
              <option v-for="item in mediaFormOptions" :key="item.id" :value="String(item.id)">
                {{ item.name }}
              </option>
            </select>
          </label>
          <label>
            <span>排序</span>
            <select v-model="filters.sort" aria-label="候选排序">
              <option value="score">综合匹配</option>
              <option value="news_count">报道数量</option>
              <option value="time">近期活跃</option>
            </select>
          </label>
        </div>
      </form>

      <p v-if="taxonomyNotice" class="media-discovery-notice">{{ taxonomyNotice }}</p>
      <p v-if="result?.notice" class="media-discovery-notice">{{ result.notice }}</p>
      <p v-if="error" class="form-error">{{ error }}</p>

      <div v-if="!searched && !error" class="media-search-empty">
        <ListFilter :size="24" />
        <div>
          <strong v-if="workflow !== 'NEWS_CONFERENCE'">媒体筛选</strong>
          <p v-if="workflow !== 'NEWS_CONFERENCE'">
            {{
              target === 'MEDIA'
                ? '支持多条件筛选，也可直接搜索媒体名称。'
                : '可按行业、线口和城市筛选；已知记者姓名时切换到“按名称”。'
            }}
          </p>
        </div>
      </div>
      <div
        v-else-if="searched && !searching && !result?.items.length && !error"
        class="media-search-empty"
      >
        <Search :size="24" />
        <div>
          <strong>没有找到合适候选</strong>
          <p>减少筛选条件后重试，或在下方人工补充已知媒体。</p>
        </div>
      </div>

      <div v-if="result?.items.length" class="media-result-list">
        <article
          v-for="item in result.items"
          :key="item.candidateKey"
          class="media-result-item"
          :class="{ selected: candidateSelected(item), unavailable: !item.available }"
        >
          <div class="media-result-avatar">
            <img
              v-if="item.avatarUrl || item.logoUrl"
              :src="item.avatarUrl || item.logoUrl"
              alt=""
              loading="lazy"
              referrerpolicy="no-referrer"
              @error="hideBrokenImage"
            />
            <UserRoundSearch v-else-if="item.candidateType === 'REPORTER'" :size="20" />
            <Building2 v-else :size="20" />
          </div>
          <div class="media-result-copy">
            <div class="media-result-title">
              <strong>{{ item.reporterName || item.displayName }}</strong>
              <span :class="{ unavailable: !item.available }">
                {{
                  !item.available
                    ? '暂不可邀约'
                    : item.candidateType === 'REPORTER'
                      ? '记者'
                      : item.attribute || '媒体'
                }}
              </span>
            </div>
            <p v-if="item.candidateType === 'REPORTER'">{{ item.displayName }}</p>
            <p v-if="item.province || item.city">
              <MapPin :size="13" />{{ [item.province, item.city].filter(Boolean).join(' · ') }}
            </p>
            <div v-if="item.coverageTags.length" class="tag-row">
              <span v-for="tag in item.coverageTags.slice(0, 4)" :key="tag">{{ tag }}</span>
            </div>
          </div>
          <div class="media-result-evidence">
            <span v-if="item.score != null"
              ><strong>{{ Math.round(item.score) }}</strong
              >综合分</span
            >
            <span v-if="item.newsCount != null"
              ><strong>{{ formatNumber(item.newsCount) }}</strong
              >篇报道</span
            >
            <span v-if="item.fansCount != null"
              ><strong>{{ formatNumber(item.fansCount) }}</strong
              >受众</span
            >
          </div>
          <button
            class="button media-result-action"
            :class="candidateSelected(item) ? 'selected' : 'secondary'"
            type="button"
            :disabled="
              busy ||
              !item.available ||
              (workflow === 'NEWS_CONFERENCE' && selectedKeySet.has(item.candidateKey))
            "
            @click="toggleCandidate(item)"
          >
            <Check v-if="candidateSelected(item)" :size="16" />
            <Plus v-else :size="16" />
            {{
              target === 'MEDIA'
                ? '筛选记者'
                : workflow === 'NEWS_CONFERENCE' && selectedKeySet.has(item.candidateKey)
                  ? '已在名单'
                  : !item.available
                    ? '暂不可选'
                    : candidateSelected(item)
                      ? '已选择'
                      : actionLabel
            }}
          </button>
        </article>
      </div>

      <footer v-if="result?.items.length" class="media-results-footer">
        <span>
          第 {{ page }} 页
          <template v-if="result.total">
            · 共 {{ result.total.toLocaleString('zh-CN') }} 项</template
          >
          <template v-if="result.updatedAt">
            · 资料更新 {{ new Date(result.updatedAt).toLocaleDateString('zh-CN') }}
          </template>
        </span>
        <div>
          <button
            class="button ghost"
            type="button"
            :disabled="searching || page <= 1"
            @click="search(page - 1)"
          >
            <ChevronLeft :size="16" />上一页
          </button>
          <button
            class="button ghost"
            type="button"
            :disabled="searching || !canGoNext"
            @click="search(page + 1)"
          >
            下一页<ChevronRight :size="16" />
          </button>
        </div>
      </footer>
    </template>

    <div class="media-manual-entry">
      <button class="button ghost" type="button" @click="showManual = !showManual">
        <Plus :size="16" />人工补充拟邀对象
      </button>
      <form v-if="showManual" @submit.prevent="addManualCandidate">
        <label>
          <span>媒体名称<span class="required">*</span></span>
          <input v-model="manual.mediaName" required maxlength="180" placeholder="媒体机构全称" />
        </label>
        <label>
          <span>记者姓名</span>
          <input v-model="manual.reporterName" maxlength="80" placeholder="知道时填写" />
        </label>
        <label><span>省份</span><input v-model="manual.province" maxlength="80" /></label>
        <label><span>城市</span><input v-model="manual.city" maxlength="80" /></label>
        <button class="button secondary" type="submit">加入已选</button>
      </form>
    </div>

    <div v-if="workflow === 'NEWS_CONFERENCE' && pendingItems.length" class="media-batch-dock">
      <div>
        <strong>已勾选 {{ pendingItems.length }} 个候选</strong>
        <span> 名单已有 {{ selectedKeys.length }} 个对象；加入后仍须由平台核定并发出邀请。 </span>
      </div>
      <button class="button primary" type="button" :disabled="busy" @click="submitPending">
        <Check :size="16" />{{ busy ? '正在加入' : '加入发布会拟邀名单' }}
      </button>
    </div>
  </section>
</template>
