<template>
  <div class="page-stack">
    <section class="panel-card empty-state">
      <p class="eyebrow">404</p>
      <h2>页面不存在</h2>
      <p>这个地址没有对应页面。</p>
      <RouterLink class="primary-btn" to="/dashboard">返回首页</RouterLink>
    </section>
  </div>
</template>

<script setup lang="ts">
import { RouterLink } from 'vue-router'
</script>
