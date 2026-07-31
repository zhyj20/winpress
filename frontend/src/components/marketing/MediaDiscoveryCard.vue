<script setup lang="ts">
import { MapPin, Search, Sparkles } from 'lucide-vue-next'
import { mediaRecommendations } from '@/marketing/content/home'
</script>

<template>
  <div class="home-media-discovery" aria-label="媒体匹配界面示例，非实时媒体数据">
    <div class="home-media-query">
      <Search :size="19" /><span>新能源汽车发布</span
      ><span class="home-media-example-tag">匹配示例</span>
    </div>
    <div class="home-media-recommend-label"><Sparkles :size="15" />传播方向示例（非实时数据）</div>
    <div class="home-media-results">
      <article v-for="item in mediaRecommendations" :key="item.group">
        <div>
          <strong>{{ item.group }}</strong
          ><span><MapPin :size="13" />{{ item.fields[1] }}</span>
        </div>
        <p>{{ item.reason }}</p>
        <div class="home-media-tags">
          <span v-for="field in item.fields" :key="field">{{ field }}</span>
        </div>
      </article>
    </div>
  </div>
</template>
