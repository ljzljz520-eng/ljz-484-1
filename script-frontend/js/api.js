/**
 * 后端接口封装。若后端端口不是 8080，修改下面的 API_BASE 即可。
 */
const API_BASE = 'http://localhost:8080/api';

async function request(method, path, body) {
  const res = await fetch(API_BASE + path, {
    method,
    headers: body ? { 'Content-Type': 'application/json' } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!res.ok) {
    let msg = '请求失败 (' + res.status + ')';
    try {
      const data = await res.json();
      if (data && data.error) msg = data.error;
    } catch (e) { /* 忽略非 JSON 错误响应 */ }
    throw new Error(msg);
  }
  if (res.status === 204) return null;
  return res.json();
}

const api = {
  // 剧本
  listScripts: () => request('GET', '/scripts'),
  createScript: (data) => request('POST', '/scripts', data),
  getScript: (id) => request('GET', '/scripts/' + id),
  updateScript: (id, data) => request('PUT', '/scripts/' + id, data),
  deleteScript: (id) => request('DELETE', '/scripts/' + id),
  // 场次
  addScene: (id, data) => request('POST', `/scripts/${id}/scenes`, data),
  updateScene: (id, sceneId, data) => request('PUT', `/scripts/${id}/scenes/${sceneId}`, data),
  deleteScene: (id, sceneId) => request('DELETE', `/scripts/${id}/scenes/${sceneId}`),
  reorderScenes: (id, sceneIds) => request('POST', `/scripts/${id}/scenes/reorder`, { sceneIds }),
  // 台词
  addLine: (id, sceneId, data) => request('POST', `/scripts/${id}/scenes/${sceneId}/lines`, data),
  updateLine: (id, sceneId, lineId, data) => request('PUT', `/scripts/${id}/scenes/${sceneId}/lines/${lineId}`, data),
  deleteLine: (id, sceneId, lineId) => request('DELETE', `/scripts/${id}/scenes/${sceneId}/lines/${lineId}`),
  reorderLines: (id, sceneId, lineIds) => request('POST', `/scripts/${id}/scenes/${sceneId}/lines/reorder`, { lineIds }),
};
