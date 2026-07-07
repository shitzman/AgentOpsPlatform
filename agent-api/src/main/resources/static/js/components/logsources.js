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
      if (tabName === 'logsources') this._refresh();
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
                  ? `路径: ${ls.properties?.filePath || '未配置'}`
                  : `ES: ${ls.properties?.esUrl || '未配置'} / ${ls.properties?.index || '未配置'}`}
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
          <option value="FILE_PATH">文件路径</option>
          <option value="ELASTICSEARCH">Elasticsearch</option>
        </select>
      </div>
      <div id="lsFormDynamic"></div>`;

    Utils.showModal('添加日志源', bodyHtml, async () => {
      const name = document.getElementById('lsFormName').value.trim();
      const type = document.getElementById('lsFormType').value;
      if (!name) { Utils.notify('名称不能为空', 'error'); return; }

      const properties = {};
      if (type === 'TEXT_INPUT') {
        properties.rawText = document.getElementById('lsFormRawText')?.value || '';
      } else if (type === 'FILE_PATH') {
        properties.filePath = document.getElementById('lsFormFilePath')?.value || '';
      } else if (type === 'ELASTICSEARCH') {
        properties.esUrl = document.getElementById('lsFormEsUrl')?.value || '';
        properties.index = document.getElementById('lsFormEsIndex')?.value || '';
      }

      try {
        const res = await Api.addLogSource(this._projectId, { name, type, properties });
        if (res.success) { Utils.notify('日志源已添加', 'success'); this._refresh(); }
        else Utils.notify(res.error, 'error');
      } catch (e) { Utils.notify('添加失败: ' + e.message, 'error'); }
    }, '添加');

    // 动态渲染类型相关字段
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
            <label class="form-label">文件路径 *</label>
            <input class="form-input" id="lsFormFilePath" placeholder="/var/log/app.log">
          </div>`;
      } else if (type === 'ELASTICSEARCH') {
        container.innerHTML = Utils.html`
          <div class="form-group">
            <label class="form-label">ES 地址</label>
            <input class="form-input" id="lsFormEsUrl" placeholder="http://es-cluster:9200">
          </div>
          <div class="form-group">
            <label class="form-label">索引名</label>
            <input class="form-input" id="lsFormEsIndex" placeholder="app-logs-*">
          </div>`;
      }
    };

    document.getElementById('lsFormType').onchange = renderDynamic;
    renderDynamic();
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
