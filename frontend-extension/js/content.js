(() => {
    'use strict';

    const API_BASE = 'http://localhost:9000';
    let sessionId = null;
    let isLoading = false;
    let panelOpen = false;

    // Listen for toggle message from background
    chrome.runtime.onMessage.addListener((msg) => {
        if (msg.action === 'toggleCopilot') togglePanel();
    });

    // Create DOM elements
    function init() {
        createFloatingButton();
        createPanel();
        loadWelcome();
    }

    function createFloatingButton() {
        const btn = document.createElement('button');
        btn.id = 'milktea-copilot-btn';
        btn.textContent = '🧋';
        btn.title = '茶悦时光客服';
        btn.addEventListener('click', togglePanel);
        document.body.appendChild(btn);
    }

    function createPanel() {
        const panel = document.createElement('div');
        panel.id = 'milktea-copilot-panel';
        panel.innerHTML = `
            <div class="copilot-header">
                <div class="header-left">
                    <div class="logo-icon">🧋</div>
                    <div>
                        <div class="header-title">茶悦时光</div>
                        <div class="header-subtitle">AI智能客服小茶</div>
                    </div>
                </div>
                <button class="close-btn" id="copilot-close">&times;</button>
            </div>

            <div class="copilot-quick-actions">
                <button class="copilot-quick-btn" data-msg="我想点一杯奶茶">☕ 点单</button>
                <button class="copilot-quick-btn" data-msg="我想查询我的订单">🔍 查单</button>
                <button class="copilot-quick-btn" data-msg="我想取消订单">↩ 退单</button>
                <button class="copilot-quick-btn" data-msg="有什么推荐的吗？">⭐ 推荐</button>
                <button class="copilot-quick-btn" data-msg="看看菜单">📋 菜单</button>
            </div>

            <div class="copilot-messages" id="copilot-messages"></div>

            <div class="copilot-input-area">
                <div class="copilot-input-wrapper">
                    <textarea class="copilot-input" id="copilot-input"
                              placeholder="请输入消息..." rows="1"></textarea>
                    <button class="copilot-send-btn" id="copilot-send">➤</button>
                </div>
                <div class="copilot-toolbar">
                    <button id="copilot-clear">🗑 清空</button>
                    <button id="copilot-new">＋ 新会话</button>
                </div>
            </div>
        `;
        document.body.appendChild(panel);

        // Event listeners
        document.getElementById('copilot-close').addEventListener('click', togglePanel);
        document.getElementById('copilot-send').addEventListener('click', sendMessage);
        document.getElementById('copilot-clear').addEventListener('click', clearChat);
        document.getElementById('copilot-new').addEventListener('click', newSession);

        const input = document.getElementById('copilot-input');
        input.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                sendMessage();
            }
        });
        input.addEventListener('input', function () {
            this.style.height = 'auto';
            this.style.height = Math.min(this.scrollHeight, 80) + 'px';
        });

        // Quick action buttons
        panel.querySelectorAll('.copilot-quick-btn').forEach(btn => {
            btn.addEventListener('click', () => {
                document.getElementById('copilot-input').value = btn.dataset.msg;
                sendMessage();
            });
        });
    }

    function togglePanel() {
        const panel = document.getElementById('milktea-copilot-panel');
        const btn = document.getElementById('milktea-copilot-btn');
        panelOpen = !panelOpen;
        if (panelOpen) {
            panel.classList.add('open');
            btn.style.display = 'none';
            document.getElementById('copilot-input').focus();
        } else {
            panel.classList.remove('open');
            btn.style.display = 'flex';
        }
    }

    function appendMessage(type, text) {
        const container = document.getElementById('copilot-messages');
        const div = document.createElement('div');
        div.className = `copilot-msg ${type}`;
        const avatar = type === 'bot' ? '🧋' : '👤';
        div.innerHTML = `
            <div class="msg-avatar">${avatar}</div>
            <div class="msg-bubble">${escapeHtml(text)}</div>
        `;
        container.appendChild(div);
        container.scrollTop = container.scrollHeight;
    }

    function createStreamingMessage() {
        const container = document.getElementById('copilot-messages');
        const div = document.createElement('div');
        div.className = 'copilot-msg bot';
        div.innerHTML = `
            <div class="msg-avatar">🧋</div>
            <div class="msg-bubble"></div>
        `;
        container.appendChild(div);
        const bubble = div.querySelector('.msg-bubble');
        return {
            append(text) {
                bubble.textContent += text;
                container.scrollTop = container.scrollHeight;
            },
            getText() {
                return bubble.textContent;
            }
        };
    }

    function showTyping() {
        const container = document.getElementById('copilot-messages');
        const div = document.createElement('div');
        div.className = 'copilot-msg bot copilot-typing';
        div.id = 'copilot-typing';
        div.innerHTML = `
            <div class="msg-avatar">🧋</div>
            <div class="msg-bubble">
                <div class="copilot-typing-dot"></div>
                <div class="copilot-typing-dot"></div>
                <div class="copilot-typing-dot"></div>
            </div>
        `;
        container.appendChild(div);
        container.scrollTop = container.scrollHeight;
    }

    function hideTyping() {
        const el = document.getElementById('copilot-typing');
        if (el) el.remove();
    }

    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    async function loadWelcome() {
        try {
            const res = await fetch(`${API_BASE}/api/chat/welcome`);
            const data = await res.json();
            appendMessage('bot', data.message);
        } catch {
            appendMessage('bot', '欢迎光临茶悦时光！我是智能客服小茶~请问有什么可以帮您的呢？');
        }
    }

    async function sendMessage() {
        const input = document.getElementById('copilot-input');
        const text = input.value.trim();
        if (!text || isLoading) return;

        appendMessage('user', text);
        input.value = '';
        input.style.height = 'auto';
        isLoading = true;
        document.getElementById('copilot-send').disabled = true;
        showTyping();

        try {
            const res = await fetch(`${API_BASE}/api/chat/stream`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ sessionId, message: text })
            });

            hideTyping();
            const streamMsg = createStreamingMessage();
            const reader = res.body.getReader();
            const decoder = new TextDecoder();
            let buffer = '';

            while (true) {
                const { done, value } = await reader.read();
                if (done) break;

                buffer += decoder.decode(value, { stream: true });
                const lines = buffer.split('\n');
                buffer = lines.pop();

                let eventName = '';
                for (const line of lines) {
                    if (line.startsWith('event:')) {
                        eventName = line.substring(6).trim();
                    } else if (line.startsWith('data:')) {
                        const data = line.substring(5);
                        if (eventName === 'meta') {
                            try {
                                const meta = JSON.parse(data);
                                sessionId = meta.sessionId;
                            } catch (e) { /* ignore */ }
                        } else if (eventName === 'token') {
                            streamMsg.append(data);
                        } else if (eventName === 'error') {
                            if (!streamMsg.getText()) {
                                streamMsg.append(data);
                            }
                        }
                        eventName = '';
                    }
                }
            }

            if (!streamMsg.getText()) {
                streamMsg.append('我没有理解您的意思，请再说一次~');
            }
        } catch {
            hideTyping();
            appendMessage('bot', '抱歉，连接服务器失败，请确保后端服务运行在 localhost:9000');
        } finally {
            isLoading = false;
            document.getElementById('copilot-send').disabled = false;
        }
    }

    async function clearChat() {
        if (sessionId) {
            try {
                await fetch(`${API_BASE}/api/chat/clear`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ sessionId })
                });
            } catch { /* ignore */ }
        }
        document.getElementById('copilot-messages').innerHTML = '';
        loadWelcome();
    }

    function newSession() {
        sessionId = null;
        document.getElementById('copilot-messages').innerHTML = '';
        loadWelcome();
    }

    // Initialize when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
