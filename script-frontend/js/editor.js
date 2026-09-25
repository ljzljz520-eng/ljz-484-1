const scriptId = new URLSearchParams(location.search).get('id');
let script = null;

window.addEventListener('DOMContentLoaded', () => {
  if (!scriptId) {
    alert('缺少剧本 ID');
    window.location.href = 'index.html';
    return;
  }
  document.getElementById('btnSaveMeta').addEventListener('click', saveMeta);
  document.getElementById('btnAddScene').addEventListener('click', addScene);
  document.getElementById('sceneList').addEventListener('click', onSceneListClick);
  load();
});

async function load() {
  try {
    script = await api.getScript(scriptId);
    document.getElementById('linkPreview').href = `preview.html?id=${scriptId}`;
    renderMeta();
    renderScenes();
  } catch (err) {
    alert('加载剧本失败：' + err.message);
    window.location.href = 'index.html';
  }
}

function renderMeta() {
  document.getElementById('metaTitle').value = script.title;
  document.getElementById('metaAuthor').value = script.author;
  document.getElementById('metaSynopsis').value = script.synopsis;
  document.getElementById('metaStatus').value = script.status;
  document.title = `编辑《${script.title}》`;
}

async function saveMeta() {
  try {
    await api.updateScript(scriptId, {
      title: document.getElementById('metaTitle').value.trim(),
      author: document.getElementById('metaAuthor').value.trim(),
      synopsis: document.getElementById('metaSynopsis').value.trim(),
      status: document.getElementById('metaStatus').value,
    });
    toast('剧本信息已保存');
    await load();
  } catch (err) {
    toast(err.message, true);
  }
}

async function addScene() {
  try {
    await api.addScene(scriptId, { heading: '新场景', description: '' });
    await load();
  } catch (err) {
    toast(err.message, true);
  }
}

async function onSceneListClick(e) {
  const btn = e.target.closest('[data-act]');
  if (!btn) return;
  const sceneCard = btn.closest('.scene-card');
  const sceneId = sceneCard.dataset.scene;
  const act = btn.dataset.act;

  try {
    switch (act) {
      case 'scene-save':
        await api.updateScene(scriptId, sceneId, {
          heading: sceneCard.querySelector('.scene-heading').value.trim(),
          description: sceneCard.querySelector('.scene-desc').value.trim(),
        });
        toast('场次已保存');
        break;

      case 'scene-del':
        if (!confirm('确定删除该场次及其全部台词？')) return;
        await api.deleteScene(scriptId, sceneId);
        break;

      case 'scene-up':
        await api.moveScene(scriptId, sceneId, 'up');
        break;

      case 'scene-down':
        await api.moveScene(scriptId, sceneId, 'down');
        break;

      case 'line-add': {
        const character = sceneCard.querySelector('.new-char').value.trim();
        const text = sceneCard.querySelector('.new-text').value.trim();
        const note = sceneCard.querySelector('.new-note').value.trim();
        if (!character || !text) {
          toast('请填写角色名和台词内容', true);
          return;
        }
        await api.addLine(scriptId, sceneId, { character, text, note });
        break;
      }

      case 'line-save':
      case 'line-del':
      case 'line-up':
      case 'line-down': {
        const row = btn.closest('.line-row');
        const lineId = row.dataset.line;
        if (act === 'line-save') {
          await api.updateLine(scriptId, sceneId, lineId, {
            character: row.querySelector('.line-char').value.trim(),
            text: row.querySelector('.line-text').value.trim(),
            note: row.querySelector('.line-note').value.trim(),
          });
          toast('台词已保存');
        } else if (act === 'line-del') {
          await api.deleteLine(scriptId, sceneId, lineId);
        } else if (act === 'line-up') {
          await api.moveLine(scriptId, sceneId, lineId, 'up');
        } else {
          await api.moveLine(scriptId, sceneId, lineId, 'down');
        }
        break;
      }

      default:
        return;
    }
    await load();
  } catch (err) {
    toast(err.message, true);
  }
}

function renderScenes() {
  const box = document.getElementById('sceneList');
  if (!script.scenes.length) {
    box.innerHTML = '<p class="empty">还没有场次，点击「添加场次」开始拆分故事结构。</p>';
    return;
  }
  box.innerHTML = script.scenes.map((sc, i) => `
    <div class="scene-card" data-scene="${sc.id}">
      <div class="scene-head">
        <span class="scene-no">第 ${i + 1} 场</span>
        <input class="scene-heading" value="${escapeHtml(sc.heading)}"
               placeholder="场景标题，如：内景 · 书店 · 夜">
        <span class="scene-ops">
          <button class="icon" data-act="scene-up" title="上移">↑</button>
          <button class="icon" data-act="scene-down" title="下移">↓</button>
          <button class="btn small" data-act="scene-save">保存场次</button>
          <button class="btn small danger" data-act="scene-del">删除</button>
        </span>
      </div>
      <textarea class="scene-desc" rows="2"
                placeholder="场景描述 / 舞台提示">${escapeHtml(sc.description)}</textarea>

      <div class="lines">
        ${sc.lines.map((ln, j) => `
          <div class="line-row" data-line="${ln.id}">
            <span class="line-no">${j + 1}.</span>
            <input class="line-char" value="${escapeHtml(ln.character)}" placeholder="角色">
            <input class="line-text" value="${escapeHtml(ln.text)}" placeholder="台词内容">
            <input class="line-note" value="${escapeHtml(ln.note)}" placeholder="备注（语气/动作）">
            <span class="scene-ops">
              <button class="icon" data-act="line-save" title="保存">✓</button>
              <button class="icon" data-act="line-up" title="上移">↑</button>
              <button class="icon" data-act="line-down" title="下移">↓</button>
              <button class="icon danger" data-act="line-del" title="删除">✕</button>
            </span>
          </div>
        `).join('')}
      </div>

      <div class="add-line">
        <input class="new-char" placeholder="角色">
        <input class="new-text" placeholder="台词内容">
        <input class="new-note" placeholder="备注（可选）">
        <button class="btn small" data-act="line-add">＋ 添加台词</button>
      </div>
    </div>
  `).join('');
}
