/* ================================================================
   logsources.js — 日志源管理组件
   ================================================================ */

const LogSourcesTab = {

  _container: null,
  _projectId: null,
  _logSources: [],

  init(container) {
    this._container = container;
    this.render();

    EventBus.on('project-selected', ({ projectId }) => {
      this._projectId = projectId;
      if (AppState.currentTab === 'logsources') this._refresh();
    });

    EventBus.on('tab-switched', ({ tabName }) => {
      if (tabName === 'logsources') {
        this._populateProjectSelect();
        this._refresh();
      }
    });

    EventBus.on('projects-changed', () => {
      if (AppState.currentTab === 'logsources') this._populateProjectSelect();
    });
  },

  render() {
    this._container.innerHTML = Utils.html`
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px">
        <h2>日志源管理</h2>
        <button class="btn btn-primary" id="lsAddBtn" ${!this._projectId ? 'disabled' : ''}>+ 添加日志源</button>
      </div>
      <div class="form-group" style="margin-bottom:16px;max-width:400px">
        <label class="form-label">选择项目</label>
        <select class="form-select" id="lsProjectSelect">
          <option value="">-- 请先选择项目 --</option>
        </select>
      </div>
      <div id="lsList">
        <div class="empty-state">
          <div class="empty-state-icon">📋</div>
          <p>${this._projectId ? '该项目暂无日志源' : '请先选择一个项目'}</p>
        </div>
      </div>`;

    // 绑定事件
    document.getElementById('lsProjectSelect').onchange = (e) => {
      this._projectId = e.target.value || null;
      EventBus.emit('project-selected', { projectId: this._projectId });
      document.getElementById('lsAddBtn').disabled = !this._projectId;
      this._refresh();
    };

    document.getElementById('lsAddBtn').onclick = () => {
      if (this._projectId) this._showAddForm();
    };

    this._populateProjectSelect();
    if (this._projectId) document.getElementById('lsProjectSelect').value = this._projectId;
  },

  _populateProjectSelect() {
    const sel = document.getElementById('lsProjectSelect');
    if (!sel) return;
    sel.innerHTML = '<option value="">-- 请先选择项目 --</option>';
    AppState.projects.forEach(p => {
      const opt = document.createElement('option');
      opt.value = p.id;
      opt.textContent = p.name;
      if (p.id === this._projectId) opt.selected = true;
      sel.appendChild(opt);
    });
  },

  async _refresh() {
    if (!this._projectId) {
      document.getElementById('lsList').innerHTML =
        '<div class="empty-state"><div class="empty-state-icon">📋</div><p>请先选择一个项目</p></div>';
      return;
    }

    try {
      const res = await Api.getLogSources(this._projectId);
      if (res.success) {
        this._logSources = res.logSources || [];
        this._renderList();
      }
    } catch (e) { Utils.notify('加载日志源失败: ' + e.message, 'error'); }
  },

  _renderList() {
    const el = document.getElementById('lsList');
    if (!el) return;

    if (!this._logSources || this._logSources.length === 0) {
      el.innerHTML = '<div class="empty-state"><div class="empty-state-icon">📋</div><p>该项目暂无日志源</p></div>';
      return;
    }

    el.innerHTML = this._logSources.map(ls => Utils.html`
      <div class="logsource-item ${ls.enabled ? '' : 'disabled'}">
        <div class="logsource-item-left">
          <span class="logsource-type-badge">${Utils.logSourceTypeLabel(ls.type)}</span>
          <div>
            <strong>${ls.name}</strong>
            <div style="font-size:12px;color:var(--color-text-secondary);margin-top:2px">
              ${ls.type === 'TEXT_INPUT'
                ? `文本长度: ${(ls.properties?.rawText || '').length} 字符`
                : ls.type === 'FILE_PATH'
                  ? (ls.properties?.originalFileName
                    ? `文件: ${Utils.escapeHtml(ls.properties.originalFileName)}`
                    : `路径: ${Utils.escapeHtml(ls.properties?.filePath || '未配置')}`)
                  : `ES: ${Utils.escapeHtml(ls.properties?.esUrl || '未配置')} / ${Utils.escapeHtml(ls.properties?.index || '未配置')}`}
            </div>
          </div>
        </div>
        <div class="btn-group">
          <span style="font-size:12px;color:var(--color-text-secondary)">${Utils.formatTime(ls.createdAt)}</span>
          <button class="btn btn-outline btn-sm" data-action="toggle" data-id="${ls.id}">
            ${ls.enabled ? '禁用' : '启用'}
          </button>
          <button class="btn btn-danger btn-sm" data-action="delete" data-id="${ls.id}">删除</button>
        </div>
      </div>`).join('');

    // 绑定事件
    el.querySelectorAll('[data-action="toggle"]').forEach(btn => {
      btn.onclick = async () => {
        const ls = this._logSources.find(l => l.id === btn.dataset.id);
        if (!ls) return;
        try {
          const res = await Api.updateLogSource(this._projectId, ls.id, { enabled: !ls.enabled });
          if (res.success) { Utils.notify(`日志源已${ls.enabled ? '禁用' : '启用'}`, 'success'); this._refresh(); }
          else Utils.notify(res.error, 'error');
        } catch (e) { Utils.notify('操作失败: ' + e.message, 'error'); }
      };
    });

    el.querySelectorAll('[data-action="delete"]').forEach(btn => {
      btn.onclick = () => this._confirmDelete(btn.dataset.id);
    });
  },

  _showAddForm() {
    const bodyHtml = Utils.html`
      <div class="form-group">
        <label class="form-label">名称 *</label>
        <input class="form-input" id="lsFormName" placeholder="如：生产环境 ES">
      </div>
      <div class="form-group">
        <label class="form-label">类型 *</label>
        <select class="form-select" id="lsFormType">
          <option value="TEXT_INPUT">文本输入</option>
          <option value="FILE_PATH">文件上传 / 路径</option>
          <option value="ELASTICSEARCH">Elasticsearch</option>
        </select>
      </div>
      <div id="lsFormDynamic"></div>`;

    Utils.showModal('添加日志源', bodyHtml, async () => {
      const name = document.getElementById('lsFormName').value.trim();
      const type = document.getElementById('lsFormType').value;
      if (!name) { Utils.notify('名称不能为空', 'error'); return; }

      try {
        if (type === 'FILE_PATH') {
          await this._submitFilePathForm(name);
        } else {
          await this._submitJsonForm(name, type);
        }
      } catch (e) { Utils.notify('添加失败: ' + e.message, 'error'); }
    }, '添加');

    const renderDynamic = () => {
      const type = document.getElementById('lsFormType').value;
      const container = document.getElementById('lsFormDynamic');
      if (type === 'TEXT_INPUT') {
        container.innerHTML = Utils.html`
          <div class="form-group">
            <label class="form-label">日志文本 *</label>
            <textarea class="form-textarea" id="lsFormRawText" rows="8"
              placeholder="粘贴原始日志文本..."></textarea>
          </div>`;
      } else if (type === 'FILE_PATH') {
        container.innerHTML = Utils.html`
          <div class="form-group">
            <label class="form-label">上传日志文件 *</label>
            <input type="file" id="lsFormFile" class="form-input"
              accept=".log,.txt,.out,text/*">
            <div style="font-size:12px;color:var(--color-text-secondary);margin-top:4px">
              选择本地日志文件上传（限 10MB）
            </div>
          </div>
          <div class="form-group">
            <label class="form-label">或手动输入服务器路径（高级）</label>
            <input class="form-input" id="lsFormFilePath" placeholder="/var/log/app.log">
            <div style="font-size:12px;color:var(--color-text-secondary);margin-top:4px">
              如不上传文件，可填写服务器本地日志文件路径
            </div>
          </div>`;
      } else if (type === 'ELASTICSEARCH') {
        container.innerHTML = Utils.html`
          <div class="form-group">
            <label class="form-label">ES 地址 *</label>
            <input class="form-input" id="lsFormEsUrl" placeholder="http://es-cluster:9200">
          </div>
          <div class="form-group">
            <label class="form-label">索引名 *</label>
            <input class="form-input" id="lsFormEsIndex" placeholder="app-logs-*">
          </div>
          <div class="form-group">
            <label class="form-label">Basic Auth 用户名（可选）</label>
            <input class="form-input" id="lsFormEsUsername" placeholder="elastic">
          </div>
          <div class="form-group">
            <label class="form-label">Basic Auth 密码（可选）</label>
            <input type="password" class="form-input" id="lsFormEsPassword" placeholder="changeme">
          </div>
          <div class="form-group">
            <label class="form-label">或 API Key（可选，与 Basic Auth 二选一）</label>
            <input class="form-input" id="lsFormEsApiKey" placeholder="encoded_api_key">
          </div>
          <div class="form-group">
            <button class="btn btn-outline btn-sm" id="lsFormTestBtn" type="button">测试连接</button>
            <span id="lsFormTestResult" style="margin-left:8px;font-size:13px"></span>
          </div>`;
        document.getElementById('lsFormTestBtn').onclick = () => this._testEsConnection();
      }
    };

    document.getElementById('lsFormType').onchange = renderDynamic;
    renderDynamic();
  },

  /** 提交 FILE_PATH 表单：有文件走上传，否则走手动路径 */
  async _submitFilePathForm(name) {
    const fileInput = document.getElementById('lsFormFile');
    const filePath = document.getElementById('lsFormFilePath')?.value.trim() || '';

    if (fileInput && fileInput.files && fileInput.files.length > 0) {
      const res = await Api.uploadLogSource(this._projectId, name, fileInput.files[0]);
      if (res.success) { Utils.notify('日志源已添加', 'success'); this._refresh(); }
      else Utils.notify(res.error, 'error');
    } else if (filePath) {
      const res = await Api.addLogSource(this._projectId,
        { name, type: 'FILE_PATH', properties: { filePath } });
      if (res.success) { Utils.notify('日志源已添加', 'success'); this._refresh(); }
      else Utils.notify(res.error, 'error');
    } else {
      Utils.notify('请上传文件或填写服务器路径', 'error');
    }
  },

  /** 提交 TEXT_INPUT / ELASTICSEARCH 表单（JSON 方式） */
  async _submitJsonForm(name, type) {
    const properties = {};
    if (type === 'TEXT_INPUT') {
      properties.rawText = document.getElementById('lsFormRawText')?.value || '';
    } else if (type === 'ELASTICSEARCH') {
      properties.esUrl = document.getElementById('lsFormEsUrl')?.value.trim() || '';
      properties.index = document.getElementById('lsFormEsIndex')?.value.trim() || '';
      const username = document.getElementById('lsFormEsUsername')?.value.trim() || '';
      const password = document.getElementById('lsFormEsPassword')?.value || '';
      const apiKey = document.getElementById('lsFormEsApiKey')?.value.trim() || '';
      if (username) { properties.username = username; properties.password = password; }
      if (apiKey) { properties.apiKey = apiKey; }
    }

    const res = await Api.addLogSource(this._projectId, { name, type, properties });
    if (res.success) { Utils.notify('日志源已添加', 'success'); this._refresh(); }
    else Utils.notify(res.error, 'error');
  },

  /** 测试 ES 连接配置 */
  async _testEsConnection() {
    const resultEl = document.getElementById('lsFormTestResult');
    const properties = {
      esUrl: document.getElementById('lsFormEsUrl')?.value.trim() || '',
      index: document.getElementById('lsFormEsIndex')?.value.trim() || ''
    };
    const username = document.getElementById('lsFormEsUsername')?.value.trim() || '';
    const password = document.getElementById('lsFormEsPassword')?.value || '';
    const apiKey = document.getElementById('lsFormEsApiKey')?.value.trim() || '';
    if (username) { properties.username = username; properties.password = password; }
    if (apiKey) { properties.apiKey = apiKey; }

    resultEl.textContent = '测试中...';
    resultEl.style.color = 'var(--color-text-secondary)';
    try {
      const res = await Api.testLogSource({ type: 'ELASTICSEARCH', properties });
      if (res.success) {
        resultEl.textContent = res.message || '连接成功';
        resultEl.style.color = 'var(--color-success, #28a745)';
      } else {
        resultEl.textContent = res.message || '连接失败';
        resultEl.style.color = 'var(--color-danger, #dc3545)';
      }
    } catch (e) {
      resultEl.textContent = '测试失败: ' + e.message;
      resultEl.style.color = 'var(--color-danger, #dc3545)';
    }
  },

  _confirmDelete(lsId) {
    const ls = this._logSources.find(l => l.id === lsId);
    if (!ls) return;

    Utils.showModal(
      '删除日志源',
      `<p>确定要删除日志源 <strong>${Utils.escapeHtml(ls.name)}</strong> 吗？</p>`,
      async () => {
        try {
          const res = await Api.deleteLogSource(this._projectId, lsId);
          if (res.success) { Utils.notify('日志源已删除', 'success'); this._refresh(); }
          else Utils.notify(res.error, 'error');
        } catch (e) { Utils.notify('删除失败: ' + e.message, 'error'); }
      },
      '确认删除'
    );
  }
};
