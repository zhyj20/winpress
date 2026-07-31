<script setup lang="ts">
import { CheckCircle2, CircleDashed, FileCheck2, RadioTower } from 'lucide-vue-next'
</script>

<template>
  <div class="mkt-dashboard-preview" aria-label="企业传播驾驶舱预览">
    <div class="mkt-dashboard-head">
      <div><small>项目 · 新品发布会</small><strong>传播执行进度</strong></div>
      <span>执行中</span>
    </div>
    <div class="mkt-dashboard-metrics">
      <article><small>现场采写</small><strong>已完成</strong></article>
      <article><small>客户定稿</small><strong>已确认</strong></article>
      <article><small>媒体发布</small><strong>执行中</strong></article>
    </div>
    <div class="mkt-dashboard-progress">
      <div>
        <CheckCircle2 :size="17" /><span>采访资料与现场采写</span
        ><strong class="is-done">已完成</strong>
      </div>
      <div>
        <FileCheck2 :size="17" /><span>稿件修改与客户确认</span
        ><strong class="is-done">已确认</strong>
      </div>
      <div>
        <RadioTower :size="17" /><span>媒体邀请与渠道发布</span
        ><strong class="is-purple">执行中</strong>
      </div>
      <div>
        <CircleDashed :size="17" /><span>发布链接与结果统计</span
        ><strong class="is-pending">待更新</strong>
      </div>
    </div>
  </div>
</template>
