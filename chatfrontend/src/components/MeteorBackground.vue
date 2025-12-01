<template>
  <div class="meteor-background">
    <div v-for="n in count" :key="n" class="meteor" :style="getMeteorStyle(n)"></div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  count: {
    type: Number,
    default: 20
  }
})

const getMeteorStyle = (n) => {
  const top = Math.floor(Math.random() * 100) + '%'
  const left = Math.floor(Math.random() * 100) + '%'
  const delay = Math.random() * 5 + 's'
  const duration = Math.random() * 2 + 2 + 's'
  
  return {
    top,
    left,
    animationDelay: delay,
    animationDuration: duration
  }
}
</script>

<style scoped>
.meteor-background {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  overflow: hidden;
  pointer-events: none;
  z-index: 0;
}

.meteor {
  position: absolute;
  width: 2px;
  height: 60px; /* Reduced length for a subtler effect */
  background: linear-gradient(to bottom, rgba(255, 255, 255, 0), rgba(255, 255, 255, 0.8));
  transform: rotate(215deg); /* Adjusted angle for better visual flow */
  opacity: 0;
  animation: meteor-fall linear infinite;
  box-shadow: 0 0 10px rgba(255, 255, 255, 0.5); /* Add glow */
}

@keyframes meteor-fall {
  0% {
    transform: translate(0, 0) rotate(215deg);
    opacity: 1;
  }
  100% {
    transform: translate(-500px, 500px) rotate(215deg); /* Adjusted trajectory */
    opacity: 0;
  }
}
</style>
