<template>
  <div class="statistics-chart">
    <div class="chart-section">
      <h3>各分类正确率</h3>
      <div ref="pieChart" class="chart"></div>
    </div>
    
    <div class="chart-section">
      <h3>近期正确率趋势</h3>
      <div ref="lineChart" class="chart"></div>
    </div>
  </div>
</template>

<script setup>import { ref, onMounted, watch, onUnmounted } from 'vue';
import * as echarts from 'echarts';
const props = defineProps({
 categoryData: {
 type: Array,
 default: () => []
 },
 trendData: {
 type: Array,
 default: () => []
 }
});
const pieChart = ref(null);
const lineChart = ref(null);
let pieInstance = null;
let lineInstance = null;
const initPieChart = () => {
 if (!pieChart.value)
 return;
 pieInstance = echarts.init(pieChart.value);
 const data = props.categoryData.map(item => ({
 name: item.categoryName,
 value: item.correctRate
 }));
 const option = {
 tooltip: {
 trigger: 'item',
 formatter: '{a} <br/>{b}: {c}% ({d}%)'
 },
 legend: {
 orient: 'horizontal',
 bottom: 10
 },
 series: [{
 name: '正确率',
 type: 'pie',
 radius: ['40%', '70%'],
 avoidLabelOverlap: false,
 itemStyle: {
 borderRadius: 10,
 borderColor: '#fff',
 borderWidth: 2
 },
 label: {
 show: true,
 formatter: '{b}\n{c}%'
 },
 emphasis: {
 label: {
 show: true,
 fontSize: 16,
 fontWeight: 'bold'
 },
 itemStyle: {
 shadowBlur: 10,
 shadowOffsetX: 0,
 shadowColor: 'rgba(0, 0, 0, 0.5)'
 }
 },
 data: data,
 color: ['#5470c6', '#91cc75', '#fac858', '#ee6666', '#73c0de']
 }]
 };
 pieInstance.setOption(option);
};
const initLineChart = () => {
 if (!lineChart.value)
 return;
 lineInstance = echarts.init(lineChart.value);
 const dates = props.trendData.map(item => item.date) || [];
 const rates = props.trendData.map(item => item.correctRate) || [];
 const option = {
 tooltip: {
 trigger: 'axis',
 formatter: '{b}<br/>正确率: {c}%'
 },
 grid: {
 left: '3%',
 right: '4%',
 bottom: '3%',
 containLabel: true
 },
 xAxis: {
 type: 'category',
 boundaryGap: false,
 data: dates,
 axisLabel: {
 color: '#666',
 rotate: 30
 }
 },
 yAxis: {
 type: 'value',
 min: 0,
 max: 100,
 axisLabel: {
 formatter: '{value}%',
 color: '#666'
 }
 },
 series: [{
 name: '正确率',
 type: 'line',
 smooth: true,
 symbol: 'circle',
 symbolSize: 8,
 data: rates,
 lineStyle: {
 width: 3,
 color: '#5470c6'
 },
 itemStyle: {
 color: '#5470c6',
 borderWidth: 2,
 borderColor: '#fff'
 },
 areaStyle: {
 color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
 { offset: 0, color: 'rgba(84, 112, 198, 0.3)' },
 { offset: 1, color: 'rgba(84, 112, 198, 0.05)' }
 ])
 },
 emphasis: {
 itemStyle: {
 color: '#5470c6',
 borderWidth: 3,
 shadowBlur: 10
 }
 }
 }]
 };
 lineInstance.setOption(option);
};
const handleResize = () => {
 pieInstance?.resize();
 lineInstance?.resize();
};
watch(() => props.categoryData, () => {
 initPieChart();
}, { deep: true });
watch(() => props.trendData, () => {
 initLineChart();
}, { deep: true });
onMounted(() => {
 initPieChart();
 initLineChart();
 window.addEventListener('resize', handleResize);
});
onUnmounted(() => {
 window.removeEventListener('resize', handleResize);
 pieInstance?.dispose();
 lineInstance?.dispose();
});
</script>

<style scoped>
.statistics-chart {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(400px, 1fr));
  gap: 20px;
  padding: 20px;
}

.chart-section {
  background: white;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
}

.chart-section h3 {
  color: #333;
  margin-bottom: 20px;
  font-size: 18px;
}

.chart {
  height: 300px;
}
</style>
