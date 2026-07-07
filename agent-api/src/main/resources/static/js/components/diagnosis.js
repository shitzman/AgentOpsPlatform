/* ================================================================
   diagnosis.js — 诊断测试组件
   ================================================================ */

const DiagnosisTab = {

  _container: null,
  _conversationId: null,

  init(container) {
    this._container = container;
    this.render();
    this._bindEvents();

    // 监听项目选择变更
    EventBus.on('project-selected', ({ projectId }) => {
      document.getElementById('diagProjectSelect').value = projectId || '';
      this._renderActiveTools();
    });

    EventBus.on('projects-changed', () => this._populateProjectSelect());
    EventBus.on('tab-switched', ({ tabName }) => {
      if (tabName === 'diagnosis') this._populateProjectSelect();
    });
  },

  render() {
    this._container.innerHTML = Utils.html`
      <div class="row">
        <div class="col">
          <div class="card">
            <div class="card-header"><h3 class="card-title">堆栈输入</h3></div>
            <div class="form-group">
              <label class="form-label">选择项目（可选）</label>
              <select class="form-select" id="diagProjectSelect">
                <option value="">-- 全局模式（不关联项目） --</option>
              </select>
            </div>
            <div id="diagActiveTools" style="margin-bottom:10px"></div>
            <div class="form-group">
              <label class="form-label">预设堆栈</label>
              <div class="preset-list" id="presetList"></div>
            </div>
            <div class="form-group">
              <textarea class="form-textarea" id="diagStackTrace" rows="8"
                placeholder="粘贴 Java 异常堆栈..."></textarea>
            </div>
            <button class="btn btn-primary" id="diagSubmit">提交诊断</button>
            <span id="diagLoading" style="display:none;margin-left:12px">
              <span class="spinner"></span>分析中...
            </span>
          </div>

          <div class="card" id="diagResultCard" style="display:none">
            <div class="card-header"><h3 class="card-title">诊断报告</h3></div>
            <div id="diagResult"></div>
          </div>
        </div>

        <div class="col">
          <div class="card">
            <div class="card-header"><h3 class="card-title">追问对话</h3></div>
            <div class="chat-box" id="chatBox">
              <div class="empty-state"><div class="empty-state-icon">💬</div>提交诊断后可在此追问</div>
            </div>
            <div class="chat-input-row" id="chatInputRow" style="display:none">
              <input class="form-input" id="chatInput" placeholder="输入追问内容...">
              <button class="btn btn-primary btn-sm" id="chatSend">发送</button>
            </div>
          </div>
        </div>
      </div>`;
  },

  _bindEvents() {
    // 预设按钮
    this._renderPresets();
    document.getElementById('diagSubmit').onclick = () => this._submitDiagnosis();
    document.getElementById('chatSend').onclick = () => this._sendChat();
    document.getElementById('chatInput').onkeydown = (e) => {
      if (e.key === 'Enter') this._sendChat();
    };
    document.getElementById('diagProjectSelect').onchange = (e) => {
      const projectId = e.target.value;
      EventBus.emit('project-selected', { projectId: projectId || null });
    };
  },

  _renderPresets() {
    const presets = [
      { label: 'NullPointerException', trace: 'java.lang.NullPointerException: Cannot invoke "String.length()" because "name" is null\n\tat com.agentops.demo.OrderService.getOrderName(OrderService.java:42)\n\tat com.agentops.demo.OrderController.getOrder(OrderController.java:28)\n\tat java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\n\tat org.springframework.web.method.support.InvocableHandlerMethod.doInvoke(InvocableHandlerMethod.java:205)' },
      { label: 'IllegalArgumentException', trace: 'java.lang.IllegalArgumentException: 订单ID不能为空\n\tat com.agentops.demo.OrderValidator.validate(OrderValidator.java:15)\n\tat com.agentops.demo.OrderService.createOrder(OrderService.java:68)\n\tat com.agentops.demo.OrderController.createOrder(OrderController.java:35)' },
      { label: 'SQL DataIntegrity', trace: 'org.springframework.dao.DataIntegrityViolationException: could not execute statement; SQL [n/a]; constraint [fk_order_user_id]\n\tat org.springframework.orm.jpa.vendor.HibernateJpaDialect.convertHibernateAccessException(HibernateJpaDialect.java:298)\n\tat com.agentops.demo.OrderRepository.save(OrderRepository.java:18)\nCaused by: org.h2.jdbc.JdbcSQLIntegrityConstraintViolationException: Referential integrity constraint violation' }
    ];

    const el = document.getElementById('presetList');
    presets.forEach(p => {
      const btn = document.createElement('button');
      btn.className = 'preset-btn';
      btn.textContent = p.label;
      btn.onclick = () => { document.getElementById('diagStackTrace').value = p.trace; };
      el.appendChild(btn);
    });
  },

  _populateProjectSelect() {
    const sel = document.getElementById('diagProjectSelect');
    if (!sel) return;
    sel.innerHTML = '<option value="">-- 全局模式（不关联项目） --</option>';
    AppState.projects.forEach(p => {
      const opt = document.createElement('option');
      opt.value = p.id;
      opt.textContent = p.name;
      if (p.id === AppState.selectedProjectId) opt.selected = true;
      sel.appendChild(opt);
    });
  },

  _renderActiveTools() {
    const el = document.getElementById('diagActiveTools');
    if (!el) return;
    const projectId = document.getElementById('diagProjectSelect')?.value;
    if (!projectId) { el.innerHTML = ''; return; }

    const project = AppState.projects.find(p => p.id === projectId);
    if (!project) { el.innerHTML = ''; return; }

    const tools = project.enabledTools || [];
    el.innerHTML = `<span style="font-size:12px;color:var(--color-text-secondary)">活动工具：</span>` +
      tools.map(t => `<span class="tag tool-tag">${t}</span>`).join('') +
      (tools.length === 0 ? '<span style="font-size:12px;color:var(--color-text-secondary)">（无）</span>' : '');
  },

  async _submitDiagnosis() {
    const stackTrace = document.getElementById('diagStackTrace').value.trim();
    if (!stackTrace) { Utils.notify('请输入异常堆栈', 'error'); return; }

    const projectId = document.getElementById('diagProjectSelect')?.value || undefined;
    const loading = document.getElementById('diagLoading');
    loading.style.display = 'inline';

    try {
      const body = { stackTrace };
      if (this._conversationId) body.conversationId = this._conversationId;
      if (projectId) body.projectId = projectId;

      const res = await Api.diagnose(body);
      if (!res.success) { Utils.notify(res.error, 'error'); return; }

      this._conversationId = res.conversationId;
      this._renderReport(res.report);
      document.getElementById('chatInputRow').style.display = 'flex';

      // 把诊断摘要加入对话
      document.getElementById('chatBox').innerHTML = '';
      this._addChatMsg('agent', '✅ 诊断完成：**' + res.report.summary + '**');
    } catch (e) {
      Utils.notify('诊断失败: ' + e.message, 'error');
    } finally {
      loading.style.display = 'none';
    }
  },

  _renderReport(report) {
    document.getElementById('diagResultCard').style.display = 'block';
    document.getElementById('diagResult').innerHTML = Utils.html`
      <div class="result-section">
        <h4>摘要</h4>
        <p>${report.summary}</p>
      </div>
      <div class="result-section" style="display:flex;gap:12px;flex-wrap:wrap">
        <div>
          <span class="badge badge-${report.severity}">${Utils.severityLabel(report.severity)}</span>
          <span style="margin-left:8px">${report.exceptionType}</span>
        </div>
        <div>${Utils.urgencyLabel(report.urgency)}</div>
        ${report.traceId ? `<div style="font-size:12px;color:var(--color-text-secondary)">Trace: ${report.traceId.slice(0,16)}...</div>` : ''}
      </div>
      <div class="result-section">
        <h4>置信度</h4>
        <div class="confidence-bar"><div class="confidence-fill" style="width:${Math.round(report.confidence * 100)}%"></div></div>
        <span style="font-size:12px;color:var(--color-text-secondary)">${Math.round(report.confidence * 100)}%</span>
      </div>
      <div class="result-section">
        <h4>根因分析</h4>
        <p>${report.likelyRootCause}</p>
      </div>
      <div class="result-section">
        <h4>影响范围</h4>
        <p>${report.impactScope}</p>
      </div>
      ${report.relatedModules?.length ? Utils.html`
        <div class="result-section">
          <h4>关联模块</h4>
          ${report.relatedModules.map(m => `<span class="tag">${m}</span>`).join('')}
        </div>` : ''}
      ${report.recommendations?.length ? Utils.html`
        <div class="result-section">
          <h4>修复建议</h4>
          <ol>${report.recommendations.map(r => `<li>${r}</li>`).join('')}</ol>
        </div>` : ''}`;
  },

  async _sendChat() {
    const input = document.getElementById('chatInput');
    const message = input.value.trim();
    if (!message || !this._conversationId) return;
    input.value = '';

    this._addChatMsg('user', message);
    const sendBtn = document.getElementById('chatSend');
    sendBtn.disabled = true;

    try {
      const body = { conversationId: this._conversationId, message };
      const projectId = document.getElementById('diagProjectSelect')?.value || undefined;
      if (projectId) body.projectId = projectId;

      const res = await Api.chat(body);
      if (!res.success) { Utils.notify(res.error, 'error'); return; }
      this._addChatMsg('agent', res.reply);
    } catch (e) {
      Utils.notify('追问失败: ' + e.message, 'error');
    } finally {
      sendBtn.disabled = false;
    }
  },

  _addChatMsg(role, content) {
    const box = document.getElementById('chatBox');
    // 移除空状态
    const empty = box.querySelector('.empty-state');
    if (empty) empty.remove();

    const div = document.createElement('div');
    div.className = `chat-msg ${role}`;
    div.innerHTML = `<div class="chat-bubble">${Utils.escapeHtml(content).replace(/\n/g, '<br>')}</div>`;
    box.appendChild(div);
    box.scrollTop = box.scrollHeight;
  }
};
