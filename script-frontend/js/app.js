/**
 * 单页应用：hash 路由 + 三个视图
 *   #/            剧本列表
 *   #/edit/{id}   编辑页（基本信息 + 场次 + 台词）
 *   #/preview/{id} 阅读预览
 */

const STATUS_LABELS = { DRAFT: '草稿', REVISING: '修改中', FINAL: '定稿' };

let listFilter = 'ALL';
let currentScript = null; // 编辑页当前剧本缓存（用于排序计算）

const appEl = document.getElementById('app');

window.addEventListener('hashchange', route);
window.addEventListener('load', route);

function route() {
  const hash = location.hash || '#/';
  const parts = hash.replace(/^#\//, '').split('/');
  if (parts[0] === 'edit' && parts[1]) {
    renderEdit(parts[1]);
  } else if (parts[0] === 'preview' && parts[1]) {
    renderPreview(parts[1]);
  } else {
    renderList();
  }
}

// ---------------- 视图一：剧本列表 ----------------

async function renderList() {
  appEl.innerHTML = '<p class="loading">加载中…</p>';
  try {
    const scripts = await api.listScripts();
    const filtered = listFilter === 'ALL' ? scripts : scripts.filter(s => s.status === listFilter);
    appEl.innerHTML = `
      <div class="page-head">
        <h2>我的剧本</h2>
        <button class="btn btn-primary" data-action="toggle-create">＋ 新建剧本</button>
      </div>
      <div id="create-form" class="card hidden">
        <h3>新建剧本</h3>
        <div class="form-grid">
          <label>标题<input id="new-title" placeholder="例如：深夜咖啡馆"></label>
          <label>作者<input id="new-author" placeholder="作者署名"></label>
          <label class="span-2">简介<textarea id="new-synopsis" rows="2" placeholder="一句话梗概"></textarea></label>
        </div>
        <div class="actions">
          <button class="btn btn-primary" data-action="create-script">创建</button>
          <button class="btn" data-action="toggle-create">取消</button>
        </div>
      </div>
      <div class="toolbar">
        <label>状态筛选
          <select id="status-filter">
            <option value="ALL">全部</option>
            <option value="DRAFT">草稿</option>
            <option value="REVISING">修改中</option>
            <option value="FINAL">定稿</option>
          </select>
        </label>
        <span class="muted">共 ${filtered.length} 部</span>
      </div>
      ${filtered.length === 0
        ? '<div class="empty">暂无剧本，点击「新建剧本」开始创作吧。</div>'
        : `<table class="table">
            <thead><tr><th>标题</th><th>作者</th><th>状态</th><th>场次</th><th>更新时间</th><th class="col-actions">操作</th></tr></thead>
            <tbody>
              ${filtered.map(s => `
                <tr>
                  <td class="cell-title">${escapeHtml(s.title)}</td>
                  <td>${escapeHtml(s.author)}</td>
                  <td>${statusBadge(s.status)}</td>
                  <td>${s.sceneCount}</td>
                  <td class="muted">${escapeHtml(s.updatedAt)}</td>
                  <td class="col-actions">
                    <a class="btn btn-sm" href="#/edit/${s.id}">编辑</a>
                    <a class="btn btn-sm" href="#/preview/${s.id}">预览</a>
                    <button class="btn btn-sm btn-danger" data-action="delete-script" data-id="${s.id}" data-title="${escapeHtml(s.title)}">删除</button>
                  </td>
                </tr>`).join('')}
            </tbody>
          </table>`}
    `;
    const filterEl = document.getElementById('status-filter');
    filterEl.value = listFilter;
    filterEl.addEventListener('change', () => {
      listFilter = filterEl.value;
      renderList();
    });
  } catch (err) {
    showError(err);
  }
}

// ---------------- 视图二：编辑页 ----------------

async function renderEdit(id) {
  appEl.innerHTML = '<p class="loading">加载中…</p>';
  try {
    const script = await api.getScript(id);
    currentScript = script;
    appEl.innerHTML = `
      <div class="page-head">
        <div>
          <a class="back-link" href="#/">← 返回列表</a>
          <h2>编辑：${escapeHtml(script.title)}</h2>
        </div>
        <a class="btn" href="#/preview/${script.id}">阅读预览</a>
      </div>

      <section class="card">
        <h3>基本信息</h3>
        <div class="form-grid">
          <label>标题<input id="meta-title" value="${escapeHtml(script.title)}"></label>
          <label>作者<input id="meta-author" value="${escapeHtml(script.author)}"></label>
          <label>状态
            <select id="meta-status">
              ${Object.entries(STATUS_LABELS).map(([v, l]) =>
                `<option value="${v}" ${script.status === v ? 'selected' : ''}>${l}</option>`).join('')}
            </select>
          </label>
          <label class="span-2">简介<textarea id="meta-synopsis" rows="2">${escapeHtml(script.synopsis)}</textarea></label>
        </div>
        <div class="actions">
          <button class="btn btn-primary" data-action="save-meta" data-id="${script.id}">保存基本信息</button>
          <span class="muted">更新于 ${escapeHtml(script.updatedAt)}</span>
        </div>
      </section>

      <div class="page-head"><h3 style="margin:0">场次（${script.scenes.length}）</h3></div>
      <div id="scenes">
        ${script.scenes.map((scene, idx) => sceneCard(script.id, scene, idx, script.scenes.length)).join('')}
      </div>

      <section class="card">
        <h3>添加场次</h3>
        <div class="form-grid">
          <label>场景标题<input id="scene-heading" placeholder="例如：内景·咖啡馆·夜"></label>
          <label>场景描述<input id="scene-description" placeholder="时间、氛围、调度说明"></label>
        </div>
        <div class="actions">
          <button class="btn btn-primary" data-action="add-scene" data-id="${script.id}">添加场次</button>
        </div>
      </section>
    `;
  } catch (err) {
    showError(err);
  }
}

function sceneCard(scriptId, scene, idx, total) {
  return `
    <section class="card scene-card" data-scene-id="${scene.id}">
      <div class="scene-head">
        <h3>第 ${idx + 1} 场</h3>
        <div class="scene-tools">
          <button class="btn btn-sm" data-action="scene-up" data-id="${scriptId}" data-scene="${scene.id}" ${idx === 0 ? 'disabled' : ''}>↑ 上移</button>
          <button class="btn btn-sm" data-action="scene-down" data-id="${scriptId}" data-scene="${scene.id}" ${idx === total - 1 ? 'disabled' : ''}>↓ 下移</button>
          <button class="btn btn-sm btn-danger" data-action="delete-scene" data-id="${scriptId}" data-scene="${scene.id}">删除场次</button>
        </div>
      </div>
      <div class="form-grid">
        <label>场景标题<input class="scene-heading" value="${escapeHtml(scene.heading)}"></label>
        <label>场景描述<input class="scene-description" value="${escapeHtml(scene.description)}"></label>
      </div>
      <div class="actions">
        <button class="btn btn-sm btn-primary" data-action="save-scene" data-id="${scriptId}" data-scene="${scene.id}">保存场次</button>
      </div>
      <table class="table lines-table">
        <thead><tr><th class="col-character">角色</th><th class="col-paren">动作/语气</th><th>台词</th><th class="col-actions">操作</th></tr></thead>
        <tbody>
          ${scene.lines.map((line, li) => `
            <tr data-line-id="${line.id}">
              <td><input class="line-character" value="${escapeHtml(line.character)}"></td>
              <td><input class="line-parenthetical" value="${escapeHtml(line.parenthetical || '')}"></td>
              <td><input class="line-text" value="${escapeHtml(line.text)}"></td>
              <td class="col-actions">
                <button class="btn btn-sm" data-action="save-line" data-id="${scriptId}" data-scene="${scene.id}" data-line="${line.id}">保存</button>
                <button class="btn btn-sm" data-action="line-up" data-id="${scriptId}" data-scene="${scene.id}" data-line="${line.id}" ${li === 0 ? 'disabled' : ''}>↑</button>
                <button class="btn btn-sm" data-action="line-down" data-id="${scriptId}" data-scene="${scene.id}" data-line="${line.id}" ${li === scene.lines.length - 1 ? 'disabled' : ''}>↓</button>
                <button class="btn btn-sm btn-danger" data-action="delete-line" data-id="${scriptId}" data-scene="${scene.id}" data-line="${line.id}">删除</button>
              </td>
            </tr>`).join('')}
          <tr class="new-line-row">
            <td><input class="line-character" placeholder="角色名"></td>
            <td><input class="line-parenthetical" placeholder="动作/语气（可选）"></td>
            <td><input class="line-text" placeholder="输入台词…"></td>
            <td class="col-actions">
              <button class="btn btn-sm btn-primary" data-action="add-line" data-id="${scriptId}" data-scene="${scene.id}">添加台词</button>
            </td>
          </tr>
        </tbody>
      </table>
    </section>`;
}

// ---------------- 视图三：阅读预览 ----------------

async function renderPreview(id) {
  appEl.innerHTML = '<p class="loading">加载中…</p>';
  try {
    const script = await api.getScript(id);
    appEl.innerHTML = `
      <div class="page-head">
        <div>
          <a class="back-link" href="#/">← 返回列表</a>
          <a class="back-link" href="#/edit/${script.id}">去编辑 →</a>
        </div>
      </div>
      <article class="preview">
        <header class="preview-header">
          <h1>${escapeHtml(script.title)}</h1>
          <p class="preview-meta">
            <span>作者：${escapeHtml(script.author || '佚名')}</span>
            ${statusBadge(script.status)}
            <span class="muted">更新于 ${escapeHtml(script.updatedAt)}</span>
          </p>
          ${script.synopsis ? `<p class="preview-synopsis">${escapeHtml(script.synopsis)}</p>` : ''}
        </header>
        ${script.scenes.map((scene, idx) => `
          <section class="preview-scene">
            <h3 class="preview-heading">第 ${idx + 1} 场 · ${escapeHtml(scene.heading)}</h3>
            ${scene.description ? `<p class="preview-desc">${escapeHtml(scene.description)}</p>` : ''}
            ${scene.lines.map(line => `
              <div class="dialogue">
                <div class="dialogue-character">${escapeHtml(line.character)}</div>
                ${line.parenthetical ? `<div class="dialogue-paren">（${escapeHtml(line.parenthetical)}）</div>` : ''}
                <p class="dialogue-text">${escapeHtml(line.text)}</p>
              </div>`).join('')}
          </section>`).join('')}
        ${script.scenes.length === 0 ? '<p class="empty">这部剧本还没有场次。</p>' : ''}
      </article>
    `;
  } catch (err) {
    showError(err);
  }
}

// ---------------- 事件委托 ----------------

appEl.addEventListener('click', async (e) => {
  const btn = e.target.closest('[data-action]');
  if (!btn) return;
  const action = btn.dataset.action;
  const id = btn.dataset.id;
  const sceneId = btn.dataset.scene;
  const lineId = btn.dataset.line;
  try {
    switch (action) {
      case 'toggle-create':
        document.getElementById('create-form').classList.toggle('hidden');
        break;

      case 'create-script': {
        const title = val('new-title').trim();
        if (!title) { toast('请填写标题'); return; }
        const created = await api.createScript({
          title,
          author: val('new-author').trim(),
          synopsis: val('new-synopsis').trim(),
        });
        location.hash = '#/edit/' + created.id;
        break;
      }

      case 'delete-script':
        if (confirm(`确定删除剧本《${btn.dataset.title}》？该操作不可恢复。`)) {
          await api.deleteScript(id);
          toast('已删除');
          renderList();
        }
        break;

      case 'save-meta':
        await api.updateScript(id, {
          title: val('meta-title').trim() || '未命名剧本',
          author: val('meta-author').trim(),
          synopsis: val('meta-synopsis').trim(),
          status: val('meta-status'),
        });
        toast('基本信息已保存');
        renderEdit(id);
        break;

      case 'add-scene':
        await api.addScene(id, {
          heading: val('scene-heading').trim() || '新场次',
          description: val('scene-description').trim(),
        });
        renderEdit(id);
        break;

      case 'save-scene': {
        const card = btn.closest('.scene-card');
        await api.updateScene(id, sceneId, {
          heading: card.querySelector('.scene-heading').value.trim(),
          description: card.querySelector('.scene-description').value.trim(),
        });
        toast('场次已保存');
        renderEdit(id);
        break;
      }

      case 'delete-scene':
        if (confirm('确定删除该场次及其全部台词？')) {
          await api.deleteScene(id, sceneId);
          renderEdit(id);
        }
        break;

      case 'scene-up':
      case 'scene-down': {
        const ids = currentScript.scenes.map(s => s.id);
        const i = ids.indexOf(sceneId);
        const j = action === 'scene-up' ? i - 1 : i + 1;
        [ids[i], ids[j]] = [ids[j], ids[i]];
        await api.reorderScenes(id, ids);
        renderEdit(id);
        break;
      }

      case 'add-line': {
        const row = btn.closest('tr');
        const character = row.querySelector('.line-character').value.trim();
        const text = row.querySelector('.line-text').value.trim();
        if (!character || !text) { toast('请填写角色名和台词'); return; }
        await api.addLine(id, sceneId, {
          character,
          parenthetical: row.querySelector('.line-parenthetical').value.trim(),
          text,
        });
        renderEdit(id);
        break;
      }

      case 'save-line': {
        const row = btn.closest('tr');
        await api.updateLine(id, sceneId, lineId, {
          character: row.querySelector('.line-character').value.trim(),
          parenthetical: row.querySelector('.line-parenthetical').value.trim(),
          text: row.querySelector('.line-text').value.trim(),
        });
        toast('台词已保存');
        break;
      }

      case 'delete-line':
        await api.deleteLine(id, sceneId, lineId);
        renderEdit(id);
        break;

      case 'line-up':
      case 'line-down': {
        const scene = currentScript.scenes.find(s => s.id === sceneId);
        const ids = scene.lines.map(l => l.id);
        const i = ids.indexOf(lineId);
        const j = action === 'line-up' ? i - 1 : i + 1;
        [ids[i], ids[j]] = [ids[j], ids[i]];
        await api.reorderLines(id, sceneId, ids);
        renderEdit(id);
        break;
      }
    }
  } catch (err) {
    alert(err.message);
  }
});

// ---------------- 工具函数 ----------------

function val(id) {
  const el = document.getElementById(id);
  return el ? el.value : '';
}

function statusBadge(status) {
  const label = STATUS_LABELS[status] || status || '';
  return `<span class="badge badge-${String(status || '').toLowerCase()}">${escapeHtml(label)}</span>`;
}

function escapeHtml(s) {
  return String(s == null ? '' : s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function toast(msg) {
  const el = document.createElement('div');
  el.className = 'toast';
  el.textContent = msg;
  document.body.appendChild(el);
  requestAnimationFrame(() => el.classList.add('show'));
  setTimeout(() => {
    el.classList.remove('show');
    setTimeout(() => el.remove(), 300);
  }, 1800);
}

function showError(err) {
  appEl.innerHTML = `
    <div class="empty error">
      <p>😕 ${escapeHtml(err.message)}</p>
      <p class="muted">请确认后端已启动（默认 http://localhost:8080）。</p>
      <button class="btn" onclick="route()">重试</button>
    </div>`;
}
