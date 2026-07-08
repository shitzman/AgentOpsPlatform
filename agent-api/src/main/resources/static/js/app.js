/* ================================================================
   app.js — 应用入口：全局状态、事件总线、选项卡管理
   V1.0: 新增诊断历史选项卡
   ================================================================ */

const AppState = {
  currentTab: 'diagnosis',
  selectedProjectId: null,
  projects: [],
  tools: []
};

const EventBus = {
  _listeners: {},

  on(event, callback) {
    if (!this._listeners[event]) this._listeners[event] = [];
    this._listeners[event].push(callback);
    return () => { this._listeners[event] = this._listeners[event].filter(cb => cb !== callback); };
  },

  emit(event, data) {
    (this._listeners[event] || []).forEach(cb => { try { cb(data); } catch (e) { console.error(e); } });
  }
};

const TabManager = {
  switch(tabName) {
    AppState.currentTab = tabName;
    document.querySelectorAll('.tab-btn').forEach(b => b.classList.toggle('active', b.dataset.tab === tabName));
    document.querySelectorAll('.tab-panel').forEach(p => p.classList.toggle('active', p.id === `panel-${tabName}`));
    EventBus.emit('tab-switched', { tabName });
  }
};

const App = {
  async init() {
    // 选项卡点击
    document.querySelectorAll('.tab-btn').forEach(btn => {
      btn.addEventListener('click', () => TabManager.switch(btn.dataset.tab));
    });

    // 初始化组件（异常隔离：单个组件失败不影响其他组件和后续数据加载）
    try { DiagnosisTab.init(document.getElementById('panel-diagnosis')); } catch (e) { console.error('DiagnosisTab 初始化失败:', e); }
    try { ProjectsTab.init(document.getElementById('panel-projects')); } catch (e) { console.error('ProjectsTab 初始化失败:', e); }
    try { LogSourcesTab.init(document.getElementById('panel-logsources')); } catch (e) { console.error('LogSourcesTab 初始化失败:', e); }
    try { HistoryTab.init(document.getElementById('panel-history')); } catch (e) { console.error('HistoryTab 初始化失败:', e); }

    // 全局状态同步
    EventBus.on('projects-changed', ({ projects }) => {
      AppState.projects = projects;
    });

    EventBus.on('project-selected', ({ projectId }) => {
      AppState.selectedProjectId = projectId;
    });

    // 加载全局数据
    try {
      const toolRes = await Api.getAvailableTools();
      if (toolRes.success) AppState.tools = toolRes.tools;
    } catch (e) { console.error('加载工具列表失败:', e); }

    try {
      const projRes = await Api.getProjects();
      if (projRes.success) {
        AppState.projects = projRes.projects;
        EventBus.emit('projects-changed', { projects: projRes.projects });
      } else {
        console.error('加载项目列表失败:', projRes.error);
        Utils.notify('加载项目列表失败: ' + (projRes.error || '未知错误'), 'error');
      }
    } catch (e) { console.error('加载项目列表异常:', e); Utils.notify('加载项目列表失败: ' + e.message, 'error'); }

    // 健康检查
    this.checkHealth();
    setInterval(() => this.checkHealth(), 30000);
  },

  async checkHealth() {
    const dot = document.getElementById('healthDot');
    const text = document.getElementById('healthText');
    const info = document.getElementById('healthInfo');
    try {
      const data = await Api.health();
      dot.className = 'health-dot online';
      text.textContent = '在线';
      info.textContent = `${data.version || ''} | ${data.prompts || ''} | ${data.tools || ''}`;
      if (data.version) document.getElementById('headerVersion').textContent = data.version;
    } catch {
      dot.className = 'health-dot offline';
      text.textContent = '离线';
      info.textContent = '无法连接服务';
    }
  }
};
