/* ================================================================
   workbench.js — 项目工作台组件
   以项目为中心：选择关联日志源 → 拉取日志 → 勾选工具 → 异常分析 → 报告追问
   ================================================================ */

const WorkbenchTab = {

  _container: null,
  _projectId: null,
  _logSources: [],
  _conversationId: null,

  init(container) {
    this._container = container;
    this.render();
    this._bindEvents();

    EventBus.on('project-selected', ({ projectId }) => {
      this._projectId = projectId;
      const sel = document.getElementById('wbProjectSelect');
      if (sel) sel.value = projectId || '';
      this._onProjectChange();
    });

    EventBus.on('projects-changed', () => {
      if (AppState.currentTab === 'workbench') this._populateProjectSelect();
    });

    EventBus.on('tab-switched', ({ tabName }) => {
      if (tabName === 'workbench') this._populateProjectSelect();
    });
  },

  render() {
    this._container.innerHTML = Utils.html`
      <div class="row">
        <div class="col">
          <div class="card">
            <div class="card-header"><h3 class="card-title">🧪 项目工作台</h3></div>
            <div class="form-group">
              <label class="form-label">选择项目 *</label>
              <select class="form-select" id="wbProjectSelect">
                <option value="">-- 请选择项目 --</option>
              </select>
            </div>
          </div>

          <div class="card" id="wbLogSourcesCard" style="display:none">
            <div class="card-header"><h3 class="card-title">📋 关联日志源</h3></div>
            <div id="wbLogSources"><div class="empty-state" style="padding:20px"><p>加载中...</p></div></div>
            <div class="form-group" style="margin-top:12px">
              <label class="form-label">关键词过滤（可选）</label>
              <input class="form-input" id="wbLogKeyword" placeholder="如：ERROR / NullPointerException">
            </div>
            <button class="btn btn-outline btn-sm" id="wbFetchLogsBtn">获取日志</button>
            <span id="wbFetchResult" style="margin-left:8px;font-size:13px"></span>
          </div>

          <div class="card" id="wbToolsCard" style="display:none">
            <div class="card-header"><h3 class="card-title">🔧 可用工具</h3></div>
            <div class="tool-checkbox-list" id="wbTools"></div>
            <div style="font-size:12px;color:var(--color-text-secondary);margin-top:6px">
              勾选的工具将保存到项目配置并用于本次分析
            </div>
          </div>

          <div class="card" id="wbInputCard" style="display:none">
            <div class="card-header"><h3 class="card-title">📝 分析输入</h3></div>
            <div class="form-group">
              <label class="form-label">异常堆栈（可选 — 日志中已有堆栈时可留空）</label>
              <textarea class="form-textarea" id="wbStackTrace" rows="6"
                placeholder="粘贴 Java 异常堆栈...（可选）"></textarea>
            </div>
            <div class="form-group">
              <label class="form-label">日志内容（获取日志后自动填充，可编辑补充）</label>
              <textarea class="form-textarea" id="wbLogContent" rows="10"
                style="font-size:12px"
                placeholder="点击上方「获取日志」自动填充，或直接粘贴日志文本..."></textarea>
            </div>
            <button class="btn btn-primary" id="wbSubmitBtn">开始分析</button>
            <span id="wbLoading" style="display:none;margin-left:12px">
              <span class="spinner"></span>分析中...
            </span>
          </div>
        </div>

        <div class="col">
          <div class="card" id="wbReportCard" style="display:none">
            <div class="card-header">
              <h3 class="card-title">诊断报告</h3>
              <span style="font-size:12px;color:var(--color-text-secondary)" id="wbTraceId"></span>
            </div>
            <div id="wbReport"></div>
          </div>

          <div class="card">
            <div class="card-header"><h3 class="card-title">追问对话</h3></div>
            <div class="chat-box" id="wbChatBox">
              <div class="empty-state"><div class="empty-state-icon">💬</div>提交分析后可在此追问</div>
            </div>
            <div class="chat-input-row" id="wbChatInputRow" style="display:none">
              <input class="form-input" id="wbChatInput" placeholder="输入追问内容...">
              <button class="btn btn-primary btn-sm" id="wbChatSend">发送</button>
            </div>
          </div>
        </div>
      </div>`;
  },

  _bindEvents() {
    document.getElementById('wbProjectSelect').onchange = (e) => {
      this._projectId = e.target.value || null;
      EventBus.emit('project-selected', { projectId: this._projectId });
      this._onProjectChange();
    };
    document.getElementById('wbFetchLogsBtn').onclick = () => this._fetchLogs();
    document.getElementById('wbSubmitBtn').onclick = () => this._submitAnalysis();
    document.getElementById('wbChatSend').onclick = () => this._sendChat();
    document.getElementById('wbChatInput').onkeydown = (e) => {
      if (e.key === 'Enter') this._sendChat();
    };
  },

  _populateProjectSelect() {
    const sel = document.getElementById('wbProjectSelect');
    if (!sel) return;
    sel.innerHTML = '<option value="">-- 请选择项目 --</option>';
    AppState.projects.forEach(p => {
      const opt = document.createElement('option');
      opt.value = p.id;
      opt.textContent = p.name;
      if (p.id === this._projectId) opt.selected = true;
      sel.appendChild(opt);
    });
  },

  /** 项目切换时加载日志源与工具 */
  async _onProjectChange() {
    const showCards = !!this._projectId;
    document.getElementById('wbLogSourcesCard').style.display = showCards ? 'block' : 'none';
    document.getElementById('wbToolsCard').style.display = showCards ? 'block' : 'none';
    document.getElementById('wbInputCard').style.display = showCards ? 'block' : 'none';

    if (!showCards) {
      this._logSources = [];
      return;
    }

    this._renderTools();
    await this._loadLogSources();
  },

  /** 加载项目的日志源列表 */
  async _loadLogSources() {
    try {
      const res = await Api.getLogSources(this._projectId);
      if (res.success) {
        this._logSources = res.logSources || [];
        this._renderLogSources();
      } else {
        Utils.notify('加载日志源失败: ' + (res.error || ''), 'error');
      }
    } catch (e) {
      Utils.notify('加载日志源失败: ' + e.message, 'error');
    }
  },

  /** 渲染日志源复选框列表（仅 enabled 的可勾选） */
  _renderLogSources() {
    const el = document.getElementById('wbLogSources');
    if (!el) return;

    const available = this._logSources.filter(ls => ls.enabled);
    if (available.length === 0) {
      el.innerHTML = '<div class="empty-state" style="padding:20px"><p>该项目暂无启用的日志源</p></div>';
      return;
    }

    el.innerHTML = available.map(ls => Utils.html`
      <label class="logsource-item" style="cursor:pointer">
        <input type="checkbox" value="${ls.id}" style="margin-right:8px">
        <span class="logsource-type-badge">${Utils.logSourceTypeLabel(ls.type)}</span>
        <strong>${ls.name}</strong>
      </label>`).join('');
  },

  /** 渲染工具复选框（预勾选项目 enabledTools） */
  _renderTools() {
    const el = document.getElementById('wbTools');
    if (!el) return;

    const project = AppState.projects.find(p => p.id === this._projectId);
    const enabledTools = project ? Utils.safeJsonArray(project.enabledTools) : [];

    el.innerHTML = AppState.tools.map(t => Utils.html`
      <label class="tool-checkbox-item">
        <input type="checkbox" value="${t.name}" ${enabledTools.includes(t.name) ? 'checked' : ''}>
        <span title="${t.description}">${t.name}</span>
      </label>`).join('');
  },

  /** 获取勾选的工具列表 */
  _getCheckedTools() {
    const container = document.getElementById('wbTools');
    return container ? [...container.querySelectorAll('input:checked')].map(cb => cb.value) : [];
  },

  /** 从勾选的日志源拉取日志，拼接后填入日志内容框 */
  async _fetchLogs() {
    const checked = [...document.querySelectorAll('#wbLogSources input:checked')].map(cb => cb.value);
    if (checked.length === 0) {
      Utils.notify('请至少勾选一个日志源', 'error');
      return;
    }

    const keyword = document.getElementById('wbLogKeyword').value.trim() || null;
    const resultEl = document.getElementById('wbFetchResult');
    resultEl.textContent = '拉取中...';
    resultEl.style.color = 'var(--color-text-secondary)';

    const parts = [];
    let totalLines = 0;
    let hasError = false;

    for (const lsId of checked) {
      const ls = this._logSources.find(l => l.id === lsId);
      if (!ls) continue;
      try {
        const res = await Api.fetchLogSource(this._projectId, lsId,
          keyword ? { keyword } : {});
        if (res.success) {
          parts.push(`=== 来源: ${ls.name} (${Utils.logSourceTypeLabel(ls.type)}) ===\n${res.content || '(空)'}`);
          totalLines += res.lineCount || 0;
        } else {
          parts.push(`=== 来源: ${ls.name} [拉取失败] ===\n${res.message || res.error || '未知错误'}`);
          hasError = true;
        }
      } catch (e) {
        parts.push(`=== 来源: ${ls.name} [拉取失败] ===\n${e.message}`);
        hasError = true;
      }
    }

    const textarea = document.getElementById('wbLogContent');
    textarea.value = parts.join('\n\n');
    resultEl.textContent = `已拉取 ${checked.length} 个日志源，共 ${totalLines} 行${hasError ? '（部分失败）' : ''}`;
    resultEl.style.color = hasError ? 'var(--color-danger, #dc3545)' : 'var(--color-success, #28a745)';
  },

  /** 提交异常分析：先同步工具配置到项目，再调用诊断 */
  async _submitAnalysis() {
    const stackTrace = document.getElementById('wbStackTrace').value.trim();
    const logContent = document.getElementById('wbLogContent').value.trim();
    if (!stackTrace && !logContent) {
      Utils.notify('请提供异常堆栈或日志内容（至少一项）', 'error');
      return;
    }

    const loading = document.getElementById('wbLoading');
    loading.style.display = 'inline';
    document.getElementById('wbSubmitBtn').disabled = true;

    try {
      await this._syncToolsIfNeeded();

      const body = { projectId: this._projectId };
      if (stackTrace) body.stackTrace = stackTrace;
      if (logContent) body.logContent = logContent;
      if (this._conversationId) body.conversationId = this._conversationId;

      const res = await Api.diagnose(body);
      if (!res.success) { Utils.notify(res.error, 'error'); return; }

      this._conversationId = res.conversationId;
      this._renderReport(res.report);
      document.getElementById('wbChatInputRow').style.display = 'flex';
      document.getElementById('wbChatBox').innerHTML = '';
      this._addChatMsg('agent', '✅ 诊断完成：<b>' + Utils.escapeHtml(res.report.summary) + '</b>');
    } catch (e) {
      Utils.notify('分析失败: ' + e.message, 'error');
    } finally {
      loading.style.display = 'none';
      document.getElementById('wbSubmitBtn').disabled = false;
    }
  },

  /** 工具勾选与项目配置不一致时，先更新项目工具配置 */
  async _syncToolsIfNeeded() {
    const project = AppState.projects.find(p => p.id === this._projectId);
    if (!project) return;

    const current = Utils.safeJsonArray(project.enabledTools);
    const selected = this._getCheckedTools();
    const same = current.length === selected.length && current.every(t => selected.includes(t));
    if (same) return;

    const res = await Api.setProjectTools(this._projectId, selected);
    if (res.success) {
      project.enabledTools = JSON.stringify(selected);
    }
  },

  /** 渲染诊断报告（结构同 DiagnosisTab._renderReport） */
  _renderReport(report) {
    document.getElementById('wbReportCard').style.display = 'block';
    document.getElementById('wbTraceId').textContent =
      report.traceId ? 'Trace: ' + report.traceId.slice(0, 16) + '...' : '';

    let html = Utils.html`
      <div class="result-section">
        <h4>摘要</h4>
        <p>${Utils.escapeHtml(report.summary)}</p>
      </div>
      <div class="result-meta">
        <span class="badge badge-${report.severity}">${Utils.severityLabel(report.severity)}</span>
        <span>${Utils.escapeHtml(report.exceptionType)}</span>
        <span>${Utils.urgencyLabel(report.urgency)}</span>
        <span>置信度: ${Utils.formatConfidence(report.confidence)}</span>
      </div>
      <div class="result-section">
        <h4>置信度</h4>
        <div class="confidence-bar"><div class="confidence-fill" style="width:${Math.min(100, Math.round(report.confidence * 100))}%"></div></div>
      </div>
      <div class="result-section">
        <h4>根因分析</h4>
        <p>${Utils.escapeHtml(report.likelyRootCause)}</p>
      </div>
      <div class="result-section">
        <h4>影响范围</h4>
        <p>${Utils.escapeHtml(report.impactScope)}</p>
      </div>`;

    if (report.gitBlameHints && report.gitBlameHints.length > 0) {
      html += Utils.html`
        <div class="result-section result-section-highlight">
          <h4>🔍 Git Blame 线索</h4>
          <ul>${report.gitBlameHints.map(h => `<li>${Utils.escapeHtml(h)}</li>`).join('')}</ul>
        </div>`;
    }
    if (report.environmentFactors && report.environmentFactors.length > 0) {
      html += Utils.html`
        <div class="result-section result-section-highlight">
          <h4>⚙️ 环境因素</h4>
          <ul>${report.environmentFactors.map(f => `<li>${Utils.escapeHtml(f)}</li>`).join('')}</ul>
        </div>`;
    }
    if (report.logContextSummary && report.logContextSummary.trim()) {
      html += Utils.html`
        <div class="result-section result-section-highlight">
          <h4>📋 日志上下文摘要</h4>
          <p>${Utils.escapeHtml(report.logContextSummary)}</p>
        </div>`;
    }
    if (report.relatedModules && report.relatedModules.length > 0) {
      html += Utils.html`
        <div class="result-section">
          <h4>关联模块</h4>
          ${report.relatedModules.map(m => `<span class="tag">${Utils.escapeHtml(m)}</span>`).join('')}
        </div>`;
    }
    if (report.recommendations && report.recommendations.length > 0) {
      html += Utils.html`
        <div class="result-section">
          <h4>修复建议</h4>
          <ol>${report.recommendations.map(r => `<li>${Utils.escapeHtml(r)}</li>`).join('')}</ol>
        </div>`;
    }

    // 引导性追问（V1.4）— 信息不足时引导用户补充
    if (report.followUpQuestions && report.followUpQuestions.length > 0) {
      html += Utils.html`
        <div class="result-section result-section-highlight">
          <h4>❓ 需要更多信息</h4>
          <p style="font-size:13px;margin-bottom:8px">当前信息不足以完全确定根因，请补充以下信息以获得更精确的诊断：</p>
          <ul>${report.followUpQuestions.map(q => `<li>${Utils.escapeHtml(q)}</li>`).join('')}</ul>
        </div>`;
    }

    document.getElementById('wbReport').innerHTML = html;
  },

  async _sendChat() {
    const input = document.getElementById('wbChatInput');
    const message = input.value.trim();
    if (!message || !this._conversationId) return;
    input.value = '';

    this._addChatMsg('user', message);
    this._toolRound = 0;
    this._toolHistory = [];
    const sendBtn = document.getElementById('wbChatSend');
    sendBtn.disabled = true;

    try {
      const body = { conversationId: this._conversationId, message };
      if (this._projectId) body.projectId = this._projectId;

      const res = await Api.chat(body);
      if (!res.success) { Utils.notify(res.error, 'error'); return; }
      this._handleChatResponse(res);
    } catch (e) {
      Utils.notify('追问失败: ' + e.message, 'error');
    } finally {
      sendBtn.disabled = false;
    }
  },

  /** 处理追问/继续的响应：先展示执行结果，再展示待批准工具或最终回复 */
  _handleChatResponse(res) {
    // 1. 先展示本轮刚执行的工具结果（如果有）
    if (res.executedToolResults && res.executedToolResults.length > 0) {
      this._toolHistory = (this._toolHistory || []).concat(res.executedToolResults);
      this._renderToolResults(res.executedToolResults);
    }

    // 2. 待批准工具调用 → 渲染进度条 + 批准卡片
    if (res.pendingToolCalls && res.pendingToolCalls.length > 0) {
      this._toolSessionId = res.sessionId;
      this._toolRound = res.toolRound || (this._toolRound || 0) + 1;
      this._maxToolRounds = res.maxToolRounds || 8;
      this._renderToolApproval(res.pendingToolCalls);
    } else {
      // 3. 最终回复 → 清空循环状态
      this._toolSessionId = null;
      this._toolRound = 0;
      this._toolHistory = [];
      this._addChatMsg('agent', res.reply || '(空回复)');
    }
  },

  /** 渲染可折叠的工具执行结果卡片 */
  _renderToolResults(results) {
    const box = document.getElementById('wbChatBox');
    results.forEach(r => {
      const msg = document.createElement('div');
      msg.className = 'chat-msg agent';
      const statusIcon = r.success ? '✅' : '❌';
      const resultClass = r.success ? 'success' : 'failure';
      msg.innerHTML = Utils.html`
        <div class="chat-bubble" style="max-width:600px">
          <div class="tool-result-card ${resultClass}">
            <div class="tool-result-header" onclick="this.nextElementSibling.classList.toggle('collapsed')">
              <span class="tool-result-status">${statusIcon}</span>
              <span class="tool-result-name">${Utils.escapeHtml(r.name)}</span>
              <span class="tool-result-toggle">展开/折叠</span>
            </div>
            <div class="tool-result-body">
              <div class="tool-result-args">参数: ${Utils.escapeHtml(r.arguments || '{}')}</div>
              <div class="tool-result-output">${Utils.escapeHtml(r.result || '(无输出)')}</div>
            </div>
          </div>
        </div>`;
      box.appendChild(msg);
    });
    box.scrollTop = box.scrollHeight;
  },

  /** 渲染工具调用批准卡片（进度条 + 预勾选 + 可编辑参数） */
  _renderToolApproval(pendingToolCalls) {
    const box = document.getElementById('wbChatBox');
    const round = this._toolRound || 1;
    const maxRound = this._maxToolRounds || 8;
    const progress = Math.round((round / maxRound) * 100);

    const card = document.createElement('div');
    card.className = 'chat-msg agent';
    card.id = 'wbToolApprovalCard';

    const toolRows = pendingToolCalls.map(tc => {
      const tool = AppState.tools.find(t => t.name === tc.name);
      const desc = tool ? tool.description : '';
      const args = tc.arguments || '{}';
      return Utils.html`
        <div class="tool-approval-row">
          <label style="display:flex;align-items:center;gap:8px;margin-bottom:6px">
            <input type="checkbox" data-tool-id="${tc.id}" checked>
            <strong>${Utils.escapeHtml(tc.name)}</strong>
          </label>
          <div style="font-size:12px;color:var(--color-text-secondary);margin-bottom:6px">${Utils.escapeHtml(desc)}</div>
          <textarea class="form-textarea" data-tool-args="${tc.id}" rows="3"
            style="font-size:12px;font-family:monospace">${Utils.escapeHtml(args)}</textarea>
        </div>`;
    }).join('');

    card.innerHTML = Utils.html`
      <div class="chat-bubble" style="max-width:600px">
        <div class="tool-loop-progress">
          <span class="tool-loop-badge">第 ${round}/${maxRound} 轮</span>
          <div class="tool-loop-bar"><div class="tool-loop-bar-fill" style="width:${progress}%"></div></div>
          <span class="tool-loop-remaining">剩余 ${maxRound - round} 轮</span>
        </div>
        <div class="tool-approval-card">
          <div class="tool-approval-title">🔧 待批准工具调用</div>
          ${toolRows}
          <button class="btn btn-primary btn-sm" id="wbApproveToolsBtn">批准执行</button>
          <span id="wbApproveLoading" style="display:none;margin-left:8px">
            <span class="spinner"></span>执行中...
          </span>
        </div>
      </div>`;
    box.appendChild(card);
    box.scrollTop = box.scrollHeight;

    document.getElementById('wbApproveToolsBtn').onclick = () => this._approveTools();
  },

  /** 收集勾选的工具调用，调用 continue 端点继续推理循环 */
  async _approveTools() {
    const card = document.getElementById('wbToolApprovalCard');
    if (!card) return;

    const approved = [];
    card.querySelectorAll('.tool-approval-row').forEach(row => {
      const cb = row.querySelector('input[type="checkbox"]');
      if (!cb.checked) return;
      const id = cb.dataset.toolId;
      const argsTextarea = row.querySelector(`textarea[data-tool-args="${id}"]`);
      approved.push({ id, name: row.querySelector('strong').textContent, arguments: argsTextarea.value });
    });

    if (approved.length === 0) {
      Utils.notify('请至少勾选一个工具调用', 'error');
      return;
    }

    const btn = document.getElementById('wbApproveToolsBtn');
    const loading = document.getElementById('wbApproveLoading');
    btn.disabled = true;
    loading.style.display = 'inline';

    try {
      const res = await Api.continueChat({
        sessionId: this._toolSessionId,
        approvedToolCalls: approved
      });
      if (!res.success) { Utils.notify(res.error, 'error'); return; }

      // 移除批准卡片，展示工具执行后的响应
      card.remove();
      this._handleChatResponse(res);
    } catch (e) {
      Utils.notify('工具执行失败: ' + e.message, 'error');
    } finally {
      btn.disabled = false;
      loading.style.display = 'none';
    }
  },

  _addChatMsg(role, content) {
    const box = document.getElementById('wbChatBox');
    const empty = box.querySelector('.empty-state');
    if (empty) empty.remove();

    const div = document.createElement('div');
    div.className = `chat-msg ${role}`;
    div.innerHTML = `<div class="chat-bubble">${Utils.escapeHtml(content).replace(/\n/g, '<br>')}</div>`;
    box.appendChild(div);
    box.scrollTop = box.scrollHeight;
  }
};
