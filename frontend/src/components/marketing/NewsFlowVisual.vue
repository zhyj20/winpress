<script setup lang="ts">
import { ArrowRight, FileCheck2, RadioTower } from 'lucide-vue-next'
</script>

<template>
  <div class="home-news-flow" aria-label="企业新闻传播链路">
    <div class="home-news-inputs">
      <small>企业事件</small><span>新品发布</span><span>战略合作</span><span>品牌故事</span>
    </div>
    <ArrowRight class="home-flow-arrow" :size="23" />
    <div class="home-news-stage is-writing">
      <span><FileCheck2 :size="22" /></span><small>内容生产</small><strong>云采写</strong>
      <p>事实梳理 · 现场采写 · 客户定稿</p>
    </div>
    <ArrowRight class="home-flow-arrow" :size="23" />
    <div class="home-news-stage is-publishing">
      <span><RadioTower :size="22" /></span><small>传播执行</small><strong>云发布</strong>
      <p>媒体邀请 · 渠道发布 · 结果归档</p>
    </div>
  </div>
</template>
