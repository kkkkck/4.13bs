<template>
  <div class="practice-history">
    <h3>练习历史</h3>
    
    <div v-if="historyList.length > 0" class="history-list">
      <div class="history-item" v-for="item in historyList" :key="item.id">
        <div class="history-header">
          <span class="category-name">{{ getCategoryName(item.categoryId) }}</span>
          <span class="practice-time">{{ formatTime(item.createdAt) }}</span>
        </div>
        
        <div class="history-stats">
          <div class="stat-item">
            <span class="stat-label">总题数</span>
            <span class="stat-value">{{ item.totalQuestions }}</span>
          </div>
          <div class="stat-item">
            <span class="stat-label">答对</span>
            <span class="stat-value correct">{{ item.correctCount }}</span>
          </div>
          <div class="stat-item">
            <span class="stat-label">正确率</span>
            <span class="stat-value rate">{{ getCorrectRate(item) }}%</span>
          </div>
          <div class="stat-item">
            <span class="stat-label">用时</span>
            <span class="stat-value">{{ formatDuration(item.duration) }}</span>
          </div>
        </div>
        
        <div class="progress-bar">
          <div class="progress-fill" :style="{ width: getCorrectRate(item) + '%' }"></div>
        </div>
      </div>
    </div>
    
    <div v-else class="empty-state">
      <p>暂无练习记录</p>
    </div>
  </div>
</template>

<script setup>
defineProps({
  historyList: {
    type: Array,
    default: () => []
  }
})

const getCategoryName = (categoryId) => {
  const categories = {
    1: '数学',
    2: '英语',
    3: '编程'
  }
  return categories[categoryId] || '未知分类'
}

const formatTime = (time) => {
  if (!time) return ''
  const date = new Date(time)
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const formatDuration = (seconds) => {
  if (!seconds) return '0分钟'
  const mins = Math.floor(seconds / 60)
  const secs = seconds % 60
  if (mins > 0) {
    return `${mins}分${secs}秒`
  }
  return `${secs}秒`
}

const getCorrectRate = (item) => {
  if (!item.totalQuestions || item.totalQuestions === 0) return 0
  return Math.round(item.correctCount * 100 / item.totalQuestions)
}
</script>

<style scoped>
.practice-history {
  background: white;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
}

.practice-history h3 {
  color: #333;
  margin-bottom: 20px;
  font-size: 18px;
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.history-item {
  padding: 16px;
  background: #f8f9fa;
  border-radius: 8px;
}

.history-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.category-name {
  font-weight: bold;
  color: #333;
}

.practice-time {
  font-size: 14px;
  color: #666;
}

.history-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 12px;
}

.stat-item {
  text-align: center;
}

.stat-label {
  display: block;
  font-size: 12px;
  color: #999;
  margin-bottom: 4px;
}

.stat-value {
  font-size: 16px;
  font-weight: bold;
  color: #333;
}

.stat-value.correct {
  color: #28a745;
}

.stat-value.rate {
  color: #007bff;
}

.progress-bar {
  height: 6px;
  background: #e9ecef;
  border-radius: 3px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #28a745, #91cc75);
  border-radius: 3px;
  transition: width 0.3s ease;
}

.empty-state {
  text-align: center;
  padding: 40px;
  color: #666;
}
</style>
