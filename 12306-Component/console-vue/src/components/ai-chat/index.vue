<template>
  <div class="ai-chat-container" v-if="!isLogin">
    <!-- 悬浮按钮 -->
    <div class="ai-chat-trigger" :class="{ active: visible }" @click="toggleChat">
      <span v-if="!visible" class="trigger-icon">AI</span>
      <span v-else class="trigger-icon">&times;</span>
    </div>

    <!-- 聊天窗口 -->
    <transition name="chat-fade">
      <div v-if="visible" class="ai-chat-window">
        <!-- 标题栏 -->
        <div class="chat-header">
          <span class="chat-title">AI 智能助手</span>
          <span class="chat-subtitle">12306 出行顾问</span>
        </div>

        <!-- 消息列表 -->
        <div class="chat-messages" ref="messagesRef">
          <div class="welcome-msg">
            <div class="ai-bubble">
              <div class="bubble-content">你好！我是 12306 AI 出行助手，可以帮你查票、购票。请问有什么可以帮您的？</div>
            </div>
          </div>

          <div v-for="(msg, index) in messages" :key="index" class="message-item" :class="{ 'user-msg': msg.role === 'user', 'ai-msg': msg.role === 'ai' }">
            <!-- 用户消息 -->
            <div v-if="msg.role === 'user'" class="user-bubble">
              <div class="bubble-content">{{ msg.content }}</div>
            </div>

            <!-- AI 消息 -->
            <div v-else class="ai-bubble">
              <!-- 电子客票卡片 -->
              <div v-if="msg.isTicket" class="ticket-card">
                <div class="ticket-header">电子客票</div>
                <div class="ticket-body">
                  <div class="ticket-line" v-for="(line, i) in msg.ticketLines" :key="i">
                    <span class="ticket-label">{{ line.label }}</span>
                    <span class="ticket-value" :class="{ 'ticket-highlight': line.highlight }">{{ line.value }}</span>
                  </div>
                </div>
                <div class="ticket-actions" v-if="msg.orderSn">
                  <span class="auto-pay-text">已成功为您锁定席位！正在前往订单支付中心...</span>
                  <a-spin size="small" />
                </div>
                <div class="ticket-tip">请携带有效身份证件按时乘车</div>
              </div>
              <!-- 购票跳转卡片 -->
              <div v-else-if="msg.bookInfo" class="book-card">
                <div class="book-header">确认购票</div>
                <div class="book-body">
                  <div class="book-line">
                    <span class="book-label">车次</span>
                    <span class="book-value highlight">{{ msg.bookInfo.trainNumber }}</span>
                  </div>
                  <div class="book-line">
                    <span class="book-label">行程</span>
                    <span class="book-value">{{ msg.bookInfo.departure }} → {{ msg.bookInfo.arrival }}</span>
                  </div>
                  <div class="book-line">
                    <span class="book-label">日期</span>
                    <span class="book-value">{{ msg.bookInfo.departureDate }}</span>
                  </div>
                  <div class="book-line">
                    <span class="book-label">席别</span>
                    <span class="book-value highlight">{{ msg.bookInfo.seatType }}</span>
                  </div>
                </div>
                <div class="book-actions">
                  <a-button type="primary" class="book-btn" @click="goBuyTicket(msg.bookInfo)">
                    立即购票
                  </a-button>
                </div>
              </div>
              <!-- 普通文本（打字机效果） -->
              <div v-else class="bubble-content">
                <span>{{ msg.displayContent }}</span>
                <span v-if="msg.typing" class="typing-cursor">|</span>
              </div>
            </div>
          </div>

          <!-- Loading 状态 -->
          <div v-if="loading" class="message-item ai-msg">
            <div class="ai-bubble">
              <div class="bubble-content loading-bubble">
                <span class="dot"></span>
                <span class="dot"></span>
                <span class="dot"></span>
                <span class="loading-text">AI 正在思考中</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 输入区 -->
        <div class="chat-input-area">
          <a-input
              ref="inputRef"
              v-model:value="inputText"
              placeholder="输入你的问题..."
              :disabled="loading"
              @keyup.enter="sendMessage"
              class="chat-input"
          />
          <a-button
              type="primary"
              :disabled="!inputText.trim() || loading"
              @click="sendMessage"
              class="send-btn"
          >
            发送
          </a-button>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import {ref, reactive, nextTick, watch, computed} from 'vue'
import {Input, Button, Spin} from 'ant-design-vue'
import {fetchAgentChat} from '@/service'
import Cookies from 'js-cookie'
import {useRoute, useRouter} from 'vue-router'

const AInput = Input
const AButton = Button
const route = useRoute()
const router = useRouter()

const isLogin = computed(() => route.path === '/login')
const visible = ref(false)
const loading = ref(false)
const inputText = ref('')
const messages = reactive([])
const messagesRef = ref(null)
const inputRef = ref(null)

const chatId = ref(Cookies.get('userId') || 'default_user')
const username = ref(Cookies.get('username') || '')

const toggleChat = () => {
  visible.value = !visible.value
}

const goBuyTicket = (info) => {
  const url = `buyTicket?trainNumber=${info.trainNumber}` +
      `&&trainId=${info.trainId}` +
      `&&fromStation=${info.departure}` +
      `&&toStation=${info.arrival}` +
      `&&departureDate=${info.departureDate}` +
      `&&arrival_date=` +
      `&&car_type=` +
      `&&departure_stations=` +
      `&&arrival_stations=` +
      `&&departure=` +
      `&&arrival=` +
      `&&seat=`
  window.open(url, '_blank')
  visible.value = false
}

const scrollToBottom = () => {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

// 解析电子客票信息
const parseTicketInfo = (text) => {
  const lines = []

  // 1. 匹配车次 (兼容 "车次:D717" 或 "D717次")
  const trainMatch = text.match(/车次[：:]\s*([A-Z0-9]+)/) || text.match(/([A-Z0-9]+)\s*次/i)
  if (trainMatch) lines.push({label: '车次', value: trainMatch[1], highlight: true})

  // 2. 匹配席别 (直接抓取常识座位关键字)
  const seatMatch = text.match(/(商务座|特等座|一等座|二等座|软卧|硬卧|软座|硬座|无座)/)
  if (seatMatch) lines.push({label: '席位', value: seatMatch[1]})

  // 3. 匹配订单号 (兼容 "订单号:E123" 或 "订单号 E123")
  const orderMatch = text.match(/订单号[^a-zA-Z0-9]*([A-Z0-9]+)/)
  if (orderMatch) lines.push({label: '订单号', value: orderMatch[1], highlight: true})

  // 4. 匹配行程 (兼容 "北京南 到 杭州东" 或 "北京南->杭州东" 或 "北京南至杭州东")
  const routeMatch = text.match(/([\u4e00-\u9fa5]+站?)\s*(?:到|至|->|->|→|-)\s*([\u4e00-\u9fa5]+站?)/)
  if (routeMatch) lines.push({label: '行程', value: routeMatch[1] + ' → ' + routeMatch[2]})

  // 5. 匹配乘客 (兼容 "乘客:张三" 或实名核验提示)
  const passengerMatch = text.match(/(?:乘客|乘车人)[：:]\s*([\u4e00-\u9fa5]+)/) || text.match(/实名核验[（(]([\u4e00-\u9fa5]+)/)
  if (passengerMatch) lines.push({label: '乘客', value: passengerMatch[1]})

  // 6. 匹配日期 (YYYY-MM-DD格式)
  const dateMatch = text.match(/\d{4}-\d{2}-\d{2}/)
  if (dateMatch) lines.push({label: '日期', value: dateMatch[0]})

  return lines.length > 0 ? lines : null
}

// 打字机效果
const typewriterEffect = (msgIndex, text) => {
  const msg = messages[msgIndex]
  let charIndex = 0
  msg.displayContent = ''
  msg.typing = true

  const timer = setInterval(() => {
    if (charIndex < text.length) {
      msg.displayContent += text[charIndex]
      charIndex++
      scrollToBottom()
    } else {
      clearInterval(timer)
      msg.typing = false
    }
  }, 40)
}

const sendMessage = async () => {
  const text = inputText.value.trim()
  if (!text || loading.value) return

  // 添加用户消息
  messages.push({role: 'user', content: text})

  // 强制清空输入框（兼容 Edge）
  if (inputRef.value?.$el) {
    const nativeInput = inputRef.value.$el.querySelector('input')
    if (nativeInput) nativeInput.value = ''
  }
  inputText.value = ''

  loading.value = true
  scrollToBottom()

  try {
    const res = await fetchAgentChat(text, chatId.value, username.value)
    loading.value = false
    console.log('AI 接口返回:', res)

    if ((res.code == 0 || res.code === '0') && res.data) {
      const aiText = res.data
      // 检测是否包含订单号（电子客票）
      if (aiText.includes('订单号')) {
        const ticketLines = parseTicketInfo(aiText)
        if (ticketLines) {
          const orderSnMatch = aiText.match(/订单号[：:]\s*(\S+)/)
          const orderSn = orderSnMatch ? orderSnMatch[1] : null
          messages.push({
            role: 'ai',
            content: aiText,
            displayContent: aiText,
            isTicket: true,
            ticketLines,
            orderSn,
            typing: false
          })
          scrollToBottom()
          // 1.5 秒后自动跳转到支付页面
          if (orderSn) {
            setTimeout(() => {
              visible.value = false
              router.push({path: '/order', query: {sn: orderSn}})
            }, 1500)
          }
          return
        }
      }
      // 检测购票跳转标记
      const bookMatch = aiText.match(/\[TICKET_BOOK:([^|]+)\|([^|]+)\|([^|]+)\|([^|]+)\|([^|]+)\|([^\]]+)\]/)
      if (bookMatch) {
        const cleanText = aiText.replace(/\[TICKET_BOOK:[^\]]+\]/, '').trim()
        const bookInfo = {
          trainId: bookMatch[1],
          trainNumber: bookMatch[2],
          departure: bookMatch[3],
          arrival: bookMatch[4],
          departureDate: bookMatch[5],
          seatType: bookMatch[6]
        }
        // 先显示文字部分（打字机效果）
        const msgIndex = messages.length
        messages.push({role: 'ai', content: cleanText, displayContent: '', typing: false, bookInfo: null})
        typewriterEffect(msgIndex, cleanText)
        // 打字机结束后显示购票卡片
        setTimeout(() => {
          messages[messages.length - 1].bookInfo = bookInfo
          scrollToBottom()
        }, cleanText.length * 40 + 200)
        return
      }
      // 普通文本，打字机效果
      const msgIndex = messages.length
      messages.push({role: 'ai', content: aiText, displayContent: '', typing: false})
      typewriterEffect(msgIndex, aiText)
    } else {
      const errMsg = res.message || '服务暂时不可用'
      const msgIndex = messages.length
      messages.push({role: 'ai', content: '抱歉: ' + errMsg, displayContent: '', typing: false})
      typewriterEffect(msgIndex, '抱歉: ' + errMsg)
    }
  } catch (e) {
    loading.value = false
    console.error('AI 请求异常:', e)
    const detail = e.response?.data?.message || e.message || '未知错误'
    const msgIndex = messages.length
    messages.push({role: 'ai', content: '请求失败: ' + detail, displayContent: '', typing: false})
    typewriterEffect(msgIndex, '请求失败: ' + detail)
  }
}
</script>

<style lang="less" scoped>
.ai-chat-container {
  position: fixed;
  bottom: 24px;
  right: 24px;
  z-index: 1000;
}

// 悬浮按钮
.ai-chat-trigger {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: #1890ff;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(24, 144, 255, 0.4);
  transition: all 0.3s;
  position: absolute;
  bottom: 0;
  right: 0;

  &:hover {
    background: #40a9ff;
    transform: scale(1.05);
    box-shadow: 0 6px 16px rgba(24, 144, 255, 0.5);
  }

  &.active {
    background: #666;

    &:hover {
      background: #888;
    }
  }

  .trigger-icon {
    font-size: 18px;
    font-weight: bold;
    user-select: none;
  }
}

// 聊天窗口
.ai-chat-window {
  position: absolute;
  bottom: 70px;
  right: 0;
  width: 400px;
  height: 520px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.15);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

// 标题栏
.chat-header {
  background: linear-gradient(135deg, #1890ff, #1e71bd);
  color: #fff;
  padding: 14px 16px;
  flex-shrink: 0;

  .chat-title {
    font-size: 16px;
    font-weight: 600;
    display: block;
  }

  .chat-subtitle {
    font-size: 12px;
    opacity: 0.8;
    margin-top: 2px;
    display: block;
  }
}

// 消息列表
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background: #f7f8fa;

  &::-webkit-scrollbar {
    width: 4px;
  }

  &::-webkit-scrollbar-thumb {
    background: #d9d9d9;
    border-radius: 2px;
  }
}

.message-item {
  margin-bottom: 12px;
  display: flex;

  &.user-msg {
    justify-content: flex-end;
  }

  &.ai-msg {
    justify-content: flex-start;
  }
}

// 用户气泡
.user-bubble {
  max-width: 80%;

  .bubble-content {
    background: #1890ff;
    color: #fff;
    padding: 10px 14px;
    border-radius: 12px 12px 2px 12px;
    font-size: 14px;
    line-height: 1.6;
    word-break: break-all;
  }
}

// AI 气泡
.ai-bubble {
  max-width: 85%;

  .bubble-content {
    background: #fff;
    color: #333;
    padding: 10px 14px;
    border-radius: 12px 12px 12px 2px;
    font-size: 14px;
    line-height: 1.6;
    word-break: break-all;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  }
}

.welcome-msg {
  margin-bottom: 12px;
}

// 打字机光标
.typing-cursor {
  animation: blink 0.8s infinite;
  color: #1890ff;
  font-weight: bold;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

// Loading 动画
.loading-bubble {
  display: flex;
  align-items: center;
  gap: 4px;

  .dot {
    width: 6px;
    height: 6px;
    background: #1890ff;
    border-radius: 50%;
    animation: dotPulse 1.2s ease-in-out infinite;

    &:nth-child(2) { animation-delay: 0.2s; }
    &:nth-child(3) { animation-delay: 0.4s; }
  }

  .loading-text {
    margin-left: 6px;
    color: #999;
    font-size: 13px;
  }
}

@keyframes dotPulse {
  0%, 80%, 100% { transform: scale(0.6); opacity: 0.4; }
  40% { transform: scale(1); opacity: 1; }
}

// 电子客票卡片
.ticket-card {
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  border: 1px solid #e8e8e8;

  .ticket-header {
    background: linear-gradient(135deg, #1e71bd, #1890ff);
    color: #fff;
    text-align: center;
    padding: 10px;
    font-size: 15px;
    font-weight: 600;
  }

  .ticket-body {
    padding: 14px 16px;
  }

  .ticket-line {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 6px 0;
    border-bottom: 1px dashed #f0f0f0;

    &:last-child {
      border-bottom: none;
    }

    .ticket-label {
      color: #999;
      font-size: 13px;
      flex-shrink: 0;
    }

    .ticket-value {
      color: #333;
      font-size: 13px;
      text-align: right;
      margin-left: 12px;
    }

    .ticket-highlight {
      color: #ff8001;
      font-weight: 600;
      font-size: 14px;
    }
  }

  .ticket-actions {
    padding: 10px 16px;
    text-align: center;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;

    .auto-pay-text {
      color: #52c41a;
      font-size: 13px;
      font-weight: 500;
    }
  }

  .ticket-tip {
    background: #fffbe5;
    color: #8c6d00;
    font-size: 12px;
    padding: 8px 16px;
    text-align: center;
    border-top: 1px solid #fbd800;
  }
}

// 购票跳转卡片
.book-card {
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  border: 1px solid #e8e8e8;
  max-width: 300px;

  .book-header {
    background: linear-gradient(135deg, #1890ff, #1e71bd);
    color: #fff;
    text-align: center;
    padding: 10px;
    font-size: 15px;
    font-weight: 600;
  }

  .book-body {
    padding: 14px 16px;
  }

  .book-line {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 5px 0;

    .book-label {
      color: #999;
      font-size: 13px;
    }

    .book-value {
      color: #333;
      font-size: 13px;
      margin-left: 12px;

      &.highlight {
        color: #ff8001;
        font-weight: 600;
        font-size: 14px;
      }
    }
  }

  .book-actions {
    padding: 10px 16px;
    text-align: center;

    .book-btn {
      background: #ff8001;
      border-color: #ff8001;
      border-radius: 20px;
      min-width: 140px;
      font-weight: 600;

      &:hover {
        background: #ff9a2e;
        border-color: #ff9a2e;
      }
    }
  }
}

// 输入区
.chat-input-area {
  display: flex;
  padding: 12px;
  gap: 8px;
  border-top: 1px solid #f0f0f0;
  background: #fff;
  flex-shrink: 0;

  .chat-input {
    flex: 1;
    border-radius: 20px;
  }

  .send-btn {
    border-radius: 20px;
    background: #1890ff;
    border-color: #1890ff;
    min-width: 64px;

    &:disabled {
      background: #d9d9d9;
      border-color: #d9d9d9;
    }
  }
}

// 过渡动画
.chat-fade-enter-active {
  animation: chatSlideUp 0.3s ease;
}

.chat-fade-leave-active {
  animation: chatSlideUp 0.2s ease reverse;
}

@keyframes chatSlideUp {
  from {
    opacity: 0;
    transform: translateY(20px) scale(0.95);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}
</style>
