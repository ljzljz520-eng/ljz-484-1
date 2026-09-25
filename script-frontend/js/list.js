let allScripts = [];
let currentFilter = '';

window.addEventListener('DOMContentLoaded', () => {
  document.getElementById('btnNew').addEventListener('click', () => toggleModal(true));
  document.getElementById('btnCancelNew').addEventListener('click', () => toggleModal(false));
  document.getElementById('newModal').addEventListener('click', (e) => {
    if (e.target.id === 'newModal') toggleModal(false);
  });
  document.getElementById('btnCreateNew').addEventListener('click', createScript);
  document.getElementById('newTitle').addEventListener('keydown', (e) => {
    if (e.key === 'Enter') createScript();
  });

  document.getElementById('statusFilters').addEventListener('click', (e) => {
    const chip = e.target.closest('.chip');
    if (!chip) return;
    document.querySelectorAll('.chip').forEach((c) => c.classList.remove('active'));
    chip.classList.add('active');
    currentFilter = chip.dataset.status;
    render();
  });

  document.getElementById('scriptList').addEventListener('click', async (e) => {
    const btn = e.target.closest('[data-del]');
    if (!btn) return;
    if (!confirm('确定删除该剧本？此操作不可恢复。')) return;
    try {
      await api.deleteScript(btn.dataset.del);
      toast('已删除');
      await load();
    } catch (err) {
      toast(err.message, true);
    }
  });

  load();
});

async function load() {
  try {
    allScripts = await api.listScripts();
    render();
  } catch (err) {
    toast(err.message, true);
  }
}

function toggleModal(show) {
  const modal = document.getElementById('newModal');
  modal.classList.toggle('hidden', !show);
  if (show) {
    document.getElementById('newTitle').value = '';
    document.getElementById('newAuthor').value = '';
    document.getElementById('newSynopsis').value = '';
    document.getElementById('newTitle').focus();
  }
}

async function createScript() {
  const title = document.getElementById('newTitle').value.trim();
  if (!title) {
    toast('请填写剧名', true);
    return;
  }
  try {
    const created = await api.createScript({
      title,
      author: document.getElementById('newAuthor').value.trim(),
      synopsis: document.getElementById('newSynopsis').value.trim(),
    });
    window.location.href = `editor.html?id=${created.id}`;
  } catch (err) {
    toast(err.message, true);
  }
}

function render() {
  const listEl = document.getElementById('scriptList');
  const items = allScripts.filter((s) => !currentFilter || s.status === currentFilter);
  document.getElementById('emptyTip').classList.toggle('hidden', items.length > 0);

  listEl.innerHTML = items.map((s) => `
    <div class="script-card">
      <div class="script-card-main">
        <div class="script-title-row">
          <h3>${escapeHtml(s.title)}</h3>
          ${statusBadge(s.status)}
        </div>
        <p class="script-synopsis">${escapeHtml(s.synopsis || '（暂无简介）')}</p>
        <p class="script-meta">
          作者：${escapeHtml(s.author || '佚名')} · ${s.sceneCount} 场 ·
          ${s.lineCount} 句台词 · 更新于 ${formatTime(s.updatedAt)}
        </p>
      </div>
      <div class="script-actions">
        <a class="btn small" href="editor.html?id=${s.id}">✏️ 编辑</a>
        <a class="btn small" href="preview.html?id=${s.id}">📖 预览</a>
        <button class="btn small danger" data-del="${s.id}">🗑 删除</button>
      </div>
    </div>
  `).join('');
}
