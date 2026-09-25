// 后端接口统一封装。前端由 Java 后端托管时走相对路径；
// 若以 file:// 或其他端口打开前端，则直连本地 8080 端口（后端已开启 CORS）。
const API_BASE = location.port === '8080' ? '/api' : 'http://localhost:8080/api';

async function request(method, path, body) {
  const options = { method, headers: {} };
  if (body !== undefined) {
    options.headers['Content-Type'] = 'application/json';
    options.body = JSON.stringify(body);
  }
  let res;
  try {
    res = await fetch(API_BASE + path, options);
  } catch (e) {
    throw new Error('无法连接后端服务，请确认 script-backend 已在 8080 端口启动');
  }
  if (res.status === 204) {
    return null;
  }
  const data = await res.json();
  if (!res.ok) {
    throw new Error(data.error || ('请求失败 (' + res.status + ')'));
  }
  return data;
}

const api = {
  listScripts: () => request('GET', '/scripts'),
  getScript: (id) => request('GET', `/scripts/${id}`),
  createScript: (data) => request('POST', '/scripts', data),
  updateScript: (id, data) => request('PUT', `/scripts/${id}`, data),
  setStatus: (id, status) => request('PUT', `/scripts/${id}/status`, { status }),
  deleteScript: (id) => request('DELETE', `/scripts/${id}`),
  addScene: (id, data) => request('POST', `/scripts/${id}/scenes`, data),
  updateScene: (id, sceneId, data) => request('PUT', `/scripts/${id}/scenes/${sceneId}`, data),
  deleteScene: (id, sceneId) => request('DELETE', `/scripts/${id}/scenes/${sceneId}`),
  moveScene: (id, sceneId, direction) =>
    request('POST', `/scripts/${id}/scenes/${sceneId}/move`, { direction }),
  addLine: (id, sceneId, data) =>
    request('POST', `/scripts/${id}/scenes/${sceneId}/lines`, data),
  updateLine: (id, sceneId, lineId, data) =>
    request('PUT', `/scripts/${id}/scenes/${sceneId}/lines/${lineId}`, data),
  deleteLine: (id, sceneId, lineId) =>
    request('DELETE', `/scripts/${id}/scenes/${sceneId}/lines/${lineId}`),
    moveLine: (id, sceneId, lineId, direction) =>
    request('POST', `/scripts/${id}/scenes/${sceneId}/lines/${lineId}/move`, { direction }),
};

/* ---------- 通用展示工具 ---------- */

const STATUS_LABELS = { DRAFT: '草稿', REVISING: '修改中', FINAL: '已定稿' };

function statusBadge(status) {
  const label = STATUS_LABELS[status] || status;
  return `<span class="badge status-${status}">${escapeHtml(label)}</span>`;
}

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, (c) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
  }[c]));
}

function formatTime(ts) {
  if (!ts) return '-';
  const d = new Date(ts);
  const pad = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ` +
         `${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function toast(message, isError = false) {
  let el = document.getElementById('toast');
  if (!el) {
    el = document.createElement('div');
    el.id = 'toast';
    document.body.appendChild(el);
  }
  el.textContent = message;
  el.className = 'show' + (isError ? ' error' : '');
  clearTimeout(toast._timer);
  toast._timer = setTimeout(() => { el.className = ''; }, 2400);
}
