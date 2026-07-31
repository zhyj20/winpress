<template>
  <footer class="mkt-footer">
    <div class="mkt-shell">
      <div class="mkt-footer-top">
        <div class="mkt-footer-brand">
          <img src="/winpress-logo.png" alt="WinPress Logo" />
          <p>为企业提供现场采写、媒体邀请、直编发稿和新闻发布会项目管理。</p>
        </div>
        <div>
          <strong>传播服务</strong>
          <RouterLink to="/requirements/cloud-writing">现场采写</RouterLink>
          <RouterLink to="/media-invitation">媒体邀请</RouterLink>
          <RouterLink to="/direct-publishing">直编发稿</RouterLink>
          <RouterLink to="/api-integration">API接入</RouterLink>
        </div>
        <div>
          <strong>了解云发布</strong>
          <RouterLink to="/insights">行业资讯</RouterLink>
          <RouterLink to="/methodology">方法论</RouterLink>
          <RouterLink to="/cases">案例展示</RouterLink>
          <RouterLink to="/about">关于我们</RouterLink>
          <RouterLink to="/case-evidence">案例与证据</RouterLink>
        </div>
        <div>
          <strong>使用与联系</strong>
          <RouterLink to="/requirements/new">创建传播需求</RouterLink>
          <RouterLink to="/login">登录操作台</RouterLink>
          <RouterLink to="/contact">联系平台</RouterLink>
          <RouterLink to="/contact?type=MEDIA_PARTNERSHIP">媒体合作申请</RouterLink>
          <RouterLink to="/service-boundaries">服务边界</RouterLink>
        </div>
        <div>
          <strong>规则</strong>
          <RouterLink to="/privacy">数据处理说明（待确认）</RouterLink>
          <RouterLink to="/terms">服务条款（待确认）</RouterLink>
          <RouterLink to="/legal">法律与经营信息</RouterLink>
        </div>
      </div>
      <div class="mkt-footer-bottom">
        <span>© 2026 WinPress 云发布</span>
        <span>正式运营主体及备案信息待核验后披露</span>
      </div>
    </div>
  </footer>
</template>
