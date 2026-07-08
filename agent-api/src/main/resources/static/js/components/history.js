/* ================================================================
   history.js — 诊断历史组件 (V1.0 Phase 3 新增)
   ================================================================ */

const HistoryTab = {

  _container: null,
  _page: 0,
  _pageSize: 15,
  _projectId: null,

  init(container) {
    this._container = container;
    this.render();
    this._bindEvents();

    EventBus.on('projects-changed', () => this._populateProjectSelect());
    EventBus.on('tab-switched', ({ tabName }) => {
      if (tabName === 'history') {
        this._populateProjectSelect();
        this._loadHistory();
      }
    });
  },

  render() {
    this._container.innerHTML = Utils.html`
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px;flex-wrap:wrap;gap:8px">
        <h2>诊断历史</h2>
        <div style="display:flex;gap:8px;align-items:center">
          <select class="form-select" id="histProjectSelect" style="width:200px">
            <option value="">全部项目</option>
          </select>
          <button class="btn btn-outline btn-sm" id="histRefresh">刷新</button>
        </div>
      </div>
      <div id="histTableContainer">
        <div class="empty-state"><div class="empty-state-icon">📊</div><p>暂无诊断记录</p></div>
      </div>
      <div class="pagination" id="histPagination" style="display:none"></div>`;

    this._populateProjectSelect();
  },

  _bindEvents() {
    document.getElementById('histProjectSelect').onchange = (e) => {
      this._projectId = e.target.value || null;
      this._page = 0;
      this._loadHistory();
    };
    document.getElementById('histRefresh').onclick = () => this._loadHistory();
  },

  _populateProjectSelect() {
    const sel = document.getElementById('histProjectSelect');
    if (!sel) return;
    const currentVal = sel.value;
    sel.innerHTML = '<option value="">全部项目</option>';
    AppState.projects.forEach(p => {
      const opt = document.createElement('option');
      opt.value = p.id;
      opt.textContent = p.name;
      if (p.id === currentVal) opt.selected = true;
      sel.appendChild(opt);
    });
  },

  async _loadHistory() {
    const container = document.getElementById('histTableContainer');
    if (!container) return;
    container.innerHTML = '<div class="empty-state"><span class="spinner"></span> 加载中...</div>';

    try {
      const res = await Api.getDiagnosisHistory(this._projectId, this._page, this._pageSize);
      if (!res.success) { Utils.notify(res.error, 'error'); return; }

      const reports = res.reports || [];
      if (reports.length === 0) {
        container.innerHTML = '<div class="empty-state"><div class="empty-state-icon">📊</div><p>暂无诊断记录</p></div>';
        document.getElementById('histPagination').style.display = 'none';
        return;
      }

      this._renderTable(reports);
      this._renderPagination(res.total, res.page, res.size);
    } catch (e) {
      container.innerHTML = `<div class="empty-state"><p style="color:var(--color-danger)">加载失败: ${Utils.escapeHtml(e.message)}</p></div>`;
    }
  },

  _renderTable(reports) {
    const container = document.getElementById('histTableContainer');

    const rows = reports.map(r => {
      const projectName = AppState.projects.find(p => p.id === r.projectId)?.name || r.projectId || '—';
      return Utils.html`
        <tr class="hist-row" data-id="${r.id}">
          <td>${Utils.formatTime(r.createdAt)}</td>
          <td>${Utils.escapeHtml(projectName)}</td>
          <td><span class="badge badge-${r.severity || 'medium'}">${Utils.severityLabel(r.severity)}</span></td>
          <td class="hist-summary-cell" title="${Utils.escapeHtml(r.summary)}">${Utils.escapeHtml(Utils.truncate(r.summary, 60))}</td>
          <td>${Utils.escapeHtml(Utils.truncate(r.exceptionType, 30))}</td>
          <td>${Utils.formatConfidence(r.confidence)}</td>
          <td><button class="btn btn-outline btn-sm hist-detail-btn" data-id="${r.id}">详情</button></td>
        </tr>`;
    }).join('');

    container.innerHTML = Utils.html`
      <div class="hist-table-wrapper">
        <table class="hist-table">
          <thead>
            <tr>
              <th>时间</th>
              <th>项目</th>
              <th>严重度</th>
              <th>摘要</th>
              <th>异常类型</th>
              <th>置信度</th>
              <th></th>
            </tr>
          </thead>
          <tbody>${rows}</tbody>
        </table>
      </div>`;

    // 绑定详情按钮
    container.querySelectorAll('.hist-detail-btn').forEach(btn => {
      btn.onclick = () => {
        const report = reports.find(r => r.id === btn.dataset.id);
        if (report) this._showDetail(report);
      };
    });
  },

  _renderPagination(total, page, size) {
    const el = document.getElementById('histPagination');
    const totalPages = Math.ceil(total / size);
    if (totalPages <= 1) { el.style.display = 'none'; return; }
    el.style.display = 'flex';

    el.innerHTML = Utils.html`
      <button class="btn btn-outline btn-sm" ${page === 0 ? 'disabled' : ''} id="histPrev">上一页</button>
      <span style="margin:0 12px;font-size:14px;color:var(--color-text-secondary)">
        第 ${page + 1} / ${totalPages} 页（共 ${total} 条）
      </span>
      <button class="btn btn-outline btn-sm" ${page >= totalPages - 1 ? 'disabled' : ''} id="histNext">下一页</button>`;

    document.getElementById('histPrev').onclick = () => {
      if (this._page > 0) { this._page--; this._loadHistory(); }
    };
    document.getElementById('histNext').onclick = () => {
      if (this._page < totalPages - 1) { this._page++; this._loadHistory(); }
    };
  },

  _showDetail(report) {
    let html = Utils.html`
      <div style="max-height:70vh;overflow-y:auto">
        <div class="result-section">
          <h4>摘要</h4>
          <p>${Utils.escapeHtml(report.summary)}</p>
        </div>
        <div class="result-meta">
          <span class="badge badge-${report.severity || 'medium'}">${Utils.severityLabel(report.severity)}</span>
          <span>${Utils.escapeHtml(report.exceptionType)}</span>
          <span>${Utils.urgencyLabel(report.urgency)}</span>
          <span>置信度: ${Utils.formatConfidence(report.confidence)}</span>
        </div>
        <div class="result-section">
          <h4>根因分析</h4>
          <p>${Utils.escapeHtml(report.rootCause || report.likelyRootCause || '')}</p>
        </div>
        <div class="result-section">
          <h4>影响范围</h4>
          <p>${Utils.escapeHtml(report.impactScope || '')}</p>
        </div>
        ${report.relatedModules ? Utils.html`
          <div class="result-section">
            <h4>关联模块</h4>
            ${Utils.safeJsonArray(report.relatedModules).map(m => `<span class="tag">${Utils.escapeHtml(m)}</span>`).join('')}
          </div>` : ''}
        ${report.recommendations ? Utils.html`
          <div class="result-section">
            <h4>修复建议</h4>
            <ol>${Utils.safeJsonArray(report.recommendations).map(r => `<li>${Utils.escapeHtml(r)}</li>`).join('')}</ol>
          </div>` : ''}
        ${report.rawTrace ? Utils.html`
          <div class="result-section">
            <h4>原始堆栈</h4>
            <pre style="font-size:11px;background:var(--color-bg);padding:12px;border-radius:6px;overflow-x:auto;max-height:200px">${Utils.escapeHtml(report.rawTrace)}</pre>
          </div>` : ''}
        ${report.traceId ? Utils.html`
          <div style="font-size:12px;color:var(--color-text-secondary);margin-top:12px">Trace ID: ${report.traceId}</div>` : ''}
      </div>`;

    Utils.showModal('诊断报告详情', html, null, '关闭');
  }
};
