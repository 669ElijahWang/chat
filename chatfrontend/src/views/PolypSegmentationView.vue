<template>
  <div class="polyp-page">
    <div class="header">
      <h2>息肉分割</h2>
      <span class="desc">上传结直肠息肉图片，调用 mdfra 分割并展示结果</span>
    </div>

    <el-card class="upload-card">
      <div class="upload-area">
        <el-upload
          class="uploader"
          drag
          :auto-upload="false"
          :show-file-list="false"
          :on-change="onFileChange"
          accept="image/*"
        >
          <el-icon class="el-icon--upload"><Upload /></el-icon>
          <div class="el-upload__text">拖拽图片到此处，或点击上传</div>
          <div class="el-upload__tip">支持 JPG/PNG，最大 10MB</div>
        </el-upload>

        <div class="actions">
          <el-button type="primary" :disabled="!selectedFile" :loading="processing" @click="handleSegment">开始分割</el-button>
          <el-button @click="reset">重置</el-button>
        </div>
      </div>
    </el-card>

    <div v-if="previewUrl || overlayUrl" class="result-grid">
      <el-card class="result-card">
        <template #header>
          <span>原图预览</span>
        </template>
        <div class="img-wrap" v-if="previewUrl">
          <img :src="previewUrl" alt="原图" />
        </div>
      </el-card>

      <el-card class="result-card">
        <template #header>
          <span>分割掩码</span>
        </template>
        <div class="img-wrap" v-if="maskUrl">
          <img :src="maskUrl" alt="掩码" />
        </div>
      </el-card>

      <el-card class="result-card">
        <template #header>
          <span>叠加效果</span>
        </template>
        <div class="img-wrap" v-if="overlayUrl">
          <img :src="overlayUrl" alt="叠加" />
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Upload } from '@element-plus/icons-vue'
import { segmentationAPI } from '@/api/segmentation'

const selectedFile = ref(null)
const previewUrl = ref('')
const maskUrl = ref('')
const overlayUrl = ref('')
const processing = ref(false)

function onFileChange(file) {
  selectedFile.value = file.raw
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = URL.createObjectURL(file.raw)
  maskUrl.value = ''
  overlayUrl.value = ''
}

async function handleSegment() {
  if (!selectedFile.value) return
  processing.value = true
  try {
    const form = new FormData()
    form.append('file', selectedFile.value)
    const res = await segmentationAPI.segmentPolyp(form)
    const data = res.data || {}
    maskUrl.value = data.maskBase64 || ''
    overlayUrl.value = data.overlayBase64 || ''
    if (!overlayUrl.value && !maskUrl.value) ElMessage.warning('分割结果为空')
  } catch (e) {
    ElMessage.error('分割失败')
  } finally {
    processing.value = false
  }
}

function reset() {
  selectedFile.value = null
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = ''
  maskUrl.value = ''
  overlayUrl.value = ''
}
</script>

<style scoped>
.polyp-page { padding: 24px; }
.header { display: flex; align-items: baseline; gap: 12px; margin-bottom: 16px; }
.header h2 { margin: 0; }
.desc { color: #666; font-size: 14px; }
.upload-card { margin-bottom: 16px; }
.upload-area { display: flex; align-items: center; justify-content: space-between; gap: 24px; }
.uploader { width: 420px; }
.actions { display: flex; gap: 12px; }
.result-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
.result-card { min-height: 300px; }
.img-wrap { display: flex; align-items: center; justify-content: center; height: 320px; background: #fff; }
.img-wrap img { max-width: 100%; max-height: 100%; object-fit: contain; }
</style>