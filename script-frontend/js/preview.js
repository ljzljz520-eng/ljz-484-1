window.addEventListener('DOMContentLoaded', () => {
  const id = new URLSearchParams(location.search).get('id');
  document.getElementById('linkBack').href = `editor.html?id=${id}`;
  document.getElementById('btnPrint').addEventListener('click', () => window.print());
  if (id) {
    load(id);
  } else {
    window.location.href = 'index.html';
  }
});

async function load(id) {
  try {
    const s = await api.getScript(id);
    document.title = `预览《${s.title}》`;
    document.getElementById('pvTitle').textContent = s.title;
    document.getElementById('pvAuthor').textContent = '作者：' + (s.author || '佚名');
    document.getElementById('pvStatus').innerHTML = statusBadge(s.status);
    document.getElementById('pvSynopsis').textContent = s.synopsis || '';

    const box = document.getElementById('pvScenes');
    if (!s.scenes.length) {
      box.innerHTML = '<p class="empty">这个剧本还没有场次。</p>';
      return;
    }
    box.innerHTML = s.scenes.map((sc, i) => `
      <section class="pv-scene">
        <h2 class="pv-scene-heading">第 ${i + 1} 场 · ${escapeHtml(sc.heading)}</h2>
        ${sc.description ? `<p class="pv-scene-desc">${escapeHtml(sc.description)}</p>` : ''}
        ${sc.lines.map((ln) => `
          <div class="pv-dialogue">
            <p class="pv-char">${escapeHtml(ln.character)}</p>
            ${ln.note ? `<p class="pv-note">（${escapeHtml(ln.note)}）</p>` : ''}
            <p class="pv-text">${escapeHtml(ln.text)}</p>
          </div>
        `).join('')}
      </section>
    `).join('');
  } catch (err) {
    toast(err.message, true);
  }
}
