<template>
  <div class="nocode-page">
    <!-- 顶部搜索与策略选择 -->
    <section class="hero">
      <h1>AI 网页生成平台</h1>
      <p class="subtitle">描述你想要构建的网页，AI 将自动选择合适的生成策略并以流式输出的方式实时执行。</p>

      <!-- 顶部右侧操作：历史记录 -->
      <div class="top-actions">
        <el-button type="info" plain @click="openHistory">历史记录</el-button>
      </div>

      <div class="prompt-bar">
        <el-input v-model="description" placeholder="例如：企业官网、产品展示页、在线商店、个人博客等" clearable />
        <el-button type="primary" :loading="generating" @click="startGenerate">开始生成</el-button>
        <el-button type="warning" @click="continueGenerate" :disabled="!conversationId || generating || !streamText">继续生成</el-button>
        <el-button @click="stopGenerate" :disabled="!generating">停止</el-button>
      </div>

      <div class="strategy-tags">
        <el-check-tag
          v-for="tag in strategyTags"
          :key="tag.value"
          :checked="strategy === tag.value"
          @change="() => strategy = tag.value"
        >{{ tag.label }}</el-check-tag>
      </div>
    </section>

    <!-- 历史记录弹窗 -->
    <el-dialog v-model="historyVisible" title="生成历史" width="80%" class="history-dialog">
      <div class="history-layout">
        <div class="history-left">
          <el-table :data="historyConversations" v-loading="historyLoading" style="width: 100%">
            <el-table-column prop="title" label="标题" width="360" />
            <el-table-column label="时间" width="180">
              <template #default="scope">{{ formatTime(scope.row.lastMessageAt || scope.row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作">
              <template #default="scope">
                <el-button size="small" @click="previewHistory(scope.row)">预览</el-button>
                <el-button size="small" @click="downloadHistory(scope.row)">下载HTML</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <div class="history-right">
          <iframe v-if="historyPreviewUrl" :src="historyPreviewUrl" class="preview-iframe"></iframe>
          <div v-else class="preview-placeholder">选择左侧记录进行预览</div>
        </div>
      </div>
    </el-dialog>

    <!-- 主体：左侧流式输出，右侧实时预览与编辑 -->
    <section class="workspace">
      <div class="left">
        <div class="panel">
          <div class="panel-header">
            <span>AI 执行过程（流式输出）</span>
            <div class="actions">
              <el-button size="small" @click="clearStream">清空</el-button>
            </div>
          </div>
          <div class="stream" ref="streamEl">
            <pre class="stream-content">{{ streamText }}</pre>
          </div>
        </div>

        <div class="panel edit-panel" v-if="editModeEnabled">
          <div class="panel-header">
            <span>编辑模式 · 选中元素对话修改</span>
          </div>
          <div class="edit-body">
            <div class="selected-info" v-if="selectedPath">
              <div class="info-line">选中：<code>{{ selectedPath }}</code></div>
              <div class="info-line">片段：<code class="snippet">{{ selectedOuterHtml }}</code></div>
            </div>
            <el-input
              v-model="editPrompt"
              type="textarea"
              :rows="3"
              placeholder="描述你希望修改的内容，例如：将标题改为‘创新驱动未来’，主按钮颜色改为蓝色并加上阴影"
            />
            <div class="edit-actions">
              <el-switch v-model="strictEdit" active-text="严格模式" inactive-text="AI模式" />
              <el-button type="primary" :loading="editing" :disabled="!selectedPath || !editPrompt" @click="applyAiEdit">应用修改</el-button>
              <el-button @click="cancelEdit">退出编辑</el-button>
            </div>
          </div>
        </div>
      </div>

      <div class="right">
        <div class="panel" v-loading="editing" element-loading-text="正在应用修改…" element-loading-background="rgba(255,255,255,0.6)">
          <div class="panel-header">
            <span>生成的应用 · 实时预览</span>
            <div class="actions">
              <el-switch v-model="editModeEnabled" active-text="编辑模式" @change="toggleEditMode" />
              <el-button size="small" @click="captureCover">截取封面</el-button>
              <el-button size="small" @click="downloadSource" :disabled="!indexHtml">下载源码</el-button>
            </div>
          </div>
          <div class="preview">
            <!-- 生成中：中央图片旋转遮罩 -->
            <div v-if="generating" class="gen-overlay">
              <img v-if="spinImageUrl" :src="spinImageUrl" class="spin-img" alt="生成中" @error="spinImageUrl = ''" />
              <div v-else class="default-spinner"></div>
              <div class="gen-text">正在生成页面，请稍候…</div>
            </div>
            <iframe v-if="previewUrl" :src="previewUrl" class="preview-iframe" ref="previewIframe"></iframe>
            <div v-else class="preview-placeholder">生成后将在此处实时展示网页</div>
          </div>
        </div>

        <div class="panel cover-panel" v-if="coverDataUrl">
          <div class="panel-header">
            <span>封面图预览</span>
            <div class="actions">
              <el-button size="small" @click="downloadCover">下载封面</el-button>
            </div>
          </div>
          <div class="cover-preview">
            <img :src="coverDataUrl" alt="封面图" />
          </div>
        </div>
      </div>
    </section>
  </div>
  
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { chatAPI } from '@/api/chat'

// 输入与策略
const description = ref('')
const strategy = ref('personal')
const strategyTags = [
  { label: '个人博客', value: 'personal' },
  { label: '企业官网', value: 'enterprise' },
  { label: '在线商店', value: 'commerce' },
  { label: '作品展示平台', value: 'portfolio' }
]

// 生成与流式输出
const generating = ref(false)
const editing = ref(false)
const streamEl = ref(null)
const streamText = ref('')
let streamCloser = null
let conversationId = null

const indexHtml = ref('')
const previewUrl = ref('')
const previewIframe = ref(null)
// 生成时旋转图片（放在 public 目录，如 /kun.png），为空则使用默认圆环
const spinImageUrl = ref('/kun.png')

// 编辑模式
const editModeEnabled = ref(false)
const selectedPath = ref('')
const selectedOuterHtml = ref('')
const editPrompt = ref('')
// 严格模式：识别“将A改为B/替换为”类指令，前端本地精确替换
const strictEdit = ref(true)

// 封面图
const coverDataUrl = ref('')

// 历史记录
const historyVisible = ref(false)
const historyLoading = ref(false)
const historyConversations = ref([])
const historyPreviewUrl = ref('')

function openHistory() {
  historyVisible.value = true
  loadHistory()
}

async function loadHistory() {
  historyLoading.value = true
  try {
    const res = await chatAPI.getConversations({ size: 100 })
    const list = res?.data?.content || []
    historyConversations.value = list
      .filter(c => /^AI应用生成\s*[:：]/.test((c?.title || '').trim()))
      .sort((a, b) => new Date(b.lastMessageAt || b.createdAt) - new Date(a.lastMessageAt || a.createdAt))
  } catch (e) {
    ElMessage.error('加载历史记录失败')
  } finally {
    historyLoading.value = false
  }
}

async function previewHistory(conv) {
  try {
    const res = await chatAPI.getMessages(conv.id)
    const msgs = res?.data || []
    // 从最后一条 AI 消息向前查找 HTML 代码块
    let html = ''
    for (let i = msgs.length - 1; i >= 0; i--) {
      const m = msgs[i]
      if (m.role === 'ASSISTANT' && m.content) {
        html = extractHtmlFromText(m.content)
        if (html) break
      }
    }
    if (!html) return ElMessage.warning('未找到该会话的 HTML 内容')
    if (historyPreviewUrl.value) URL.revokeObjectURL(historyPreviewUrl.value)
    const blob = new Blob([html], { type: 'text/html' })
    historyPreviewUrl.value = URL.createObjectURL(blob)
  } catch (e) {
    ElMessage.error('预览加载失败')
  }
}

async function downloadHistory(conv) {
  try {
    const res = await chatAPI.getMessages(conv.id)
    const msgs = res?.data || []
    let html = ''
    for (let i = msgs.length - 1; i >= 0; i--) {
      const m = msgs[i]
      if (m.role === 'ASSISTANT' && m.content) {
        html = extractHtmlFromText(m.content)
        if (html) break
      }
    }
    if (!html) return ElMessage.warning('未找到该会话的 HTML 内容')
    const blob = new Blob([html], { type: 'text/html' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'index.html'
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error('下载失败')
  }
}

function buildPrompt() {
  const base = `你是资深前端工程师，请根据下面需求生成一个单页网站，务必返回完整的 index.html 内容。要求：
1) 仅返回一个代码块，格式为 \`\`\`html 开头，\`\`\` 结尾；
2) 将 CSS 写在 <style> 中，将 JS 写在 <script> 中（不依赖打包器与第三方库）；
3) 使用响应式布局，并提供简洁美观的设计（Banner、导航、主要内容与页脚）；
4) 文案与结构请贴合需求与策略；
5) 不要解释文字，不要输出除代码以外的内容。`

  const strategyMap = {
    personal: '个人博客：包含首页、近期文章列表、关于我、联系入口。突出阅读体验与可分享性。',
    enterprise: '企业官网：包含公司介绍、产品/服务、优势亮点、客户案例与联系我们（按钮显眼）。',
    commerce: '在线商店：包含商品网格、横幅、分类筛选与加入购物车按钮（仅前端展示）。',
    portfolio: '作品展示平台：包含作品瀑布流、作品详情卡片、作者介绍与联系按钮。'
  }

  return `${base}\n\n【策略】${strategyMap[strategy.value]}\n【需求】${description.value || '请生成一个漂亮的示例页面'}`
}

function startGenerate() {
  if (!description.value) {
    ElMessage.warning('请先描述你要生成的应用')
    return
  }
  streamText.value = ''
  indexHtml.value = ''
  previewUrl.value = ''
  coverDataUrl.value = ''

  generating.value = true

  // 创建会话（标题可用用户输入）
  chatAPI.createConversation(`AI应用生成：${description.value}`).then(res => {
    conversationId = res.data.id
  const payload = {
    conversationId,
    content: buildPrompt(),
    model: 'deepseek-chat',
    temperature: 0.6,
    // 合理的最大输出 token，避免触发 400 错误
    maxTokens: 8000
  }

    // 流式发送
    const closer = chatAPI.sendMessageStream(payload, onStreamMessage, onStreamError, onStreamComplete)
    streamCloser = closer
  }).catch(err => {
    generating.value = false
    ElMessage.error('创建会话失败：' + (err?.message || '未知错误'))
  })
}

function stopGenerate() {
  try { streamCloser?.close?.() } catch {}
  generating.value = false
}

// 继续生成：基于现有会话与上下文，让模型从上次中断处补全
function continueGenerate() {
  if (!conversationId) {
    ElMessage.warning('当前没有可继续的会话')
    return
  }
  generating.value = true

  const hintPart = indexHtml.value
    ? '上次已生成部分 index.html，请从未完成处继续补全并闭合代码块。若需要重写，请直接返回完整 index.html。'
    : '上次输出被截断，请继续生成完整的 index.html。'

  const content = `请继续上一次的输出，${hintPart}\n\n要求：\n1) 只返回一个代码块（\`\`\`html 开头，\`\`\` 结尾）；\n2) 不要重复已输出的内容；\n3) 保证最终 HTML 完整可运行。`

  const payload = {
    conversationId,
    content,
    model: 'deepseek-chat',
    temperature: 0.6,
    maxTokens: 8000
  }

  const closer = chatAPI.sendMessageStream(payload, onStreamMessage, (err) => {
    generating.value = false
    ElMessage.error('继续生成失败：' + (err?.message || '未知错误'))
  }, () => {
    generating.value = false
    // 兜底：若仍未闭合代码块，尝试从全文提取
    if (!indexHtml.value) {
      const extracted = extractHtmlFromText(streamText.value)
      if (extracted) indexHtml.value = extracted
    }
    if (indexHtml.value) updatePreview()
  })
  streamCloser = closer
}

function onStreamMessage(chunk) {
  streamText.value += chunk
  // 自动滚动
  nextTick(() => {
    const el = streamEl.value
    if (el) el.scrollTop = el.scrollHeight
  })
  // 流式解析 html 代码块
  parseHtmlStream(chunk)
}

function onStreamError(err) {
  generating.value = false
  ElMessage.error('生成失败：' + (err?.message || '未知错误'))
}

function onStreamComplete() {
  generating.value = false
  // 若未成功抓取代码块，尝试兜底从整段文本提取 HTML
  if (!indexHtml.value) {
    const extracted = extractHtmlFromText(streamText.value)
    if (extracted) indexHtml.value = extracted
  }
  if (indexHtml.value) updatePreview()
}

// 逐块解析 “```html ... ```” 格式的代码
let htmlBuffer = ''
let inHtml = false
function parseHtmlStream(text) {
  // 简单状态机：进入、累积、退出
  if (!inHtml) {
    const idx = text.indexOf('```html')
    if (idx !== -1) {
      inHtml = true
      htmlBuffer += text.slice(idx + 7) // 跳过 ```html
      return
    }
  } else {
    const endIdx = text.indexOf('```')
    if (endIdx !== -1) {
      htmlBuffer += text.slice(0, endIdx)
      indexHtml.value = htmlBuffer
      inHtml = false
      htmlBuffer = ''
      updatePreview()
      return
    }
  }
  if (inHtml) htmlBuffer += text
}

// 兜底提取：当模型未闭合代码块或未使用 ```html 包裹时，尽量从全文提取 HTML
function extractHtmlFromText(text) {
  if (!text) return ''
  // 优先匹配代码块
  const fenced = text.match(/```html\s*([\s\S]*?)```/i)
  if (fenced && fenced[1]) return fenced[1]
  // 其次匹配完整 HTML 文档
  const fullDoc = text.match(/<!DOCTYPE html[\s\S]*?<html[\s\S]*?<\/html>/i) || text.match(/<html[\s\S]*?<\/html>/i)
  if (fullDoc && fullDoc[0]) return fullDoc[0]
  // 再次匹配主体片段并包裹骨架
  if (/(<div|<section|<header|<main|<footer|<h1|<h2|<p|<ul|<li|<nav|<style|<script)/i.test(text)) {
    return `<!DOCTYPE html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>AI 页面</title></head><body>${text}</body></html>`
  }
  return ''
}

function updatePreview() {
  try {
    // 释放旧 URL
    if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
    const blob = new Blob([indexHtml.value], { type: 'text/html' })
    previewUrl.value = URL.createObjectURL(blob)
    // 如果处于编辑模式，重新注入辅助脚本
    nextTick(() => {
      if (editModeEnabled.value) injectEditorBridge()
    })
  } catch (e) {
    console.error('预览构建失败', e)
    ElMessage.error('预览构建失败')
  }
}

function toggleEditMode(val) {
  if (!previewIframe.value) return
  if (val) {
    injectEditorBridge()
  } else {
    // 移除事件监听
    const win = previewIframe.value.contentWindow
    if (!win) return
    try { win.__aiDetach && win.__aiDetach() } catch {}
    selectedPath.value = ''
    selectedOuterHtml.value = ''
    editPrompt.value = ''
  }
}

function injectEditorBridge() {
  const iframe = previewIframe.value
  const win = iframe?.contentWindow
  const doc = win?.document
  if (!doc) return

  // 高亮样式
  const style = doc.createElement('style')
  style.textContent = `.ai-highlight{outline:2px solid #409EFF !important; cursor:pointer !important;}`
  doc.head.appendChild(style)

  let last = null
  const mouseover = (e) => {
    if (last) last.classList.remove('ai-highlight')
    last = e.target
    last.classList.add('ai-highlight')
  }
  const click = (e) => {
    e.preventDefault()
    e.stopPropagation()
    const node = e.target
    const path = cssPath(node)
    selectedPath.value = path
    selectedOuterHtml.value = node.outerHTML
  }

  doc.addEventListener('mouseover', mouseover, true)
  doc.addEventListener('click', click, true)
  win.__aiDetach = () => {
    doc.removeEventListener('mouseover', mouseover, true)
    doc.removeEventListener('click', click, true)
  }
}

// 生成简易 CSS 选择器路径
function cssPath(el) {
  if (!el || el.nodeType !== 1) return ''
  const parts = []
  while (el && el.nodeType === 1 && parts.length < 12) {
    let selector = el.tagName.toLowerCase()
    if (el.id) {
      selector += `#${el.id}`
      parts.unshift(selector)
      break
    } else {
      // nth-child
      const parent = el.parentElement
      if (!parent) break
      const children = Array.from(parent.children)
      const idx = children.indexOf(el) + 1
      selector += `:nth-child(${idx})`
    }
    parts.unshift(selector)
    el = el.parentElement
  }
  return parts.join(' > ')
}

function applyAiEdit() {
  if (!conversationId) return ElMessage.warning('当前未创建会话')
  if (!selectedPath.value) return ElMessage.warning('请先在预览中选中一个元素')
  if (!editPrompt.value) return ElMessage.warning('请输入修改说明')
  
  // 优先走严格模式：解析“将A改为B/替换为/replace X with Y”指令，直接在 indexHtml 中替换
  if (strictEdit.value) {
    const instr = parseReplaceInstruction(editPrompt.value)
    if (instr) {
      const { fromText, toText } = instr
      const res = smartReplaceAll(indexHtml.value || '', fromText, toText)
      if (res.count > 0) {
        indexHtml.value = res.html
        updatePreview()
        streamText.value += `\n[本地严格替换] ${fromText} -> ${toText}（共${res.count}处）\n`
        ElMessage.success(`已按指令替换 ${res.count} 处：${fromText} -> ${toText}`)
        return
      } else {
        ElMessage.warning('没有相关字符')
        return
      }
    } else {
      ElMessage.info('未识别到“将A改为B/替换为”指令格式，切换为 AI 模式执行')
    }
  }
  
  // 回退到 AI 模式
  editing.value = true

  const content = `严格按我的指令修改页面：\n- 仅在必要位置做最小改动，不得进行与指令无关的优化或重构；\n- 保持除涉及修改的部分外，页面结构与样式完全不变；\n- 若指令为文案替换（如“将汪星人改为喵星人”），请在页面内逐处替换对应文本；\n- 返回完整的 index.html（\`\`\`html 开头，\`\`\` 结尾）。\n\n【选中元素选择器】${selectedPath.value}\n【选中元素 outerHTML】\n${selectedOuterHtml.value}\n\n【我的修改指令】${editPrompt.value}\n\n【当前页面 index.html】\n${indexHtml.value}`

  const payload = {
    conversationId,
    content,
    model: 'deepseek-chat',
    temperature: 0.6,
    maxTokens: 3500
  }
  chatAPI.sendMessageStream(payload, (chunk) => {
    streamText.value += chunk
    parseHtmlStream(chunk)
  }, (err) => {
    editing.value = false
    ElMessage.error('修改失败：' + (err?.message || '未知错误'))
  }, () => {
    editing.value = false
    if (indexHtml.value) updatePreview()
  })
}

function cancelEdit() {
  editModeEnabled.value = false
  toggleEditMode(false)
}

// 解析“将A改为B/替换为/replace X with Y”指令
function parseReplaceInstruction(text) {
  if (!text) return null
  const cn1 = text.match(/将\s*(.+?)\s*改为\s*(.+?)(。|，|,|；|;|$)/)
  const cn2 = text.match(/把\s*(.+?)\s*(改成|替换成|替换为)\s*(.+?)(。|，|,|；|;|$)/)
  const en = text.match(/replace\s+(.+?)\s+with\s+(.+?)(\.|,|;|$)/i)
  if (cn1) return { fromText: cn1[1].trim(), toText: cn1[2].trim() }
  if (cn2) return { fromText: cn2[1].trim(), toText: cn2[3].trim() }
  if (en) return { fromText: en[1].trim(), toText: en[2].trim() }
  return null
}

// 大小写感知的全局替换：保持原匹配的大小写风格
function smartReplaceAll(html, fromText, toText) {
  if (!html || !fromText) return { html, count: 0 }
  const esc = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const re = new RegExp(esc(fromText), 'gi')
  let count = 0
  const replaced = html.replace(re, (m) => {
    count++
    // 全大写
    if (m === m.toUpperCase()) return toText.toUpperCase()
    // 全小写
    if (m === m.toLowerCase()) return toText.toLowerCase()
    // 首字母大写
    const cap = m.charAt(0).toUpperCase() + m.slice(1).toLowerCase()
    if (m === cap) return toText.charAt(0).toUpperCase() + toText.slice(1).toLowerCase()
    // 默认返回原样式
    return toText
  })
  return { html: replaced, count }
}

// 生成封面（注入 html2canvas 到 iframe 内部执行）
async function captureCover() {
  if (!previewIframe.value) return
  const win = previewIframe.value.contentWindow
  const doc = win.document
  await ensureHtml2Canvas(doc)
  try {
    const canvas = await win.html2canvas(doc.body, { scale: 1.5 })
    coverDataUrl.value = canvas.toDataURL('image/png')
    ElMessage.success('封面截取成功')
  } catch (e) {
    console.error(e)
    ElMessage.error('封面截取失败')
  }
}

function downloadCover() {
  if (!coverDataUrl.value) return
  const a = document.createElement('a')
  a.href = coverDataUrl.value
  a.download = 'cover.png'
  a.click()
}

async function ensureHtml2Canvas(doc) {
  return new Promise((resolve) => {
    if (doc.defaultView.html2canvas) return resolve()
    const script = doc.createElement('script')
    script.src = 'https://cdn.jsdelivr.net/npm/html2canvas@1.4.1/dist/html2canvas.min.js'
    script.onload = () => resolve()
    doc.body.appendChild(script)
  })
}

// 源码下载（ZIP）
async function downloadSource() {
  if (!indexHtml.value) return ElMessage.warning('暂无生成的源码')
  try {
    await ensureZipSaver()
    const zip = new window.JSZip()
    zip.file('index.html', indexHtml.value)
    const blob = await zip.generateAsync({ type: 'blob' })
    window.saveAs(blob, 'app.zip')
  } catch (e) {
    // 回退：直接下载 index.html（不压缩）
    console.warn('ZIP 依赖加载失败，回退为直接下载 index.html', e)
    const blob = new Blob([indexHtml.value], { type: 'text/html' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'index.html'
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.info('已回退为直接下载 index.html')
  }
}

// 在父页面通过 CDN 注入 jszip 与 file-saver，避免 Vite 解析依赖
function ensureZipSaver() {
  return new Promise((resolve, reject) => {
    // 已存在则直接返回
    if (window.JSZip && window.saveAs) return resolve()

    const inject = (src) => new Promise((res, rej) => {
      const s = document.createElement('script')
      s.src = src
      s.async = true
      s.onload = () => res()
      s.onerror = () => rej(new Error('脚本加载失败: ' + src))
      document.head.appendChild(s)
    })

    // 顺序加载，先 jszip 再 file-saver
    inject('https://cdn.jsdelivr.net/npm/jszip@3.10.1/dist/jszip.min.js')
      .then(() => inject('https://cdn.jsdelivr.net/npm/file-saver@2.0.5/dist/FileSaver.min.js'))
      .then(() => {
        if (window.JSZip && window.saveAs) resolve()
        else reject(new Error('未检测到 JSZip 或 saveAs'))
      })
      .catch(reject)
  })
}

function clearStream() {
  streamText.value = ''
}

onMounted(() => {
  // 初始不做事
})

// 时间格式化（简化版，与聊天页一致的表现）
function formatTime(time) {
  if (!time) return ''
  const date = new Date(time)
  const now = new Date()
  const diff = now - date
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
  return date.toLocaleDateString()
}
</script>

<style scoped>
.nocode-page {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.hero {
  padding: 32px 32px 16px 32px;
  background: linear-gradient(135deg, #6a85ff, #b96aff);
  color: #fff;
  position: relative;
}
.hero h1 { margin: 0 0 8px; }
.subtitle { opacity: 0.9; }

.top-actions { position: absolute; right: 24px; top: 24px; }

.prompt-bar { display: flex; gap: 12px; margin-top: 16px; }
.prompt-bar :deep(.el-input) { flex: 1; }

.strategy-tags { margin-top: 12px; display: flex; gap: 8px; }

.workspace {
  display: grid;
  grid-template-columns: 1.2fr 1fr;
  gap: 16px;
  padding: 16px;
}

.panel {
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 6px 18px rgba(0,0,0,0.06);
  overflow: hidden;
}
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #eee;
}
.actions { display: flex; align-items: center; gap: 8px; }

.stream { height: 280px; overflow: auto; padding: 12px; background: #0f172a; }
.stream-content { color: #e2e8f0; white-space: pre-wrap; word-break: break-word; }

.preview { height: 520px; background: #f6f7fb; display: flex; align-items: center; justify-content: center; }
.preview-iframe { width: 100%; height: 100%; border: 0; background: #fff; }
.preview-placeholder { color: #888; }

/* 生成中遮罩与旋转图片 */
.preview { position: relative; }
.gen-overlay { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; background: rgba(255,255,255,0.85); z-index: 10; }
.spin-img { width: 160px; height: 160px; border-radius: 50%; box-shadow: 0 8px 20px rgba(0,0,0,0.08); animation: spin 2s linear infinite; }
.default-spinner { width: 120px; height: 120px; border-radius: 50%; border: 10px solid #e5e7eb; border-top-color: #409EFF; animation: spin 1s linear infinite; }
.gen-text { margin-top: 12px; color: #666; font-size: 14px; }
@keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }

.edit-panel { margin-top: 16px; }
.edit-body { padding: 12px 16px; }
.selected-info { font-size: 12px; color: #666; margin-bottom: 8px; }
.selected-info .snippet { display: block; max-height: 120px; overflow: auto; }
.edit-actions { margin-top: 8px; display: flex; gap: 8px; }

.cover-panel { margin-top: 16px; }
.cover-preview { padding: 16px; display: flex; align-items: center; justify-content: center; }
.cover-preview img { max-width: 100%; border-radius: 8px; box-shadow: 0 6px 18px rgba(0,0,0,0.06); }

/* 历史记录弹窗布局 */
.history-layout { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.history-right .preview-iframe { width: 100%; height: 480px; border: 0; background: #fff; }
.history-right .preview-placeholder { height: 480px; display: flex; align-items: center; justify-content: center; color: #888; background: #f6f7fb; }
</style>
