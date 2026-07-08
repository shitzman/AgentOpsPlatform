/* ================================================================
   projects.js — 项目配置组件
   ================================================================ */

const ProjectsTab = {

  _container: null,
  _editingProjectId: null,

  init(container) {
    this._container = container;
    this.render();

    EventBus.on('projects-changed', ({ projects }) => this._renderProjectList(projects));
    EventBus.on('tab-switched', ({ tabName }) => {
      if (tabName === 'projects') this._refresh();
    });
  },

  render() {
    this._container.innerHTML = Utils.html`
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px">
        <h2>检测项目</h2>
        <button class="btn btn-primary" id="projNewBtn">+ 新建项目</button>
      </div>
      <div class="project-grid" id="projGrid">
        <div class="empty-state"><div class="empty-state-icon">📁</div><p>暂无检测项目</p></div>
      </div>`;

    document.getElementById('projNewBtn').onclick = () => this._showForm();
  },

  async _refresh() {
    try {
      const res = await Api.getProjects();
      if (res.success) {
        AppState.projects = res.projects;
        EventBus.emit('projects-changed', { projects: res.projects });
      } else {
        console.error('[ProjectsTab] 加载项目列表失败:', res.error);
        Utils.notify('加载项目列表失败: ' + (res.error || '未知错误'), 'error');
      }
    } catch (e) { Utils.notify('加载项目列表失败: ' + e.message, 'error'); }
  },

  _renderProjectList(projects) {
    const grid = document.getElementById('projGrid');
    if (!grid) { console.error('projGrid 元素不存在'); return; }

    if (!projects || projects.length === 0) {
      grid.innerHTML = '<div class="empty-state"><div class="empty-state-icon">📁</div><p>暂无检测项目</p></div>';
      return;
    }

    try {
      grid.innerHTML = projects.map(p => Utils.html`
      <div class="project-card">
        <div class="project-card-header">
          <div>
            <div class="project-card-name">${p.name}</div>
            ${p.description ? `<div style="font-size:13px;color:var(--color-text-secondary);margin-top:2px">${Utils.escapeHtml(p.description)}</div>` : ''}
          </div>
          <div class="btn-group">
            <button class="btn btn-outline btn-sm" data-action="edit" data-id="${p.id}">编辑</button>
            <button class="btn btn-danger btn-sm" data-action="delete" data-id="${p.id}">删除</button>
          </div>
        </div>
        <div class="project-card-meta">Git 本地路径：<span>${p.gitRepoLocalPath || '未配置'}</span></div>
        ${p.gitRepoUrl ? Utils.html`<div class="project-card-meta">Git 远程：<span>${Utils.escapeHtml(p.gitRepoUrl)}</span></div>` : ''}
        <div class="project-card-meta">日志源：<span>${Utils.safeJsonArray(p.logSourceIds).length} 个</span></div>
        <div>
          <span style="font-size:12px;color:var(--color-text-secondary)">启用的工具：</span>
          ${Utils.safeJsonArray(p.enabledTools).length === 0
            ? '<span style="font-size:12px;color:var(--color-text-secondary)">（无）</span>'
            : Utils.safeJsonArray(p.enabledTools).map(t => `<span class="tag tool-tag">${t}</span>`).join('')}
        </div>
        <div class="project-card-actions">
          <span style="font-size:12px;color:var(--color-text-secondary)">创建于 ${Utils.formatTime(p.createdAt)}</span>
        </div>
      </div>`).join('');

    // 绑定事件
    grid.querySelectorAll('[data-action="edit"]').forEach(btn => {
      btn.onclick = () => {
        const project = projects.find(p => p.id === btn.dataset.id);
        if (project) this._showForm(project);
      };
    });

    grid.querySelectorAll('[data-action="delete"]').forEach(btn => {
      btn.onclick = () => {
        const project = projects.find(p => p.id === btn.dataset.id);
        if (project) this._confirmDelete(project);
      };
    });
    } catch (e) {
      console.error('渲染项目列表失败:', e);
      grid.innerHTML = '<div class="empty-state"><p style="color:var(--color-danger)">渲染失败，请刷新页面</p></div>';
    }
  },

  _showForm(existing) {
    const isEdit = !!existing;
    const name = existing?.name || '';
    const desc = existing?.description || '';
    const gitUrl = existing?.gitRepoUrl || '';
    const gitPath = existing?.gitRepoLocalPath || '';

    const bodyHtml = Utils.html`
      <div class="form-group">
        <label class="form-label">项目名称 *</label>
        <input class="form-input" id="projFormName" value="${Utils.escapeHtml(name)}">
      </div>
      <div class="form-group">
        <label class="form-label">描述</label>
        <input class="form-input" id="projFormDesc" value="${Utils.escapeHtml(desc)}">
      </div>
      <div class="form-group">
        <label class="form-label">Git 远程地址</label>
        <input class="form-input" id="projFormGitUrl" value="${Utils.escapeHtml(gitUrl)}" placeholder="https://github.com/...（可选）">
      </div>
      <div class="form-group">
        <label class="form-label">Git 本地路径</label>
        <input class="form-input" id="projFormGitPath" value="${Utils.escapeHtml(gitPath)}" placeholder="E:/projects/my-app（默认当前目录）">
      </div>
      ${isEdit ? Utils.html`
        <div class="form-group">
          <label class="form-label">启用的工具</label>
          <div class="tool-checkbox-list" id="projFormTools"></div>
        </div>` : ''}`;

    Utils.showModal(
      isEdit ? `编辑项目：${name}` : '新建项目',
      bodyHtml,
      async () => {
        const data = {
          name: document.getElementById('projFormName').value.trim(),
          description: document.getElementById('projFormDesc').value.trim(),
          gitRepoUrl: document.getElementById('projFormGitUrl').value.trim(),
          gitRepoLocalPath: document.getElementById('projFormGitPath').value.trim() || undefined
        };
        if (!data.name) { Utils.notify('项目名称不能为空', 'error'); return; }

        try {
          const res = isEdit
            ? await Api.updateProject(existing.id, data)
            : await Api.createProject(data);
          if (res.success) {
            Utils.notify(isEdit ? '项目已更新' : '项目已创建', 'success');
            this._refresh();
          } else {
            Utils.notify(res.error, 'error');
          }
        } catch (e) { Utils.notify('保存失败: ' + e.message, 'error'); }
      },
      isEdit ? '保存' : '创建'
    );

    // 如果是编辑模式，填充工具复选框
    if (isEdit) {
      const existingTools = Utils.safeJsonArray(existing.enabledTools);
      const container = document.getElementById('projFormTools');
      if (container) {
        container.innerHTML = AppState.tools.map(t => Utils.html`
          <label class="tool-checkbox-item">
            <input type="checkbox" value="${t.name}" ${existingTools.includes(t.name) ? 'checked' : ''}>
            <span title="${t.description}">${t.name}</span>
          </label>`).join('');
        // 保存工具选择的函数
        container._getCheckedTools = () =>
          [...container.querySelectorAll('input:checked')].map(cb => cb.value);
      }
    }
  },

  _confirmDelete(project) {
    Utils.showModal(
      '删除项目',
      `<p>确定要删除项目 <strong>${Utils.escapeHtml(project.name)}</strong> 吗？</p>
       <p style="color:var(--color-text-secondary);font-size:13px">关联的日志源也将被删除，此操作不可撤销。</p>`,
      async () => {
        try {
          const res = await Api.deleteProject(project.id);
          if (res.success) {
            Utils.notify('项目已删除', 'success');
            this._refresh();
          } else {
            Utils.notify(res.error, 'error');
          }
        } catch (e) { Utils.notify('删除失败: ' + e.message, 'error'); }
      },
      '确认删除'
    );
  }
};
