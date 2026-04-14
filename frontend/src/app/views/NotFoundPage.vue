<template>
  <div class="page-stack">
    <section class="panel-card empty-state">
      <h2>页面不存在</h2>
      <p>你访问的地址没有对应页面，返回首页继续使用系统。</p>
      <RouterLink class="primary-btn" to="/dashboard">回到首页</RouterLink>
    </section>
  </div>
</template>

<script setup lang="ts">
import { RouterLink } from 'vue-router'
</script>
