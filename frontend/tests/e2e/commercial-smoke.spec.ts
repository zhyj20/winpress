import { expect, test, type Page } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'

const publicPages = [
  '/',
  '/insights',
  '/methodology',
  '/cases',
  '/cases/industry-forum',
  '/cases/chipsea-computex-2026',
  '/about',
  '/api-integration',
  '/cloud-writing',
  '/contact',
  '/legal',
  '/privacy',
  '/terms',
  '/service-boundaries',
  '/case-evidence',
]

function readAdminCredentials(markdown: string) {
  const row = markdown
    .split(/\r?\n/)
    .map((line) =>
      line
        .split('|')
        .map((field) => field.trim().replace(/^`|`$/g, ''))
        .filter(Boolean),
    )
    .find(
      (fields) =>
        fields.length === 4 &&
        fields[0] === '平台运营' &&
        /^[^@\s|]+@[^@\s|]+$/.test(fields[1] || '') &&
        (fields[2]?.length || 0) >= 8,
    )

  if (!row) throw new Error('本机平台运营测试账号未配置')
  return { username: row[1], password: row[2] }
}

function readCustomerCredentials(markdown: string) {
  const row = markdown
    .split(/\r?\n/)
    .map((line) =>
      line
        .split('|')
        .map((field) => field.trim().replace(/^`|`$/g, ''))
        .filter(Boolean),
    )
    .find(
      (fields) =>
        fields.length === 4 &&
        fields[0] === '客户' &&
        /^[^@\s|]+@[^@\s|]+$/.test(fields[1] || '') &&
        (fields[2]?.length || 0) >= 8,
    )

  if (!row) throw new Error('本机客户测试账号未配置')
  return { username: row[1], password: row[2] }
}

function readOperatorCredentials(markdown: string) {
  const row = markdown
    .split(/\r?\n/)
    .map((line) =>
      line
        .split('|')
        .map((field) => field.trim().replace(/^`|`$/g, ''))
        .filter(Boolean),
    )
    .find(
      (fields) =>
        fields.length === 4 &&
        fields[0] === '服务运营 / 测试写手' &&
        /^[^@\s|]+@[^@\s|]+$/.test(fields[1] || '') &&
        (fields[2]?.length || 0) >= 8,
    )

  if (!row) throw new Error('本机服务运营测试账号未配置')
  return { username: row[1], password: row[2] }
}

async function loadAdminCredentials() {
  const username = process.env.WINPRESS_E2E_ADMIN_USERNAME
  const password = process.env.WINPRESS_E2E_ADMIN_PASSWORD
  if (username && password) return { username, password }

  try {
    const accountDocument = await readFile(
      resolve(process.cwd(), '../docs/TEST-ACCOUNTS.md'),
      'utf8',
    )
    return readAdminCredentials(accountDocument)
  } catch {
    return null
  }
}

async function loadCustomerCredentials() {
  const username = process.env.WINPRESS_E2E_CUSTOMER_USERNAME
  const password = process.env.WINPRESS_E2E_CUSTOMER_PASSWORD
  if (username && password) return { username, password }

  try {
    const accountDocument = await readFile(
      resolve(process.cwd(), '../docs/TEST-ACCOUNTS.md'),
      'utf8',
    )
    return readCustomerCredentials(accountDocument)
  } catch {
    return null
  }
}

async function loadOperatorCredentials() {
  const username = process.env.WINPRESS_E2E_OPERATOR_USERNAME
  const password = process.env.WINPRESS_E2E_OPERATOR_PASSWORD
  if (username && password) return { username, password }

  try {
    const accountDocument = await readFile(
      resolve(process.cwd(), '../docs/TEST-ACCOUNTS.md'),
      'utf8',
    )
    return readOperatorCredentials(accountDocument)
  } catch {
    return null
  }
}

async function signInForNavigationAudit(
  page: Page,
  credentials: { username: string; password: string },
) {
  await page.goto('/', { waitUntil: 'networkidle' })
  await page.evaluate(() => {
    localStorage.clear()
    sessionStorage.clear()
  })
  await page.goto('/login', { waitUntil: 'networkidle' })
  await page.locator('input[autocomplete="username"]').fill(credentials.username)
  await page.locator('input[autocomplete="current-password"]').fill(credentials.password)
  await page.getByRole('button', { name: /^登录$/ }).click()
  await expect(page).toHaveURL(/\/dashboard$/)
}

async function assertPageIntegrity(page: Page) {
  await expect(page.locator('.mkt-header')).toBeVisible()
  await expect(page.locator('.mkt-logo')).toHaveCount(1)
  await expect(page.locator('body')).not.toContainText('[plugin:vite:vue]')
  await expect(page.locator('body')).not.toContainText('Internal Server Error')

  const failures = await page
    .locator('img')
    .evaluateAll((images) =>
      images
        .filter((image) => image.complete && image.naturalWidth === 0)
        .map((image) => image.getAttribute('src') || '(missing src)'),
    )
  expect(failures, `损坏图片：${failures.join(', ')}`).toEqual([])

  const dimensions = await page.evaluate(() => ({
    clientWidth: document.documentElement.clientWidth,
    scrollWidth: document.documentElement.scrollWidth,
  }))
  expect(dimensions.scrollWidth).toBeLessThanOrEqual(dimensions.clientWidth + 1)

  const accessibility = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze()
  const blockingViolations = accessibility.violations.filter((violation) =>
    ['serious', 'critical'].includes(violation.impact || ''),
  )
  expect(
    blockingViolations.map((violation) => ({
      id: violation.id,
      impact: violation.impact,
      targets: violation.nodes.slice(0, 3).map((node) => node.target.join(' ')),
    })),
    '存在严重或关键级无障碍缺陷',
  ).toEqual([])
}

test.describe('公开营销页面', () => {
  for (const path of publicPages) {
    test(`${path} 保持导航、资源与跨端布局完整`, async ({ page }, testInfo) => {
      const errors: string[] = []
      page.on('pageerror', (error) => errors.push(error.message))
      page.on('console', (message) => {
        if (message.type() === 'error') errors.push(message.text())
      })

      await page.goto(path, { waitUntil: 'networkidle' })
      await assertPageIntegrity(page)

      if (testInfo.project.name === 'desktop') {
        await expect(page.locator('.mkt-nav')).toBeVisible()
        await expect(page.locator('.mkt-nav')).toContainText('解决方案')
        await expect(page.locator('.mkt-nav')).toContainText('行业资讯')
        await expect(page.locator('.mkt-nav')).toContainText('方法论')
        await expect(page.locator('.mkt-nav')).toContainText('案例展示')
        await expect(page.locator('.mkt-nav')).toContainText('关于我们')
      } else {
        await expect(page.getByRole('button', { name: '打开导航' })).toBeVisible()
      }

      expect(errors, `页面异常：${errors.join(' | ')}`).toEqual([])
    })
  }
})

test('关于我们页的服务方案入口定位到首页解决方案', async ({ page }) => {
  await page.goto('/about', { waitUntil: 'networkidle' })

  const serviceLink = page.getByRole('link', { name: '查看服务方案' })
  await expect(serviceLink).toHaveAttribute('href', '/#solutions')
  await serviceLink.click()

  await expect(page).toHaveURL(/\/#solutions$/)
  await expect(page.locator('#solutions')).toBeVisible()
})

test('首页营销页保持可读正文与紧凑的服务入口', async ({ page }) => {
  await page.goto('/', { waitUntil: 'networkidle' })

  const heroCopy = await page.locator('.home-hero-copy > p').evaluate((element) => {
    const style = window.getComputedStyle(element)
    return { fontSize: Number.parseFloat(style.fontSize), lineHeight: Number.parseFloat(style.lineHeight) }
  })
  expect(heroCopy.fontSize).toBeGreaterThanOrEqual(16)
  expect(heroCopy.lineHeight / heroCopy.fontSize).toBeGreaterThanOrEqual(1.65)

  const entryCards = await page.locator('.home-entry-card').evaluateAll((elements) =>
    elements.map((element) => {
      const bounds = element.getBoundingClientRect()
      return { height: bounds.height, width: bounds.width }
    }),
  )
  expect(entryCards).toHaveLength(4)
  expect(entryCards.every((card) => card.height <= 390 && card.width > 0)).toBeTruthy()

  await expect(page.locator('.home-news-flow')).toBeVisible()
})

test('旧站文章按方法资料与证据边界呈现', async ({ page }) => {
  await page.goto('/insights', { waitUntil: 'networkidle' })

  await expect(page.getByRole('heading', { name: '行业资讯' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '项目资料选读' }).first()).toBeVisible()
  await expect(page.getByText('“同理不同样”').first()).toBeVisible()
  await expect(page.getByText('“帮助记者寻找不同的新闻角度”').first()).toBeVisible()
  await expect(page.getByText('“一个地方错了后面可能就会引发连锁反应”')).toBeVisible()
  await expect(page.getByText('“线上线下同步，为记者参会破除时空限制”')).toBeVisible()
  await expect(page.locator('a[href="https://old.winpress.cn/article/23"]')).toBeVisible()
  await expect(page.locator('a[href="https://old.winpress.cn/article/18"]')).toBeVisible()
  await expect(page.locator('a[href="https://old.winpress.cn/article/20"]').last()).toBeVisible()
  await expect(page.locator('a[href="https://old.winpress.cn/article/22"]')).toBeVisible()

  const bodyText = await page.locator('body').innerText()
  expect(bodyText).not.toContain('100%记者参会率')
  expect(bodyText).not.toContain('十万记者大本营')
  expect(bodyText).not.toContain('必出成果')
})

test('营销页面以栏目词组呈现主标题', async ({ page }) => {
  const pages = [
    ['/api-integration', 'API 接入'],
    ['/insights', '行业资讯'],
    ['/methodology', '发布会方法论'],
    ['/cases', '项目示例'],
  ] as const

  for (const [path, heading] of pages) {
    await page.goto(path, { waitUntil: 'networkidle' })
    await expect(page.getByRole('heading', { name: heading, level: 1 })).toBeVisible()
  }

  await expect(page.getByRole('heading', { name: '项目资料选读', level: 2 })).toBeVisible()
})

test('媒体邀请案例隐藏操作过程、原件与现场素材', async ({ page }, testInfo) => {
  await page.goto('/cases/industry-forum', { waitUntil: 'networkidle' })

  await expect(page.getByRole('heading', { name: '媒体邀请项目（脱敏样本）' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '项目操作留在项目内' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '本页不说明什么' })).toBeVisible()
  await expect(page.locator('a[download]')).toHaveCount(0)
  await expect(page.locator('a[href$=".pdf"], a[href$=".docx"], a[href$=".pptx"]')).toHaveCount(0)
  await expect(page.locator('img[src^="/case-media/"]')).toHaveCount(0)
  await expect(page.locator('a[href^="/case-media/"]')).toHaveCount(0)
  await expect(
    page.locator('.case-study-flow, .case-study-gallery, .case-study-material-grid'),
  ).toHaveCount(0)

  const bodyText = await page.locator('body').innerText()
  for (const restrictedText of [
    '辰韬资本',
    '中信证券',
    '易咖智车',
    '闻客信息',
    '18675948964',
    '传播框架',
    '采访准备',
    '现场协同',
    '稿件审校',
  ]) {
    expect(bodyText).not.toContain(restrictedText)
  }

  const robots = await page.request.get('/robots.txt')
  expect(robots.ok()).toBeTruthy()
  expect(await robots.text()).toContain('Disallow: /cases/industry-forum')
  expect(await robots.text()).toContain('Disallow: /cases/chipsea-computex-2026')

  const caseResponse = await page.request.get('/cases/industry-forum')
  expect(caseResponse.headers()['x-robots-tag']).toContain('noindex')

  const protectedMaterial = await page.request.get('/case-materials/original.pdf')
  expect(protectedMaterial.status()).toBe(404)

  for (const filename of [
    'industry-forum-interview-space.jpg',
    'industry-forum-interview-01.jpg',
    'industry-forum-interview-02.jpg',
    'industry-forum-interview-03.jpg',
    'industry-forum-interview-04.jpg',
    'industry-forum-interview-05.jpg',
    'industry-forum-interview-06.jpg',
  ]) {
    const photoResponse = await page.request.get(`/case-media/${filename}`)
    expect(photoResponse.status()).toBe(404)
  }

  await assertPageIntegrity(page)
  await page.screenshot({
    path: testInfo.outputPath('media-invitation-case-redacted.png'),
    fullPage: true,
  })
})

test('芯海科技案例按项目阶段和公开报道证据呈现', async ({ page }) => {
  await page.goto('/cases/chipsea-computex-2026', { waitUntil: 'networkidle' })

  await expect(
    page.getByRole('heading', { name: '芯海科技 COMPUTEX 2026 媒体云发布会' }),
  ).toBeVisible()
  await expect(page.getByRole('heading', { name: '立项与资料确认' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '项目时间线' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '采访与产品展示' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '公开报道核验' })).toBeVisible()
  await expect(page.getByText('6 月 11 日另保留一条展后行业观察。')).toBeVisible()
  await expect(page.getByText('一颗EC的全球突围：AI PC风口下')).toBeVisible()
  await expect(page.locator('.chipsea-coverage-list article')).toHaveCount(13)
  await expect(page.locator('.chipsea-evidence-label--archive_evidence')).toHaveCount(1)
  // The published case deliberately distinguishes only public pages and archive evidence.
  // Do not invent a metadata-only result merely to satisfy a stale UI assertion.
  await expect(page.locator('.chipsea-evidence-label--metadata')).toHaveCount(0)
  await expect(page.locator('img[src^="/case-media/chipsea-"]')).toHaveCount(9)
  await expect(page.locator('a[download]')).toHaveCount(0)
  await expect(page.locator('a[href$=".pdf"], a[href$=".docx"], a[href$=".pptx"]')).toHaveCount(0)
  await expect(page.getByText('预算、联系人、媒体沟通记录')).toBeVisible()

  const robots = await page.request.get('/robots.txt')
  expect(robots.ok()).toBeTruthy()
  expect(await robots.text()).toContain('Disallow: /cases/chipsea-computex-2026')

  const caseResponse = await page.request.get('/cases/chipsea-computex-2026')
  expect(caseResponse.headers()['x-robots-tag']).toContain('noindex')

  const photoDerivative = await page.request.get('/case-media/chipsea-computex-booth.webp')
  expect(photoDerivative.ok()).toBeTruthy()
  expect(photoDerivative.headers()['x-robots-tag']).toContain('noimageindex')

  await page.goto('/cases/chipsea-computex-2026#results', { waitUntil: 'networkidle' })
  await page.waitForTimeout(100)
  const anchorPosition = await page.evaluate(() => {
    const targetTop = document.querySelector('#results')?.getBoundingClientRect().top ?? 0
    const navigationBottom =
      document.querySelector('.case-story-nav')?.getBoundingClientRect().bottom ?? 0
    return { targetTop, navigationBottom }
  })
  expect(anchorPosition.targetTop).toBeGreaterThanOrEqual(anchorPosition.navigationBottom - 2)
})

test('三类角色的操作菜单遵循 PRD 名称、顺序和路由', async ({ page }, testInfo) => {
  const [customer, operator, admin] = await Promise.all([
    loadCustomerCredentials(),
    loadOperatorCredentials(),
    loadAdminCredentials(),
  ])
  test.skip(
    !customer || !operator || !admin,
    '请配置客户、服务运营和平台运营测试账号后执行角色导航回归。',
  )
  if (!customer || !operator || !admin) return

  const profiles = [
    {
      credentials: customer,
      role: '客户',
      groups: ['项目总览', '服务下单', '任务与订单'],
      items: [
        ['传播看板', '/dashboard'],
        ['项目管理', '/projects'],
        ['提交需求', '/requirements/new'],
        ['云采写', '/requirements/cloud-writing'],
        ['邀请媒体', '/media-invitation'],
        ['直编发稿', '/direct-publishing'],
        ['举办新闻发布会', '/requirements/news-conference'],
        ['待办事项', '/work-items'],
        ['媒体与发布任务', '/tasks'],
        ['任务记录', '/orders'],
        ['订单管理', '/order-management'],
      ],
    },
    {
      credentials: operator,
      role: '服务运营',
      groups: ['工作总览', '服务执行', '历史记录'],
      items: [
        ['传播看板', '/dashboard'],
        ['项目协同', '/projects'],
        ['当前待办', '/work-items'],
        ['云采写任务', '/writing-assignments'],
        ['媒体与发布任务', '/tasks'],
        ['任务记录', '/orders'],
      ],
    },
    {
      credentials: admin,
      role: '平台运营',
      groups: ['运营总览', '服务执行', '渠道与交易', '平台管理'],
      items: [
        ['传播看板', '/dashboard'],
        ['项目管理', '/projects'],
        ['当前待办', '/work-items'],
        ['采写派单', '/writing-assignments'],
        ['媒体与发布任务', '/tasks'],
        ['任务记录', '/orders'],
        ['渠道管理', '/admin/channels'],
        ['定价与比价', '/admin/pricing'],
        ['供应商与订单', '/admin/suppliers'],
        ['接口管理', '/admin/integrations'],
        ['开放 API', '/admin/open-api'],
        ['结算与交易', '/admin/settlements'],
        ['商务咨询', '/admin/inquiries'],
        ['账号与权限', '/admin/users'],
        ['操作日志', '/admin/audit'],
      ],
    },
  ] as const

  for (const profile of profiles) {
    await signInForNavigationAudit(page, profile.credentials)

    if (testInfo.project.name !== 'desktop') {
      await page.getByRole('button', { name: '打开操作菜单' }).click()
    }

    const sidebar = page.locator('.sidebar')
    await expect(sidebar).toBeVisible()
    await expect(sidebar.locator('.sidebar-nav-group-label')).toHaveText(profile.groups)
    await expect(sidebar.locator('.user-block small')).toHaveText(profile.role)

    const items = await sidebar.locator('.sidebar-nav a').evaluateAll((links) =>
      links.map((link) => ({
        label: link.textContent?.trim() || '',
        path: new URL((link as HTMLAnchorElement).href).pathname,
      })),
    )
    expect(items).toEqual(profile.items.map(([label, path]) => ({ label, path })))

    if (profile.role === '客户') {
      await sidebar.getByRole('link', { name: '举办新闻发布会' }).click()
      await expect(page).toHaveURL(/\/requirements\/news-conference$/)
      await expect(page.locator('h1')).toHaveText('举办新闻发布会')
    }
  }
})

test('传播看板按角色展示服务口径，指标落在对应筛选范围', async ({ page }, testInfo) => {
  const [customer, operator, admin] = await Promise.all([
    loadCustomerCredentials(),
    loadOperatorCredentials(),
    loadAdminCredentials(),
  ])
  test.skip(
    !customer || !operator || !admin,
    '请配置客户、服务运营和平台运营测试账号后执行传播看板回归。',
  )
  if (!customer || !operator || !admin) return

  const profiles = [
    {
      key: 'customer',
      credentials: customer,
      required: ['待我处理', '进行中项目', '待客户确认', '待平台执行', '任务记录', '已完成待验收'],
      forbidden: ['待处理咨询', '待客户验收'],
    },
    {
      key: 'operator',
      credentials: operator,
      required: ['待我处理', '进行中项目', '待平台执行', '任务记录', '待客户验收'],
      forbidden: ['待处理咨询', '待客户确认'],
    },
    {
      key: 'admin',
      credentials: admin,
      required: ['待我处理', '进行中项目', '待平台执行', '任务记录', '待处理咨询'],
      forbidden: ['待客户确认'],
    },
  ] as const

  for (const profile of profiles) {
    await signInForNavigationAudit(page, profile.credentials)
    await page.goto('/dashboard', { waitUntil: 'networkidle' })

    const main = page.getByRole('main')
    const primaryMetrics = main.locator('.dashboard-primary-metrics')
    await expect(main.locator('h1')).toHaveText('传播看板')
    await expect(main.getByRole('heading', { name: '服务概况', exact: true })).toBeVisible()
    for (const label of profile.required)
      await expect(primaryMetrics.getByText(label, { exact: true })).toBeVisible()
    for (const label of profile.forbidden) {
      await expect(primaryMetrics.getByText(label, { exact: true })).toHaveCount(0)
    }
    await expect(main.locator('.compact-metric-grid small')).toHaveText([
      '邀请媒体',
      '直编发稿',
      '云采写',
      '举办新闻发布会',
    ])
    await expect(main.getByText('新闻发布会', { exact: true })).toHaveCount(0)
    await expect(main.getByText('牛媒信源工单', { exact: true })).toHaveCount(0)
    await expect(primaryMetrics.getByRole('link', { name: '查看待办任务' })).toHaveAttribute(
      'href',
      '/work-items',
    )
    await expect(
      primaryMetrics.getByRole('link', { name: '查看四项服务待平台执行任务' }),
    ).toHaveAttribute('href', '/orders?scope=pendingExecution')

    await assertPageIntegrity(page)
    await page.screenshot({
      path: testInfo.outputPath(`dashboard-${profile.key}.png`),
      fullPage: true,
    })

    if (profile.key === 'customer') {
      await expect(
        primaryMetrics.getByRole('link', { name: '查看待客户确认的发布计划' }),
      ).toHaveAttribute('href', '/work-items?scope=planConfirmation')
    }

    if (profile.key === 'admin') {
      const inquiryRequest = page.waitForRequest((request) => {
        const url = new URL(request.url())
        return (
          url.pathname.endsWith('/api/v1/admin/inquiries') &&
          url.searchParams.get('status') === 'NEW'
        )
      })
      await primaryMetrics.getByRole('link', { name: '查看待处理商务咨询' }).click()
      await expect(page).toHaveURL(/\/admin\/inquiries\?status=NEW$/)
      await inquiryRequest
      await expect(page.getByRole('heading', { name: '商务咨询', exact: true })).toBeVisible()
      await expect(page.getByLabel('咨询状态')).toHaveValue('NEW')
    }
  }
})

test('媒体与发布任务按角色隔离，未分配任务进入负责人安排', async ({ page }, testInfo) => {
  const [customer, operator, admin] = await Promise.all([
    loadCustomerCredentials(),
    loadOperatorCredentials(),
    loadAdminCredentials(),
  ])
  test.skip(
    !customer || !operator || !admin,
    '请配置客户、服务运营和平台运营测试账号后执行任务记录回归。',
  )
  if (!customer || !operator || !admin) return

  async function taskItems() {
    return page.evaluate(async () => {
      const token = localStorage.getItem('winpress_token')
      const params = new URLSearchParams(location.search)
      params.set('page', '1')
      params.set('pageSize', '15')
      const response = await fetch(`/api/v1/publish-tasks?${params.toString()}`, {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      })
      const payload = await response.json()
      return payload.data.items as Array<Record<string, unknown>>
    })
  }

  await signInForNavigationAudit(page, customer)
  await page.goto('/tasks?channelType=MEDIA_PR', { waitUntil: 'networkidle' })
  const customerMain = page.getByRole('main')
  await expect(customerMain.locator('h1')).toHaveText('媒体与发布任务')
  await expect(customerMain.getByLabel('按渠道类型筛选任务')).toHaveValue('MEDIA_PR')
  const customerTasks = await taskItems()
  expect(customerTasks.length).toBeGreaterThan(0)
  for (const task of customerTasks) {
    expect(Object.keys(task)).not.toEqual(
      expect.arrayContaining([
        'id',
        'manuscriptId',
        'operatorName',
        'executionNote',
        'exceptionReason',
      ]),
    )
  }
  await expect(customerMain.locator('.publish-task-table')).toBeVisible()
  await expect(customerMain.getByRole('columnheader', { name: '负责人', exact: true })).toHaveCount(
    0,
  )
  await expect(customerMain.getByRole('button', { name: '处理', exact: true })).toHaveCount(0)
  await expect(customerMain.getByText('待分配', { exact: true })).toHaveCount(0)
  await assertPageIntegrity(page)
  await page.screenshot({ path: testInfo.outputPath('tasks-customer.png'), fullPage: true })

  await signInForNavigationAudit(page, operator)
  await page.goto('/tasks?channelType=MEDIA_PR', { waitUntil: 'networkidle' })
  const operatorMain = page.getByRole('main')
  await expect(operatorMain.locator('h1')).toHaveText('媒体与发布任务')
  await expect(operatorMain.getByLabel('按渠道类型筛选任务')).toHaveValue('MEDIA_PR')
  const operatorTasks = await taskItems()
  expect(operatorTasks.length).toBeGreaterThan(0)
  for (const task of operatorTasks) {
    expect(task.id).toEqual(expect.any(Number))
    expect(task.operatorName).toEqual(expect.any(String))
    expect(task.status).not.toBe('PENDING_ASSIGNMENT')
  }
  await expect(
    operatorMain.getByRole('columnheader', { name: '负责人', exact: true }),
  ).toBeVisible()
  await expect(operatorMain.getByRole('link', { name: '安排负责人', exact: true })).toHaveCount(0)
  await assertPageIntegrity(page)
  await page.screenshot({ path: testInfo.outputPath('tasks-operator.png'), fullPage: true })

  await signInForNavigationAudit(page, admin)
  await page.goto('/tasks?status=PENDING_ASSIGNMENT', { waitUntil: 'networkidle' })
  const adminMain = page.getByRole('main')
  await expect(adminMain.locator('h1')).toHaveText('媒体与发布任务')
  await expect(adminMain.getByLabel('按任务状态筛选')).toHaveValue('PENDING_ASSIGNMENT')
  const adminTasks = await taskItems()
  expect(adminTasks.length).toBeGreaterThan(0)
  for (const task of adminTasks) {
    expect(task.status).toBe('PENDING_ASSIGNMENT')
    expect(task.operatorName).toBeFalsy()
  }
  await expect(adminMain.getByRole('columnheader', { name: '负责人', exact: true })).toBeVisible()
  await expect(adminMain.getByRole('link', { name: '安排负责人', exact: true })).toHaveCount(
    adminTasks.length,
  )
  await assertPageIntegrity(page)
  await page.screenshot({ path: testInfo.outputPath('tasks-admin.png'), fullPage: true })

  await adminMain.getByRole('link', { name: '安排负责人', exact: true }).first().click()
  await expect(page).toHaveURL(/\/projects\/\d+$/)
  await expect(page.getByRole('main').getByText('服务负责人', { exact: true })).toBeVisible()
  await expect(page.getByLabel('选择项目负责人')).toBeVisible()
})

test('媒体与发布任务保留暂不推进状态的深链筛选', async ({ page }, testInfo) => {
  const credentials = await loadAdminCredentials()
  test.skip(!credentials, '请配置平台运营测试账号后执行任务状态筛选回归。')
  if (!credentials) return

  await signInForNavigationAudit(page, credentials)
  await page.goto('/tasks?status=NOT_PROCEEDING', { waitUntil: 'networkidle' })

  const main = page.getByRole('main')
  const statusFilter = main.getByLabel('按任务状态筛选')
  await expect(statusFilter).toHaveValue('NOT_PROCEEDING')
  await expect(statusFilter.getByRole('option', { name: '暂不推进', exact: true })).toHaveCount(1)
  await assertPageIntegrity(page)
  await page.screenshot({ path: testInfo.outputPath('tasks-not-proceeding.png'), fullPage: true })
})

test('管理员接口管理页只显示待验收状态与凭据引用', async ({ page }) => {
  const credentials = await loadAdminCredentials()
  test.skip(
    !credentials,
    '请设置 WINPRESS_E2E_ADMIN_USERNAME 和 WINPRESS_E2E_ADMIN_PASSWORD 后执行管理员回归。',
  )
  if (!credentials) return

  await page.goto('/login', { waitUntil: 'networkidle' })
  await page.locator('input[autocomplete="username"]').fill(credentials.username)
  await page.locator('input[autocomplete="current-password"]').fill(credentials.password)
  await page.getByRole('button', { name: /^登录$/ }).click()
  await expect(page).toHaveURL(/\/dashboard$/)

  await page.goto('/admin/integrations', { waitUntil: 'networkidle' })
  await expect(page.getByRole('heading', { name: '接口管理', exact: true })).toBeVisible()
  await expect(page.getByRole('navigation', { name: '接口管理栏目' })).toBeVisible()
  await expect(page.getByText('凭据不进数据库')).toBeVisible()
  await expect(page.getByText('牛媒媒体与记者检索')).toBeVisible()
  await expect(page.getByText('暂不可用').first()).toBeVisible()

  await page.getByRole('button', { name: /上线验收/ }).click()
  await expect(page.locator('.gate-card')).toHaveCount(5)

  await assertPageIntegrity(page)

  await page.getByRole('button', { name: /历史组合审核/ }).click()
  await expect(
    page.getByText('只登记业务决定，不自动拆单、删除、归档或改写历史项目。'),
  ).toBeVisible()
})

test('发布会项目日程可导出且不包含内部经营字段', async ({ page }) => {
  const credentials = await loadAdminCredentials()
  test.skip(
    !credentials,
    '请设置 WINPRESS_E2E_ADMIN_USERNAME 和 WINPRESS_E2E_ADMIN_PASSWORD 后执行管理员回归。',
  )
  if (!credentials) return

  await page.goto('/login', { waitUntil: 'networkidle' })
  await page.locator('input[autocomplete="username"]').fill(credentials.username)
  await page.locator('input[autocomplete="current-password"]').fill(credentials.password)
  await page.getByRole('button', { name: /^登录$/ }).click()
  await expect(page).toHaveURL(/\/dashboard$/)

  const projectId = await page.evaluate(async () => {
    const token = localStorage.getItem('winpress_token')
    const headers = token ? { Authorization: `Bearer ${token}` } : {}
    const response = await fetch(
      '/api/v1/projects?serviceType=NEWS_CONFERENCE&page=1&pageSize=100',
      { headers },
    )
    const payload = await response.json()
    for (const item of payload?.data?.items || []) {
      const detailResponse = await fetch(`/api/v1/projects/${item.id}`, { headers })
      if (!detailResponse.ok) continue
      const detailPayload = await detailResponse.json()
      const detail = detailPayload?.data
      if (
        detail?.conference?.eventTime ||
        detail?.project?.eventTime ||
        detail?.conferenceWorkItems?.some((workItem: { dueAt?: string }) => workItem.dueAt)
      ) {
        return item.id
      }
    }
    return 0
  })
  test.skip(!projectId, '当前本机数据没有可导出日期的发布会项目。')
  if (!projectId) return

  await page.goto(`/projects/${projectId}`, { waitUntil: 'networkidle' })
  const downloadPromise = page.waitForEvent('download')
  await page.getByRole('button', { name: '导出项目日程' }).click()
  const download = await downloadPromise
  expect(download.suggestedFilename()).toMatch(/项目日程\.ics$/)
  const downloadPath = await download.path()
  expect(downloadPath).toBeTruthy()
  if (!downloadPath) return
  const calendar = await readFile(downloadPath, 'utf8')
  expect(calendar).toContain('BEGIN:VCALENDAR')
  expect(calendar).toContain('BEGIN:VEVENT')
  expect(calendar).toContain('END:VCALENDAR')
  expect(calendar).not.toMatch(/SUPPLIER|COST_PRICE|TOKEN|SECRET|ORGANIZER|CONTACT/i)
})

test('已完成发布会统筹事项在工作台中保持只读锁定', async ({ page }, testInfo) => {
  const credentials = await loadAdminCredentials()
  test.skip(
    !credentials,
    '请设置 WINPRESS_E2E_ADMIN_USERNAME 和 WINPRESS_E2E_ADMIN_PASSWORD 后执行管理员回归。',
  )
  if (!credentials) return

  await signInForNavigationAudit(page, credentials)
  const projectId = await page.evaluate(async () => {
    const token = localStorage.getItem('winpress_token')
    const headers = token ? { Authorization: `Bearer ${token}` } : {}
    const response = await fetch(
      '/api/v1/projects?serviceType=NEWS_CONFERENCE&page=1&pageSize=100',
      { headers },
    )
    const payload = await response.json()
    for (const item of payload?.data?.items || []) {
      const detailResponse = await fetch(`/api/v1/projects/${item.id}`, { headers })
      if (!detailResponse.ok) continue
      const detailPayload = await detailResponse.json()
      const workItems = detailPayload?.data?.conferenceWorkItems || []
      if (workItems.some((workItem: { status?: string }) => workItem.status === 'COMPLETED')) {
        return item.id
      }
    }
    return 0
  })
  test.skip(!projectId, '当前本机数据没有含已完成统筹事项的发布会项目。')
  if (!projectId) return

  await page.goto(`/projects/${projectId}`, { waitUntil: 'networkidle' })
  await page.getByRole('tab', { name: /执行清单/ }).click()

  const completedItem = page
    .locator('.conference-work-list article')
    .filter({ has: page.getByText('事项已完成，状态已锁定。', { exact: true }) })
    .first()
  await expect(completedItem).toBeVisible()
  await expect(completedItem.locator('select')).toHaveCount(0)
  await expect(completedItem.getByRole('button', { name: '保存事项' })).toHaveCount(0)
  await assertPageIntegrity(page)
  await page.screenshot({
    path: testInfo.outputPath('conference-completed-work-item-locked.png'),
    fullPage: true,
  })
})

test('已登记结果的发布会候选在媒体名单中保持只读锁定', async ({ page }, testInfo) => {
  const credentials = await loadAdminCredentials()
  test.skip(
    !credentials,
    '请设置 WINPRESS_E2E_ADMIN_USERNAME 和 WINPRESS_E2E_ADMIN_PASSWORD 后执行管理员回归。',
  )
  if (!credentials) return

  await signInForNavigationAudit(page, credentials)
  const candidate = await page.evaluate(async () => {
    const token = localStorage.getItem('winpress_token')
    const headers = token ? { Authorization: `Bearer ${token}` } : {}
    const response = await fetch(
      '/api/v1/projects?serviceType=NEWS_CONFERENCE&page=1&pageSize=100',
      { headers },
    )
    const payload = await response.json()
    for (const item of payload?.data?.items || []) {
      const detailResponse = await fetch(`/api/v1/projects/${item.id}`, { headers })
      if (!detailResponse.ok) continue
      const detailPayload = await detailResponse.json()
      const target = (detailPayload?.data?.conferenceMediaCandidates || []).find(
        (mediaCandidate: {
          id?: number
          displayName?: string
          reporterName?: string
          status?: string
        }) => ['ATTENDING', 'DECLINED', 'NOT_PROCEEDING'].includes(mediaCandidate.status || ''),
      )
      if (target?.id) {
        return {
          projectId: item.id,
          name: target.reporterName || target.displayName,
        }
      }
    }
    return null
  })
  test.skip(!candidate, '当前本机数据没有已登记结果的发布会候选。')
  if (!candidate) return

  await page.goto(`/projects/${candidate.projectId}`, { waitUntil: 'networkidle' })
  await page.getByRole('tab', { name: /媒体名单/ }).click()

  const lockedCandidate = page
    .locator('.conference-media-item')
    .filter({ has: page.getByText('邀约结果已记录，状态已锁定。', { exact: true }) })
    .filter({ has: page.getByText(candidate.name, { exact: true }) })
    .first()
  await expect(lockedCandidate).toBeVisible()
  await expect(lockedCandidate.locator('select')).toHaveCount(0)
  await expect(lockedCandidate.getByRole('button', { name: '保存进度' })).toHaveCount(0)
  await assertPageIntegrity(page)
  await page.screenshot({
    path: testInfo.outputPath('conference-candidate-locked.png'),
    fullPage: true,
  })
})

test('直编目录高级筛选保持参考字段与经营信息边界', async ({ page }) => {
  const credentials = await loadAdminCredentials()
  test.skip(
    !credentials,
    '请设置 WINPRESS_E2E_ADMIN_USERNAME 和 WINPRESS_E2E_ADMIN_PASSWORD 后执行目录回归。',
  )
  if (!credentials) return

  await page.goto('/login', { waitUntil: 'networkidle' })
  await page.locator('input[autocomplete="username"]').fill(credentials.username)
  await page.locator('input[autocomplete="current-password"]').fill(credentials.password)
  await page.getByRole('button', { name: /^登录$/ }).click()
  await expect(page).toHaveURL(/\/dashboard$/)

  const result = await page.evaluate(async () => {
    const token = localStorage.getItem('winpress_token')
    const headers = token ? { Authorization: `Bearer ${token}` } : {}
    const taxonomyResponse = await fetch('/api/v1/channels/taxonomy?type=DIRECT_PUBLISHING', {
      headers,
    })
    const taxonomyPayload = await taxonomyResponse.json()
    const taxonomy = taxonomyPayload?.data || {}
    const linkType = taxonomy?.linkTypes?.[0] || ''
    if (!taxonomyResponse.ok || !linkType) {
      return {
        ok: false,
        reason: '目录分类为空或接口不可用',
        taxonomy,
        items: [],
      }
    }

    const channelsResponse = await fetch(
      `/api/v1/channels?type=DIRECT_PUBLISHING&link_type=${encodeURIComponent(linkType)}&page=1&pageSize=20`,
      { headers },
    )
    const channelsPayload = await channelsResponse.json()
    return {
      ok: channelsResponse.ok,
      linkType,
      taxonomy,
      items: channelsPayload?.data?.items || [],
    }
  })

  expect(result.ok, result.reason || '直编目录筛选接口不可用').toBeTruthy()
  expect(result.taxonomy.linkTypes.length).toBeGreaterThan(0)
  expect(result.taxonomy.newsSources.length).toBeGreaterThan(0)
  expect(result.taxonomy.entryLevels.length).toBeGreaterThan(0)
  expect(result.taxonomy.specialIndustries.length).toBeGreaterThan(0)
  expect(result.taxonomy.weekendPolicies.length).toBeGreaterThan(0)
  expect(result.items.length).toBeGreaterThan(0)
  expect(
    result.items.every((item: { linkType?: string }) => item.linkType === result.linkType),
  ).toBeTruthy()

  const forbiddenKeys = new Set([
    'supplier',
    'supplierId',
    'supplierName',
    'costPrice',
    'supplierPrice',
    'upstream',
    'token',
    'secret',
    'channelNo',
  ])
  for (const item of result.items) {
    expect(Object.keys(item).filter((key) => forbiddenKeys.has(key))).toEqual([])
  }
})

test('客户可在直编发稿页展开并使用高级筛选', async ({ page }) => {
  const credentials = await loadCustomerCredentials()
  test.skip(
    !credentials,
    '请设置 WINPRESS_E2E_CUSTOMER_USERNAME 和 WINPRESS_E2E_CUSTOMER_PASSWORD 后执行客户页面回归。',
  )
  if (!credentials) return

  await page.goto('/login', { waitUntil: 'networkidle' })
  await page.locator('input[autocomplete="username"]').fill(credentials.username)
  await page.locator('input[autocomplete="current-password"]').fill(credentials.password)
  await page.getByRole('button', { name: /^登录$/ }).click()
  await expect(page).toHaveURL(/\/dashboard$/)

  await page.goto('/direct-publishing', { waitUntil: 'networkidle' })
  await expect(page.getByRole('heading', { name: '直编发稿', exact: true })).toBeVisible()
  await expect(page.getByText('这不是实时媒体库存或媒体发布承诺')).toBeVisible()
  await page.locator('.channel-advanced-filters summary').click()

  const linkTypeFilter = page.getByLabel('按链接类型筛选')
  await expect(linkTypeFilter).toBeVisible()
  await expect(page.getByLabel('按新闻源参考筛选')).toBeVisible()
  await expect(page.getByLabel('按入口级别筛选')).toBeVisible()
  await expect(page.getByLabel('按特殊行业要求筛选')).toBeVisible()
  await expect(page.getByLabel('按周末发布参考筛选')).toBeVisible()

  const optionValue = await linkTypeFilter.locator('option').nth(1).getAttribute('value')
  expect(optionValue).toBeTruthy()
  if (!optionValue) return
  await linkTypeFilter.selectOption(optionValue)
  await expect(page.locator('.direct-channel-table tbody tr').first()).toBeVisible()
  await expect(page.locator('.channel-reference-tags').first()).toContainText(optionValue)

  const dimensions = await page.evaluate(() => ({
    clientWidth: document.documentElement.clientWidth,
    scrollWidth: document.documentElement.scrollWidth,
  }))
  expect(dimensions.scrollWidth).toBeLessThanOrEqual(dimensions.clientWidth + 1)
  await expect(page.locator('body')).not.toContainText('供应商成本')
})

test('未验收媒体资料时，客户媒体邀请保留人工补充与核验路径', async ({ page }, testInfo) => {
  const credentials = await loadCustomerCredentials()
  test.skip(
    !credentials,
    '请设置 WINPRESS_E2E_CUSTOMER_USERNAME 和 WINPRESS_E2E_CUSTOMER_PASSWORD 后执行客户页面回归。',
  )
  if (!credentials) return

  await signInForNavigationAudit(page, credentials)
  await page.goto('/media-invitation', { waitUntil: 'networkidle' })

  await expect(page).toHaveURL(/\/media-invitation$/)
  await expect(page.getByRole('heading', { name: '媒体邀请', exact: true })).toBeVisible()
  await expect(
    page.getByText('在线筛选暂不可用，可人工补充候选名单，由项目负责人核验。'),
  ).toBeVisible()
  await expect(page.getByRole('button', { name: '人工补充拟邀对象' })).toBeVisible()

  // The customer workflow is media first, then reporters within the selected media.
  // It must not regress to two parallel entry cards while the external directory remains unverified.
  await expect(page.getByRole('button', { name: '按媒体筛选' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '按记者筛选' })).toHaveCount(0)
  await expect(page.locator('body')).not.toContainText('供应商成本')
  await expect(page.locator('body')).not.toContainText('令牌')

  const dimensions = await page.evaluate(() => ({
    clientWidth: document.documentElement.clientWidth,
    scrollWidth: document.documentElement.scrollWidth,
  }))
  expect(dimensions.scrollWidth).toBeLessThanOrEqual(dimensions.clientWidth + 1)
  await page.screenshot({
    path: testInfo.outputPath('media-invitation-safe-fallback.png'),
    fullPage: true,
  })
})

test('举办新闻发布会保留三项必填、同页筛选与独立服务边界', async ({ page }, testInfo) => {
  const credentials = await loadCustomerCredentials()
  test.skip(
    !credentials,
    '请设置 WINPRESS_E2E_CUSTOMER_USERNAME 和 WINPRESS_E2E_CUSTOMER_PASSWORD 后执行客户页面回归。',
  )
  if (!credentials) return

  await signInForNavigationAudit(page, credentials)
  await page.goto('/requirements/news-conference', { waitUntil: 'networkidle' })

  await expect(page.locator('h1')).toHaveText('举办新闻发布会')
  await expect(page.getByRole('heading', { name: '服务类型', exact: true })).toHaveCount(0)
  await expect(page.locator('.service-choice-grid')).toHaveCount(0)
  await expect(page.getByRole('heading', { name: '项目信息', exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: '媒体筛选', exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: '需求说明', exact: true })).toBeVisible()

  const requiredFields = await page
    .locator(
      '.order-form input[required], .order-form select[required], .order-form textarea[required]',
    )
    .evaluateAll((fields) =>
      fields.map((field) => field.closest('label')?.textContent?.replace(/\s+/g, ' ').trim() || ''),
    )
  expect(requiredFields).toHaveLength(3)
  expect(requiredFields.join('|')).toContain('需求标题')
  expect(requiredFields.join('|')).toContain('会务联系人')
  expect(requiredFields.join('|')).toContain('联系电话')

  const projectInformation = page
    .locator('.form-section')
    .filter({ has: page.getByRole('heading', { name: '项目信息', exact: true }) })
  const formBlockOrder = await projectInformation.locator('.form-grid > *').allTextContents()
  const mediaGoalIndex = formBlockOrder.findIndex((text) => text.includes('媒体与传播目标'))
  const uploadIndex = formBlockOrder.findIndex((text) => text.includes('上传选题资料'))
  expect(mediaGoalIndex).toBeGreaterThanOrEqual(0)
  expect(uploadIndex).toBeGreaterThan(mediaGoalIndex)

  await expect(page.locator('body')).not.toContainText('筛选发布会拟邀名单')
  await expect(page.locator('body')).not.toContainText('先按活动议题、行业和地区筛出候选')
  await expect(page.locator('body')).not.toContainText('先输入议题，也可以直接点选条件')
  await expect(page.getByText('其他传播服务分别下单', { exact: true })).toBeVisible()

  const dimensions = await page.evaluate(() => ({
    clientWidth: document.documentElement.clientWidth,
    scrollWidth: document.documentElement.scrollWidth,
  }))
  expect(dimensions.scrollWidth).toBeLessThanOrEqual(dimensions.clientWidth + 1)
  await page.screenshot({ path: testInfo.outputPath('news-conference-intake.png'), fullPage: true })
})

test('平台运营可追溯供应商订单履约，移动端表单标题保持完整', async ({ page }, testInfo) => {
  const credentials = await loadAdminCredentials()
  test.skip(
    !credentials,
    '请设置 WINPRESS_E2E_ADMIN_USERNAME 和 WINPRESS_E2E_ADMIN_PASSWORD 后执行供应商履约界面回归。',
  )
  if (!credentials) return

  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  page.on('console', (message) => {
    if (message.type() === 'error') errors.push(message.text())
  })

  await signInForNavigationAudit(page, credentials)
  await page.goto('/admin/suppliers?tab=orders', { waitUntil: 'networkidle' })
  await expect(page.locator('h1')).toHaveText('供应商与订单')
  await expect(page.getByRole('button', { name: '供应商订单', exact: true })).toHaveClass(/active/)
  const orderSearch = page.getByPlaceholder('任务号、计划号、项目号、订单号、渠道或供应商')
  await expect(orderSearch).toBeVisible()

  const firstOrderRow = page.locator('tbody tr').first()
  await expect(firstOrderRow).toBeVisible()
  const taskNo = (await firstOrderRow.locator('td').first().locator('small').first().textContent())
    ?.split('·')[0]
    ?.trim()
  expect(taskNo).toMatch(/^PUB-/)
  await orderSearch.fill(taskNo || '')
  await page.getByRole('button', { name: '筛选', exact: true }).click()
  await expect(page.locator('tbody tr').filter({ hasText: taskNo || '' })).toHaveCount(1)

  await orderSearch.fill('')
  await page.getByRole('button', { name: '筛选', exact: true }).click()

  const pendingOrder = page.locator('tbody tr').filter({ hasText: '待提交' }).first()
  await expect(pendingOrder).toBeVisible()
  const candidateRequest = page.waitForRequest((request) => {
    const url = new URL(request.url())
    return (
      request.method() === 'GET' &&
      url.pathname.endsWith('/api/v1/admin/suppliers/options') &&
      Boolean(url.searchParams.get('channelId'))
    )
  })
  await pendingOrder.getByRole('button', { name: '处理', exact: true }).click()
  await candidateRequest

  const modal = page.locator('.modal-panel').filter({ hasText: '履约轨迹' })
  await expect(modal).toHaveCount(1)
  await expect(modal.getByRole('heading', { name: '履约轨迹', exact: true })).toBeVisible()
  const supplierLabel = modal
    .locator('label')
    .filter({ hasText: /^供应商/ })
    .first()
  await expect(supplierLabel.getByText('仅展示已关联当前渠道的可用供应商。')).toBeVisible()
  await expect(supplierLabel.locator('select')).toBeEnabled()
  expect(await supplierLabel.locator('option').allTextContents()).toEqual(
    expect.arrayContaining(['待分配', '本机演示直编服务商']),
  )
  await expect(modal.getByLabel('履约记录方式')).toBeDisabled()
  await expect(modal.getByLabel('上游订单号')).toHaveCount(0)

  const statusLabel = modal.locator('label').filter({ hasText: '订单状态' })
  await expect(statusLabel).toHaveCount(1)
  await expect(statusLabel.locator('.field-label')).toHaveText('订单状态*')
  await expect(statusLabel.locator('select option')).toHaveText(['待提交', '已提交', '已取消'])
  const labelGeometry = await statusLabel.evaluate((label) => {
    const title = label.querySelector('.field-label')
    const mark = title?.querySelector('.required')
    if (!title || !mark) return null
    const titleRect = title.getBoundingClientRect()
    const markRect = mark.getBoundingClientRect()
    return {
      sameLine: Math.abs(titleRect.top - markRect.top) < 1,
      titleWidth: titleRect.width,
      markLeft: markRect.left,
    }
  })
  expect(labelGeometry).toMatchObject({ sameLine: true })
  expect(labelGeometry?.markLeft || 0).toBeGreaterThan(0)

  const dimensions = await page.evaluate(() => ({
    clientWidth: document.documentElement.clientWidth,
    scrollWidth: document.documentElement.scrollWidth,
  }))
  expect(dimensions.scrollWidth).toBeLessThanOrEqual(dimensions.clientWidth + 1)
  expect(errors).toEqual([])
  await page.screenshot({
    path: testInfo.outputPath('supplier-order-fulfillment.png'),
    fullPage: true,
  })
})

test('客户任务记录与订单管理各自聚焦且不暴露内部经营字段', async ({ page }, testInfo) => {
  const credentials = await loadCustomerCredentials()
  test.skip(
    !credentials,
    '请设置 WINPRESS_E2E_CUSTOMER_USERNAME 和 WINPRESS_E2E_CUSTOMER_PASSWORD 后执行客户页面回归。',
  )
  if (!credentials) return

  await signInForNavigationAudit(page, credentials)
  await page.goto('/orders', { waitUntil: 'networkidle' })
  await expect(page.locator('h1')).toHaveText('任务记录')
  await expect(page.getByRole('heading', { name: '任务明细', exact: true })).toBeVisible()
  await expect(
    page.getByRole('main').getByRole('link', { name: '项目管理', exact: true }),
  ).toBeVisible()
  await expect(page.getByRole('link', { name: '查看当前待办' })).toBeVisible()
  await expect(page.locator('body')).not.toContainText('项目台账')
  await expect(page.locator('body')).not.toContainText('项目清单')
  await expect(page.locator('body')).not.toContainText('筛选媒体')
  await expect(page.locator('body')).not.toContainText('供应商成本')
  await expect(page.locator('body')).not.toContainText('上游接口')
  await page.screenshot({ path: testInfo.outputPath('task-records.png'), fullPage: true })

  await page.goto('/order-management', { waitUntil: 'networkidle' })
  await expect(page.locator('h1')).toHaveText('订单管理')
  await expect(page.getByRole('heading', { name: '服务订单', exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: '交易记录', exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: '结算记录', exact: true })).toBeVisible()

  const serviceOptions = await page.getByLabel('按服务类别筛选').locator('option').allTextContents()
  expect(serviceOptions).toEqual(
    expect.arrayContaining(['云采写', '媒体邀请', '直编发稿', '举办新闻发布会']),
  )
  await expect(page.locator('body')).not.toContainText('供应商成本')
  await expect(page.locator('body')).not.toContainText('上游接口')

  const dimensions = await page.evaluate(() => ({
    clientWidth: document.documentElement.clientWidth,
    scrollWidth: document.documentElement.scrollWidth,
  }))
  expect(dimensions.scrollWidth).toBeLessThanOrEqual(dimensions.clientWidth + 1)
  await page.screenshot({ path: testInfo.outputPath('order-management.png'), fullPage: true })
})

test('订单管理在浏览器历史切换时同步服务筛选', async ({ page }, testInfo) => {
  const credentials = await loadCustomerCredentials()
  test.skip(
    !credentials,
    '请设置 WINPRESS_E2E_CUSTOMER_USERNAME 和 WINPRESS_E2E_CUSTOMER_PASSWORD 后执行订单筛选回归。',
  )
  if (!credentials) return

  await signInForNavigationAudit(page, credentials)
  await page.goto('/order-management?serviceType=ONSITE_WRITING', { waitUntil: 'networkidle' })

  const serviceFilter = page.getByLabel('按服务类别筛选')
  await expect(serviceFilter).toHaveValue('ONSITE_WRITING')

  const mediaOrderRequest = page.waitForRequest((request) => {
    const url = new URL(request.url())
    return (
      url.pathname.endsWith('/api/v1/order-records') &&
      url.searchParams.get('serviceType') === 'MEDIA_PR'
    )
  })
  await page.evaluate(() => {
    window.history.pushState({}, '', '/order-management?serviceType=MEDIA_PR')
    window.dispatchEvent(new PopStateEvent('popstate', { state: window.history.state }))
  })

  await mediaOrderRequest
  await expect(serviceFilter).toHaveValue('MEDIA_PR')
  await assertPageIntegrity(page)
  await page.screenshot({
    path: testInfo.outputPath('order-management-history-filter.png'),
    fullPage: true,
  })
})
