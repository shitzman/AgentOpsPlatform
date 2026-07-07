/* ================================================================
   utils.js — DOM 工具函数
   ================================================================ */

const Utils = {

  /** 简易模板字面量（避免 XSS — 不做 HTML 转义，调用方需自行确保安全） */
  html(strings, ...values) {
    return strings.reduce((r, s, i) => r + s + (values[i] ?? ''), '');
  },

  /** 显示通知条 */
  notify(message, type = 'info') {
    const el = document.getElementById('notification');
    el.textContent = message;
    el.className = `notification ${type}`;
    el.style.display = 'block';
    clearTimeout(el._timeout);
    el._timeout = setTimeout(() => { el.style.display = 'none'; }, 4000);
  },

  /** 显示 Modal */
  showModal(title, bodyHtml, onConfirm, confirmText = '确定') {
    document.getElementById('modalTitle').textContent = title;
    document.getElementById('modalBody').innerHTML = bodyHtml;
    document.getElementById('modalFooter').innerHTML = `
      <button class="btn btn-outline" id="modalCancel">取消</button>
      <button class="btn btn-primary" id="modalConfirm">${confirmText}</button>`;
    document.getElementById('modalOverlay').style.display = 'flex';

    document.getElementById('modalClose').onclick = () => this.closeModal();
    document.getElementById('modalCancel').onclick = () => this.closeModal();
    document.getElementById('modalConfirm').onclick = () => {
      if (onConfirm) onConfirm();
      this.closeModal();
    };
  },

  /** 关闭 Modal */
  closeModal() {
    document.getElementById('modalOverlay').style.display = 'none';
  },

  /** 创建 badge 元素 */
  badge(text, type) {
    const el = document.createElement('span');
    el.className = `badge badge-${type}`;
    el.textContent = text;
    return el;
  },

  /** 创建 tool tag */
  toolTag(name, enabled = true) {
    const el = document.createElement('span');
    el.className = `tag tool-tag${enabled ? '' : ' disabled'}`;
    el.textContent = name;
    return el;
  },

  /** 转义 HTML */
  escapeHtml(str) {
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
  },

  /** 格式化 ISO 时间为可读字符串 */
  formatTime(iso) {
    if (!iso) return '';
    try { return new Date(iso).toLocaleString('zh-CN'); } catch { return iso; }
  },

  /** 格式化严重级别为中文 */
  severityLabel(severity) {
    const labels = { critical: '严重', high: '高', medium: '中', low: '低' };
    return labels[severity] || severity;
  },

  /** 格式化紧急程度为中文 */
  urgencyLabel(urgency) {
    const labels = { '立即修复': '🔴 立即修复', '计划修复': '🟡 计划修复', '低优先级': '🟢 低优先级' };
    return labels[urgency] || urgency;
  },

  /** 日志源类型中文名 */
  logSourceTypeLabel(type) {
    const labels = { TEXT_INPUT: '文本输入', FILE_PATH: '文件路径', ELASTICSEARCH: 'Elasticsearch' };
    return labels[type] || type;
  }
};
