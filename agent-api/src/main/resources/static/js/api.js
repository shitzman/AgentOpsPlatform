/* ================================================================
   api.js — HTTP API 客户端（所有 fetch 请求的单一出口）
   ================================================================ */

const Api = {

  async _fetch(method, url, body) {
    const opts = { method, headers: { 'Content-Type': 'application/json' } };
    if (body) opts.body = JSON.stringify(body);
    const res = await fetch(url, opts);
    if (!res.ok) throw new Error(`HTTP ${res.status}: ${res.statusText}`);
    return res.json();
  },

  // ---- 健康检查 ----
  async health() {
    return this._fetch('GET', '/api/health');
  },

  // ---- 项目 CRUD ----
  async getProjects() {
    return this._fetch('GET', '/api/projects');
  },
  async createProject(data) {
    return this._fetch('POST', '/api/projects', data);
  },
  async getProject(id) {
    return this._fetch('GET', `/api/projects/${id}`);
  },
  async updateProject(id, data) {
    return this._fetch('PUT', `/api/projects/${id}`, data);
  },
  async deleteProject(id) {
    return this._fetch('DELETE', `/api/projects/${id}`);
  },

  // ---- 工具管理 ----
  async getAvailableTools() {
    return this._fetch('GET', '/api/tools');
  },
  async setProjectTools(projectId, toolNames) {
    return this._fetch('PUT', `/api/projects/${projectId}/tools`, { toolNames });
  },

  // ---- 日志源 CRUD ----
  async getLogSources(projectId) {
    return this._fetch('GET', `/api/projects/${projectId}/logsources`);
  },
  async addLogSource(projectId, data) {
    return this._fetch('POST', `/api/projects/${projectId}/logsources`, data);
  },
  async updateLogSource(projectId, lsId, data) {
    return this._fetch('PUT', `/api/projects/${projectId}/logsources/${lsId}`, data);
  },
  async deleteLogSource(projectId, lsId) {
    return this._fetch('DELETE', `/api/projects/${projectId}/logsources/${lsId}`);
  },

  // ---- 诊断 ----
  async diagnose(data) {
    return this._fetch('POST', '/api/diagnosis', data);
  },
  async chat(data) {
    return this._fetch('POST', '/api/chat', data);
  }
};
