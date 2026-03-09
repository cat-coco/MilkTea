(() => {
    'use strict';

    const API_BASE = 'http://localhost:9000';
    let sessionId = null;
    let isLoading = false;
    let panelOpen = false;
    let taskListCollapsed = false;
    let excelSheets = [];
    let activeSheetIndex = 0;

    // Task planning definitions
    const TASK_PLAN = [
        { id: 'step1', text: 'Step 1: Retrieve EFM report data and calculate volatility', status: 'pending', detail: '' },
        { id: 'step2', text: 'Step 2: Retrieve DCF010102 detail data', status: 'pending', detail: '' },
        { id: 'step3_1', text: 'Step 3.1: Retrieve equity info and mark 8 tiers', status: 'pending', detail: '' },
        { id: 'step3_2', text: 'Step 3.2: Associate detail data and mark tiers', status: 'pending', detail: '' },
        { id: 'step3_3', text: 'Step 3.3: Update SR6 data IC tier', status: 'pending', detail: '' },
        { id: 'step3_4', text: 'Step 3.4: Filter and create processed data', status: 'pending', detail: '' },
        { id: 'step4', text: 'Step 4: Verify detail data reasonability', status: 'pending', detail: '' },
        { id: 'step5', text: 'Step 5: Aggregate by simplified scenario and analyze volatility', status: 'pending', detail: '' }
    ];

    // Listen for toggle message from background
    chrome.runtime.onMessage.addListener((msg) => {
        if (msg.action === 'toggleFinanceCopilot') togglePanel();
    });

    function init() {
        createFloatingButton();
        createMainPanel();
        loadWelcome();
    }

    function createFloatingButton() {
        const btn = document.createElement('button');
        btn.id = 'finance-copilot-btn';
        btn.innerHTML = '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 3h18v18H3z"/><path d="M3 9h18"/><path d="M3 15h18"/><path d="M9 3v18"/><path d="M15 3v18"/></svg>';
        btn.title = 'Financial Report Analysis';
        btn.addEventListener('click', togglePanel);
        document.body.appendChild(btn);
    }

    function createMainPanel() {
        const container = document.createElement('div');
        container.id = 'finance-copilot-container';
        container.innerHTML = `
            <!-- Left: Main Content Area (Excel) -->
            <div id="finance-main-area">
                <div class="finance-main-header">
                    <div class="header-title">Online Data Worksheet</div>
                    <div style="font-size:12px;opacity:0.8;">Financial Report Volatility Analysis</div>
                </div>
                <div class="excel-tab-bar" id="excel-tab-bar"></div>
                <div class="excel-content" id="excel-content">
                    <div class="excel-placeholder">
                        <div class="icon"><svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="#90CAF9" stroke-width="1.5"><path d="M3 3h18v18H3z"/><path d="M3 9h18"/><path d="M3 15h18"/><path d="M9 3v18"/><path d="M15 3v18"/></svg></div>
                        <div class="text">Start a conversation to trigger workflow analysis, and data will be displayed here automatically</div>
                    </div>
                </div>
            </div>

            <!-- Right: Chat Panel -->
            <div id="finance-chat-panel">
                <div class="finance-chat-header">
                    <div class="header-left">
                        <div class="logo-icon"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 3h18v18H3z"/><path d="M3 9h18"/><path d="M9 3v18"/></svg></div>
                        <div>
                            <div class="header-title">Financial Report Analysis</div>
                            <div class="header-subtitle">AI Intelligent Assistant</div>
                        </div>
                    </div>
                    <button class="close-btn" id="finance-close">&times;</button>
                </div>

                <div class="task-planning-panel" id="task-planning-panel" style="display:none;">
                    <div class="task-planning-header" id="task-planning-header">
                        <div class="task-planning-title">
                            <span>Task Plan</span>
                            <span class="task-progress-badge" id="task-progress-badge">0/8</span>
                        </div>
                        <span class="task-planning-toggle" id="task-planning-toggle">&#9660;</span>
                    </div>
                    <div class="task-planning-list" id="task-planning-list"></div>
                </div>

                <div class="finance-quick-actions">
                    <button class="finance-quick-btn" data-msg="Please help me analyze the cash flow report for volatility reasonability">Cash Flow Analysis</button>
                    <button class="finance-quick-btn" data-msg="Please help me check the balance sheet volatility">Balance Sheet Check</button>
                </div>

                <div class="finance-messages" id="finance-messages"></div>

                <div class="finance-input-area">
                    <div class="finance-input-wrapper">
                        <textarea class="finance-input" id="finance-input"
                                  placeholder="Enter message..." rows="1"></textarea>
                        <button class="finance-send-btn" id="finance-send">&#10148;</button>
                    </div>
                    <div class="finance-toolbar">
                        <button id="finance-clear">Clear</button>
                        <button id="finance-new">+ New Session</button>
                    </div>
                </div>
            </div>
        `;
        document.body.appendChild(container);

        // Event listeners
        document.getElementById('finance-close').addEventListener('click', togglePanel);
        document.getElementById('finance-send').addEventListener('click', sendMessage);
        document.getElementById('finance-clear').addEventListener('click', clearChat);
        document.getElementById('finance-new').addEventListener('click', newSession);
        document.getElementById('task-planning-header').addEventListener('click', toggleTaskList);

        const input = document.getElementById('finance-input');
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
        container.querySelectorAll('.finance-quick-btn').forEach(btn => {
            btn.addEventListener('click', () => {
                document.getElementById('finance-input').value = btn.dataset.msg;
                sendMessage();
            });
        });
    }

    function togglePanel() {
        const container = document.getElementById('finance-copilot-container');
        const btn = document.getElementById('finance-copilot-btn');
        panelOpen = !panelOpen;
        if (panelOpen) {
            container.classList.add('open');
            btn.style.display = 'none';
            document.getElementById('finance-input').focus();
        } else {
            container.classList.remove('open');
            btn.style.display = 'flex';
        }
    }

    function toggleTaskList() {
        taskListCollapsed = !taskListCollapsed;
        const list = document.getElementById('task-planning-list');
        const toggle = document.getElementById('task-planning-toggle');
        if (taskListCollapsed) {
            list.classList.add('hidden');
            toggle.classList.add('collapsed');
        } else {
            list.classList.remove('hidden');
            toggle.classList.remove('collapsed');
        }
    }

    // ===================== Task Planning =====================

    function showTaskPlan() {
        const panel = document.getElementById('task-planning-panel');
        panel.style.display = 'block';
        renderTaskList();
    }

    function renderTaskList() {
        const list = document.getElementById('task-planning-list');
        const completedCount = TASK_PLAN.filter(t => t.status === 'completed').length;
        document.getElementById('task-progress-badge').textContent = `${completedCount}/${TASK_PLAN.length}`;

        list.innerHTML = TASK_PLAN.map(task => {
            let icon = '';
            let statusClass = '';
            let statusText = '';
            if (task.status === 'completed') {
                icon = '<span style="color:#4CAF50;">&#10003;</span>';
                statusClass = 'completed';
                statusText = 'Done';
            } else if (task.status === 'active') {
                icon = '<span class="task-spinner"></span>';
                statusClass = 'active';
                statusText = 'Executing...';
            } else {
                icon = '<span style="color:#BDBDBD;">&#9675;</span>';
                statusClass = '';
                statusText = 'Pending';
            }
            const detailHtml = task.detail ? `
                <div class="task-detail-content ${task.status === 'completed' ? 'visible' : ''}" id="detail-${task.id}">
                    ${escapeHtml(task.detail)}
                </div>
            ` : '';
            return `
                <div class="task-item ${statusClass} ${task.detail ? 'task-detail-toggle' : ''}"
                     onclick="document.getElementById('detail-${task.id}')&&document.getElementById('detail-${task.id}').classList.toggle('visible')">
                    <div class="task-icon">${icon}</div>
                    <div class="task-text">${escapeHtml(task.text)}</div>
                    <div class="task-status">${statusText}</div>
                </div>
                ${detailHtml}
            `;
        }).join('');
    }

    function updateTaskStatus(taskId, status, detail) {
        const task = TASK_PLAN.find(t => t.id === taskId);
        if (task) {
            task.status = status;
            if (detail) task.detail = detail;
            renderTaskList();
        }
    }

    // ===================== Excel Operations =====================

    function addExcelSheet(name, headers, rows) {
        excelSheets.push({ name, headers, rows });
        activeSheetIndex = excelSheets.length - 1;
        renderExcelTabs();
        renderExcelContent();
    }

    function updateExcelSheet(index, headers, rows, append) {
        if (index >= 0 && index < excelSheets.length) {
            if (append) {
                // Append columns
                const sheet = excelSheets[index];
                const newHeaders = headers.filter(h => !sheet.headers.includes(h));
                sheet.headers = sheet.headers.concat(newHeaders);
                sheet.rows = sheet.rows.map((row, i) => {
                    const newRow = { ...row };
                    newHeaders.forEach(h => {
                        newRow[h] = rows[i] ? rows[i][h] || '' : '';
                    });
                    return newRow;
                });
            } else {
                excelSheets[index].headers = headers;
                excelSheets[index].rows = rows;
            }
            if (activeSheetIndex === index) renderExcelContent();
        }
    }

    function renderExcelTabs() {
        const tabBar = document.getElementById('excel-tab-bar');
        tabBar.innerHTML = excelSheets.map((sheet, i) =>
            `<div class="excel-tab ${i === activeSheetIndex ? 'active' : ''}"
                  onclick="document.dispatchEvent(new CustomEvent('switchSheet',{detail:${i}}))">${escapeHtml(sheet.name)}</div>`
        ).join('');
        document.addEventListener('switchSheet', (e) => {
            activeSheetIndex = e.detail;
            renderExcelTabs();
            renderExcelContent();
        }, { once: true });
    }

    function renderExcelContent() {
        const content = document.getElementById('excel-content');
        if (excelSheets.length === 0) {
            content.innerHTML = `
                <div class="excel-placeholder">
                    <div class="icon"><svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="#90CAF9" stroke-width="1.5"><path d="M3 3h18v18H3z"/><path d="M3 9h18"/><path d="M3 15h18"/><path d="M9 3v18"/><path d="M15 3v18"/></svg></div>
                    <div class="text">Start a conversation to trigger workflow analysis, and data will be displayed here automatically</div>
                </div>`;
            return;
        }
        const sheet = excelSheets[activeSheetIndex];
        let html = '<div class="excel-table-wrapper"><table class="excel-table"><thead><tr>';
        sheet.headers.forEach(h => { html += `<th>${escapeHtml(h)}</th>`; });
        html += '</tr></thead><tbody>';
        sheet.rows.forEach(row => {
            html += '<tr>';
            sheet.headers.forEach(h => {
                const val = row[h] !== undefined ? String(row[h]) : '';
                let cls = '';
                if (h === 'Is Volatility > 20%' && val === 'Yes') cls = 'highlight-red';
                if (h === 'Is Volatility > 20%' && val === 'No') cls = 'highlight-green';
                if (h === 'Volatility Ratio' && val.includes('-')) cls = 'highlight-red';
                html += `<td class="${cls}">${escapeHtml(val)}</td>`;
            });
            html += '</tr>';
        });
        html += '</tbody></table></div>';
        content.innerHTML = html;

        // Re-bindtab events
        document.querySelectorAll('.excel-tab').forEach((tab, i) => {
            tab.onclick = () => {
                activeSheetIndex = i;
                renderExcelTabs();
                renderExcelContent();
            };
        });
    }

    // ===================== Chat Functions =====================

    function appendMessage(type, text) {
        const container = document.getElementById('finance-messages');
        const div = document.createElement('div');
        div.className = `finance-msg ${type}`;
        const avatar = type === 'bot' ? '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 3h18v18H3z"/><path d="M3 9h18"/><path d="M9 3v18"/></svg>' : '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="8" r="4"/><path d="M4 20c0-4 4-7 8-7s8 3 8 7"/></svg>';
        div.innerHTML = `
            <div class="msg-avatar">${avatar}</div>
            <div class="msg-bubble">${escapeHtml(text)}</div>
        `;
        container.appendChild(div);
        container.scrollTop = container.scrollHeight;
    }

    function showTyping() {
        const container = document.getElementById('finance-messages');
        const div = document.createElement('div');
        div.className = 'finance-msg bot finance-typing';
        div.id = 'finance-typing';
        div.innerHTML = `
            <div class="msg-avatar"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 3h18v18H3z"/><path d="M3 9h18"/><path d="M9 3v18"/></svg></div>
            <div class="msg-bubble">
                <div class="finance-typing-dot"></div>
                <div class="finance-typing-dot"></div>
                <div class="finance-typing-dot"></div>
            </div>
        `;
        container.appendChild(div);
        container.scrollTop = container.scrollHeight;
    }

    function hideTyping() {
        const el = document.getElementById('finance-typing');
        if (el) el.remove();
    }

    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    async function loadWelcome() {
        try {
            const res = await fetch(`${API_BASE}/api/finance/welcome`);
            const data = await res.json();
            appendMessage('bot', data.message);
        } catch {
            appendMessage('bot', 'Welcome! I am the Financial Report Volatility Analysis Intelligent Assistant. I can help you analyze the reasonability of report item volatility, including cash flow statements and balance sheets. Please tell me which report you would like to analyze.');
        }
    }

    async function sendMessage() {
        const input = document.getElementById('finance-input');
        const text = input.value.trim();
        if (!text || isLoading) return;

        appendMessage('user', text);
        input.value = '';
        input.style.height = 'auto';
        isLoading = true;
        document.getElementById('finance-send').disabled = true;
        showTyping();

        try {
            const res = await fetch(`${API_BASE}/api/finance/send`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ sessionId, message: text })
            });
            const data = await res.json();
            sessionId = data.sessionId;
            hideTyping();
            appendMessage('bot', data.reply);

            // Process workflow steps if present
            if (data.workflowActive) {
                showTaskPlan();
            }
            if (data.taskUpdates && data.taskUpdates.length > 0) {
                data.taskUpdates.forEach(update => {
                    updateTaskStatus(update.taskId, update.status, update.detail);
                });
            }
            if (data.excelOperations && data.excelOperations.length > 0) {
                data.excelOperations.forEach(op => {
                    if (op.action === 'addSheet') {
                        addExcelSheet(op.sheetName, op.headers, op.rows);
                    } else if (op.action === 'updateSheet') {
                        updateExcelSheet(op.sheetIndex, op.headers, op.rows, op.append);
                    }
                });
            }

            // Auto-execute workflow steps if triggered
            if (data.nextStep) {
                await executeWorkflowStep(data.nextStep);
            }
        } catch {
            hideTyping();
            appendMessage('bot', 'Sorry, failed to connect to server. Please ensure the backend service is running on localhost:9000');
        } finally {
            isLoading = false;
            document.getElementById('finance-send').disabled = false;
        }
    }

    async function executeWorkflowStep(stepId) {
        isLoading = true;
        showTyping();
        try {
            const res = await fetch(`${API_BASE}/api/finance/execute-step`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ sessionId, stepId })
            });
            const data = await res.json();
            hideTyping();

            if (data.reply) {
                appendMessage('bot', data.reply);
            }
            if (data.taskUpdates && data.taskUpdates.length > 0) {
                data.taskUpdates.forEach(update => {
                    updateTaskStatus(update.taskId, update.status, update.detail);
                });
            }
            if (data.excelOperations && data.excelOperations.length > 0) {
                data.excelOperations.forEach(op => {
                    if (op.action === 'addSheet') {
                        addExcelSheet(op.sheetName, op.headers, op.rows);
                    } else if (op.action === 'updateSheet') {
                        updateExcelSheet(op.sheetIndex, op.headers, op.rows, op.append);
                    }
                });
            }

            // Chain to next step
            if (data.nextStep) {
                await new Promise(r => setTimeout(r, 500));
                await executeWorkflowStep(data.nextStep);
            }
        } catch {
            hideTyping();
            appendMessage('bot', 'Error occurred during workflow step execution, please try again.');
        } finally {
            isLoading = false;
        }
    }

    async function clearChat() {
        if (sessionId) {
            try {
                await fetch(`${API_BASE}/api/finance/clear`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ sessionId })
                });
            } catch { /* ignore */ }
        }
        document.getElementById('finance-messages').innerHTML = '';
        // Reset task plan
        TASK_PLAN.forEach(t => { t.status = 'pending'; t.detail = ''; });
        document.getElementById('task-planning-panel').style.display = 'none';
        // Reset excel
        excelSheets = [];
        activeSheetIndex = 0;
        renderExcelContent();
        document.getElementById('excel-tab-bar').innerHTML = '';
        loadWelcome();
    }

    function newSession() {
        sessionId = null;
        clearChat();
    }

    // Initialize when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
