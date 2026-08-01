// GitBook-style documentation for RelishAuth (Font Awesome icons, no emoji)

const PAGES = [
  { file: 'README.md', title: 'Home', section: null, icon: 'fa-solid fa-house' },
  { file: 'QuickStart.md', title: 'Quick Start', section: null, icon: 'fa-solid fa-bolt' },
  { file: 'Installation.md', title: 'Installation', section: null, icon: 'fa-solid fa-download' },

  { file: 'Configuration.md', title: 'Configuration', section: 'Configuration', icon: 'fa-solid fa-sliders' },
  { file: 'AuthMethods.md', title: 'Auth Methods', section: 'Configuration', icon: 'fa-solid fa-key' },
  { file: 'Security.md', title: 'Security', section: 'Configuration', icon: 'fa-solid fa-shield-halved' },
  { file: 'Database.md', title: 'Database', section: 'Configuration', icon: 'fa-solid fa-database' },

  { file: 'DiscordBot.md', title: 'Discord Bot', section: 'Features', icon: 'fa-brands fa-discord' },
  { file: 'Commands.md', title: 'Commands', section: 'Features', icon: 'fa-solid fa-terminal' },
  { file: 'Permissions.md', title: 'Permissions', section: 'Features', icon: 'fa-solid fa-lock' },

  { file: 'Troubleshooting.md', title: 'Troubleshooting', section: 'Reference', icon: 'fa-solid fa-wrench' },
  { file: 'CHANGELOG.md', title: 'Changelog', section: 'Reference', icon: 'fa-solid fa-clock-rotate-left' },
];

const el = (sel) => document.querySelector(sel);
const nav = el('#sidebar-nav');
const doc = el('#doc');
const toc = el('#toc');
const search = el('#search');
const searchResults = el('#search-results');
const lastUpdated = el('#last-updated');
const themeToggle = el('#theme-toggle');
const sidebarToggle = el('#sidebar-toggle');
const sidebar = el('#sidebar');
const searchIndex = new Map();
const searchPreviewIndex = new Map();
let searchIndexPromise = null;
let searchActiveIndex = -1;

function initTheme() {
  const savedTheme = localStorage.getItem('theme') || 'dark';
  document.documentElement.setAttribute('data-theme', savedTheme);
}

function toggleTheme() {
  const currentTheme = document.documentElement.getAttribute('data-theme');
  const newTheme = currentTheme === 'dark' ? 'light' : 'dark';
  document.documentElement.setAttribute('data-theme', newTheme);
  localStorage.setItem('theme', newTheme);
}

function toggleSidebar() {
  sidebar.classList.toggle('open');

  let overlay = el('.sidebar-overlay');
  if (!overlay) {
    overlay = document.createElement('div');
    overlay.className = 'sidebar-overlay';
    overlay.addEventListener('click', closeSidebar);
    document.body.appendChild(overlay);
  }
  overlay.classList.toggle('active');
}

function closeSidebar() {
  sidebar.classList.remove('open');
  const overlay = el('.sidebar-overlay');
  if (overlay) overlay.classList.remove('active');
}

function normalizeHash(hash) {
  const file = decodeURIComponent((hash || '').replace(/^#\/?/, ''));
  if (!file) return 'README.md';
  const entry = PAGES.find(p => p.file.toLowerCase() === file.toLowerCase());
  return entry ? entry.file : 'README.md';
}

function stripMarkdown(md, lower = true) {
  let text = md
    .replace(/```[\s\S]*?```/g, ' ')
    .replace(/`[^`]*`/g, ' ')
    .replace(/!\[[^\]]*]\([^)]+\)/g, ' ')
    .replace(/\[[^\]]+\]\([^)]+\)/g, ' ')
    .replace(/^#{1,6}\s+/gm, '')
    .replace(/[>*_~\-\[\]\(\)]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();

  if (lower) {
    text = text.toLowerCase();
  }

  return text;
}

async function buildSearchIndex() {
  if (searchIndexPromise) {
    return searchIndexPromise;
  }

  searchIndexPromise = Promise.all(
    PAGES.map(async (page) => {
      try {
        const res = await fetch(page.file + `?search=${Date.now()}`);
        if (!res.ok) {
          searchIndex.set(page.file, '');
          searchPreviewIndex.set(page.file, '');
          return;
        }
        const md = await res.text();
        searchIndex.set(page.file, stripMarkdown(md, true));
        searchPreviewIndex.set(page.file, stripMarkdown(md, false));
      } catch {
        searchIndex.set(page.file, '');
        searchPreviewIndex.set(page.file, '');
      }
    })
  );

  return searchIndexPromise;
}

function escapeHtml(text) {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function matchesSearchQuery(page, query) {
  const q = query.trim().toLowerCase();
  if (!q) {
    return true;
  }

  const tokens = q.split(/\s+/).filter(Boolean);
  const content = searchIndex.get(page.file) || '';
  const title = page.title.toLowerCase();

  return tokens.every((token) => content.includes(token) || title.includes(token));
}

function extractSearchSnippet(file, query) {
  const preview = searchPreviewIndex.get(file) || '';
  if (!preview) {
    return '';
  }

  const lowerPreview = preview.toLowerCase();
  const tokens = query.trim().toLowerCase().split(/\s+/).filter(Boolean);
  const firstToken = tokens.find((token) => lowerPreview.includes(token));
  if (!firstToken) {
    return '';
  }

  const index = lowerPreview.indexOf(firstToken);
  const start = Math.max(0, index - 45);
  const end = Math.min(preview.length, index + 110);
  const prefix = start > 0 ? '... ' : '';
  const suffix = end < preview.length ? ' ...' : '';

  return `${prefix}${preview.slice(start, end).trim()}${suffix}`;
}

function iconHtml(iconClass) {
  if (!iconClass) {
    return '<i class="fa-solid fa-file-lines" aria-hidden="true"></i>';
  }
  return `<i class="${escapeHtml(iconClass)}" aria-hidden="true"></i>`;
}

function highlightSnippet(snippet, query) {
  let out = escapeHtml(snippet);
  const tokens = query.trim().split(/\s+/).filter(Boolean);
  tokens.forEach((token) => {
    const escaped = token.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    out = out.replace(new RegExp(`(${escaped})`, 'gi'), '<mark>$1</mark>');
  });
  return out;
}

function hideSearchResults() {
  if (!searchResults) return;
  searchResults.hidden = true;
  searchResults.innerHTML = '';
  searchActiveIndex = -1;
}

function setSearchActive(index) {
  const items = Array.from(searchResults.querySelectorAll('.search-result'));
  if (!items.length) {
    searchActiveIndex = -1;
    return;
  }

  searchActiveIndex = ((index % items.length) + items.length) % items.length;
  items.forEach((item, i) => {
    item.classList.toggle('active', i === searchActiveIndex);
  });
  items[searchActiveIndex].scrollIntoView({ block: 'nearest' });
}

function openSearchResult(file) {
  hideSearchResults();
  search.value = '';
  location.hash = `#/${encodeURIComponent(file)}`;
}

function renderSearchResults(query) {
  const q = query.trim();
  if (!q) {
    hideSearchResults();
    return;
  }

  const matches = PAGES
    .filter((p) => matchesSearchQuery(p, q))
    .map((p) => {
      const titleHit = p.title.toLowerCase().includes(q.toLowerCase());
      return {
        page: p,
        snippet: extractSearchSnippet(p.file, q),
        score: titleHit ? 0 : 1,
      };
    })
    .sort((a, b) => a.score - b.score || a.page.title.localeCompare(b.page.title));

  if (!matches.length) {
    searchResults.innerHTML = `<div class="search-empty">No results for <code>${escapeHtml(q)}</code></div>`;
    searchResults.hidden = false;
    searchActiveIndex = -1;
    return;
  }

  searchResults.innerHTML = matches.map(({ page, snippet }, i) => `
    <a class="search-result${i === 0 ? ' active' : ''}" href="#/${encodeURIComponent(page.file)}" role="option" data-file="${escapeHtml(page.file)}" data-index="${i}">
      <span class="search-result-icon">${iconHtml(page.icon)}</span>
      <span class="search-result-body">
        <span class="search-result-title">${escapeHtml(page.title)}</span>
        ${page.section ? `<span class="search-result-meta">${escapeHtml(page.section)}</span>` : ''}
        ${snippet ? `<span class="search-result-snippet">${highlightSnippet(snippet, q)}</span>` : ''}
      </span>
    </a>
  `).join('');

  searchResults.hidden = false;
  searchActiveIndex = 0;

  searchResults.querySelectorAll('.search-result').forEach((link) => {
    link.addEventListener('click', (e) => {
      e.preventDefault();
      openSearchResult(link.dataset.file);
    });
  });
}

function buildSidebar() {
  let html = '';
  let currentSection = null;

  PAGES.forEach(p => {
    if (p.section !== currentSection) {
      if (p.section) {
        html += `<div class="nav-section">${p.section}</div>`;
      }
      currentSection = p.section;
    }

    html += `<a href="#/${encodeURIComponent(p.file)}" data-file="${p.file}"><span class="nav-icon">${iconHtml(p.icon)}</span><span class="nav-text"><span class="nav-label">${p.title}</span></span></a>`;
  });

  nav.innerHTML = html;

  const currentFile = normalizeHash(location.hash);
  const activeLink = nav.querySelector(`a[data-file="${currentFile}"]`);
  if (activeLink) {
    activeLink.classList.add('active');
  }

  nav.querySelectorAll('a').forEach(link => {
    link.addEventListener('click', () => {
      if (window.innerWidth <= 1000) {
        closeSidebar();
      }
    });
  });
}

function slugify(text) {
  return text
    .toLowerCase()
    .replace(/[^a-z0-9\s-]/g, '')
    .trim()
    .replace(/\s+/g, '-')
    .replace(/-+/g, '-');
}

function enhanceHeadings(container) {
  const hs = container.querySelectorAll('h2, h3');
  hs.forEach(h => {
    if (!h.id) h.id = slugify(h.textContent);
    const a = document.createElement('a');
    a.href = `#${h.id}`;
    a.className = 'anchor';
    a.textContent = '#';
    h.prepend(a);
  });
  return Array.from(hs);
}

function buildTOC(headings) {
  if (!headings.length) {
    toc.innerHTML = '';
    return;
  }

  const links = headings.map(h => {
    const lvl = h.tagName === 'H2' ? 2 : 3;
    return `<a class="lvl-${lvl}" href="#${h.id}" data-anchor="${h.id}">${h.textContent.replace('#', '')}</a>`;
  }).join('');

  toc.innerHTML = `<h4>On this page</h4>${links}`;

  toc.querySelectorAll('a').forEach(link => {
    link.addEventListener('click', (e) => {
      e.preventDefault();
      const targetId = link.getAttribute('data-anchor');
      const targetElement = document.getElementById(targetId);
      if (targetElement) {
        targetElement.scrollIntoView({ behavior: 'smooth', block: 'start' });
        history.pushState(null, '', `${location.pathname}${location.hash.split('#')[0]}#${targetId}`);
      }
    });
  });

  const obs = new IntersectionObserver(entries => {
    entries.forEach(entry => {
      if (entry.isIntersecting) {
        const id = entry.target.id;
        toc.querySelectorAll('a').forEach(a => {
          a.classList.toggle('active', a.getAttribute('data-anchor') === id);
        });
      }
    });
  }, {
    rootMargin: '-80px 0px -70% 0px',
    threshold: [0, 0.5, 1]
  });

  headings.forEach(h => obs.observe(h));
}

async function loadPage(file) {
  nav.querySelectorAll('a').forEach(a => {
    a.classList.toggle('active', a.dataset.file === file);
  });

  doc.innerHTML = '<div class="loading"><i class="fa-solid fa-spinner fa-spin" aria-hidden="true"></i> Loading documentation...</div>';
  toc.innerHTML = '';

  try {
    const res = await fetch(file + `?t=${Date.now()}`);
    if (!res.ok) {
      throw new Error(`Failed to load ${file}`);
    }
    const md = await res.text();

    marked.setOptions({
      breaks: true,
      gfm: true,
      headerIds: true,
      mangle: false
    });

    const html = marked.parse(md);
    doc.innerHTML = html;

    doc.querySelectorAll('a[href$=".md"]').forEach(a => {
      const href = a.getAttribute('href');
      const target = PAGES.find(p => p.file.toLowerCase() === href.toLowerCase());
      if (target) {
        a.setAttribute('href', `#/${encodeURIComponent(target.file)}`);
        a.removeAttribute('target');
      }
    });

    const hs = enhanceHeadings(doc);
    buildTOC(hs);

    if (lastUpdated) {
      const now = new Date().toLocaleDateString('en-US', {
        year: 'numeric',
        month: 'short',
        day: 'numeric'
      });
      lastUpdated.textContent = now;
    }

    window.scrollTo({ top: 0, behavior: 'smooth' });
  } catch (err) {
    doc.innerHTML = `
      <div class="error">
        <h1><i class="fa-solid fa-triangle-exclamation" aria-hidden="true"></i> Error Loading Page</h1>
        <p>Failed to load <code>${escapeHtml(file)}</code></p>
        <p>${escapeHtml(err.message)}</p>
      </div>
    `;
    console.error('Failed to load page:', err);
  }
}

function route() {
  const file = normalizeHash(location.hash);
  const norm = `#/${encodeURIComponent(file)}`;
  if (location.hash !== norm) {
    history.replaceState(null, '', norm);
  }
  loadPage(file);
}

function initSearch() {
  search.addEventListener('input', async (e) => {
    const q = e.target.value;
    if (!q.trim()) {
      hideSearchResults();
      return;
    }

    await buildSearchIndex();
    renderSearchResults(q);
  });

  search.addEventListener('keydown', (e) => {
    if (searchResults.hidden) {
      if ((e.key === 'ArrowDown' || e.key === 'Enter') && search.value.trim()) {
        e.preventDefault();
        renderSearchResults(search.value);
      }
      return;
    }

    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setSearchActive(searchActiveIndex + 1);
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setSearchActive(searchActiveIndex - 1);
    } else if (e.key === 'Enter') {
      e.preventDefault();
      const active = searchResults.querySelector('.search-result.active');
      if (active) openSearchResult(active.dataset.file);
    } else if (e.key === 'Escape') {
      e.preventDefault();
      search.value = '';
      hideSearchResults();
      search.blur();
    }
  });

  document.addEventListener('keydown', (e) => {
    if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
      e.preventDefault();
      search.focus();
      search.select();
    }
  });

  document.addEventListener('click', (e) => {
    if (!e.target.closest('.search-wrapper')) {
      hideSearchResults();
    }
  });
}

function init() {
  initTheme();
  buildSidebar();
  route();
  initSearch();
  buildSearchIndex();

  window.addEventListener('hashchange', route);
  themeToggle.addEventListener('click', toggleTheme);
  sidebarToggle.addEventListener('click', toggleSidebar);

  window.addEventListener('resize', () => {
    if (window.innerWidth > 1000) {
      closeSidebar();
    }
  });
}

document.addEventListener('DOMContentLoaded', init);
