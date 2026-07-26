// GitBook-style Documentation System for RelishAuth
// Enhanced with theme toggle, mobile sidebar, and improved navigation

const PAGES = [
  { file: 'README.md', title: 'Home', section: null, icon: '🏠' },
  { file: 'QuickStart.md', title: 'Quick Start', section: null, icon: '⚡' },
  { file: 'Installation.md', title: 'Installation', section: null, icon: '📥' },
  
  { file: 'Configuration.md', title: 'Configuration Guide', section: 'Configuration', icon: '⚙️' },
  { file: 'AuthMethods.md', title: 'Authentication Methods', section: 'Configuration', icon: '🔑' },
  { file: 'Security.md', title: 'Security Settings', section: 'Configuration', icon: '🛡️' },
  { file: 'Database.md', title: 'Database Setup', section: 'Configuration', icon: '🗄️' },
  
  { file: 'DiscordBot.md', title: 'Discord Bot Setup', section: 'Features', icon: '🤖' },
  { file: 'Commands.md', title: 'Commands', section: 'Features', icon: '⌨️' },
  { file: 'Permissions.md', title: 'Permissions', section: 'Features', icon: '🔐' },
  
  { file: 'Troubleshooting.md', title: 'Troubleshooting', section: 'Reference', icon: '🩺' },
];

// DOM Elements
const el = (sel) => document.querySelector(sel);
const nav = el('#sidebar-nav');
const doc = el('#doc');
const toc = el('#toc');
const search = el('#search');
const lastUpdated = el('#last-updated');
const themeToggle = el('#theme-toggle');
const sidebarToggle = el('#sidebar-toggle');
const sidebar = el('#sidebar');
const searchIndex = new Map();
const searchPreviewIndex = new Map();
let searchIndexPromise = null;

// Theme Management
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

// Mobile Sidebar Management
function toggleSidebar() {
  sidebar.classList.toggle('open');
  
  // Add/remove overlay
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

// Hash and Navigation
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

  return tokens.every((token) => content.includes(token));
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

// Sidebar Builder
function buildSidebar(filter = '') {
  const q = filter.trim();
  let html = '';
  let currentSection = null;
  let hasResults = false;
  
  PAGES.forEach(p => {
    if (q && !matchesSearchQuery(p, q)) {
      return;
    }

    hasResults = true;

    if (p.section !== currentSection) {
      if (p.section) {
        html += `<div class="nav-section">${p.section}</div>`;
      }
      currentSection = p.section;
    }
    const snippet = q ? extractSearchSnippet(p.file, q) : '';
    html += `<a href="#/${encodeURIComponent(p.file)}" data-file="${p.file}"><span class="nav-icon" aria-hidden="true">${p.icon || '*'}</span><span class="nav-text"><span class="nav-label">${p.title}</span>${snippet ? `<span class="nav-snippet">${escapeHtml(snippet)}</span>` : ''}</span></a>`;
  });

  if (q && !hasResults) {
    html = `<div class="nav-empty">No content matches for "<code>${escapeHtml(q)}</code>".</div>`;
  }
  
  nav.innerHTML = html;

  const currentFile = normalizeHash(location.hash);
  const activeLink = nav.querySelector(`a[data-file="${currentFile}"]`);
  if (activeLink) {
    activeLink.classList.add('active');
  }
  
  // Add click handlers for mobile
  nav.querySelectorAll('a').forEach(link => {
    link.addEventListener('click', () => {
      if (window.innerWidth <= 1000) {
        closeSidebar();
      }
    });
  });
}

// Utility Functions
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

// Table of Contents Builder
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

  // Add click handlers to prevent page navigation
  toc.querySelectorAll('a').forEach(link => {
    link.addEventListener('click', (e) => {
      e.preventDefault();
      const targetId = link.getAttribute('data-anchor');
      const targetElement = document.getElementById(targetId);
      if (targetElement) {
        targetElement.scrollIntoView({ behavior: 'smooth', block: 'start' });
        // Update URL without triggering hashchange
        history.pushState(null, '', `${location.pathname}${location.hash.split('#')[0]}#${targetId}`);
      }
    });
  });

  // Highlight active section on scroll
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

// Page Loader
async function loadPage(file) {
  // Update active link in sidebar
  nav.querySelectorAll('a').forEach(a => {
    a.classList.toggle('active', a.dataset.file === file);
  });

  // Show loading state
  doc.innerHTML = '<div class="loading">Loading documentation...</div>';
  toc.innerHTML = '';

  try {
    // Fetch markdown file
    const res = await fetch(file + `?t=${Date.now()}`);
    if (!res.ok) {
      throw new Error(`Failed to load ${file}`);
    }
    const md = await res.text();
    
    // Configure marked
    marked.setOptions({
      breaks: true,
      gfm: true,
      headerIds: true,
      mangle: false
    });
    
    // Parse and render
    const html = marked.parse(md);
    doc.innerHTML = html;

    // Fix internal .md links
    doc.querySelectorAll('a[href$=".md"]').forEach(a => {
      const href = a.getAttribute('href');
      const target = PAGES.find(p => p.file.toLowerCase() === href.toLowerCase());
      if (target) {
        a.setAttribute('href', `#/${encodeURIComponent(target.file)}`);
        a.removeAttribute('target');
      }
    });

    // Build TOC
    const hs = enhanceHeadings(doc);
    buildTOC(hs);
    
    // Update last modified (mock for now)
    if (lastUpdated) {
      const now = new Date().toLocaleDateString('en-US', { 
        year: 'numeric', 
        month: 'short', 
        day: 'numeric' 
      });
      lastUpdated.textContent = now;
    }
    
    // Scroll to top
    window.scrollTo({ top: 0, behavior: 'smooth' });
    
  } catch (err) {
    doc.innerHTML = `
      <div class="error">
        <h1>⚠️ Error Loading Page</h1>
        <p>Failed to load <code>${file}</code></p>
        <p>${err.message}</p>
      </div>
    `;
    console.error('Failed to load page:', err);
  }
}

// Router
function route() {
  const file = normalizeHash(location.hash);
  const norm = `#/${encodeURIComponent(file)}`;
  if (location.hash !== norm) {
    history.replaceState(null, '', norm);
  }
  loadPage(file);
}

// Search with keyboard shortcut
function initSearch() {
  search.addEventListener('input', async (e) => {
    const q = e.target.value;
    if (!q.trim()) {
      buildSidebar();
      return;
    }

    await buildSearchIndex();
    buildSidebar(q);
  });
  
  // Ctrl+K or Cmd+K to focus search
  document.addEventListener('keydown', (e) => {
    if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
      e.preventDefault();
      search.focus();
      search.select();
    }
    
    // Escape to clear search
    if (e.key === 'Escape' && document.activeElement === search) {
      search.value = '';
      buildSidebar();
      search.blur();
    }
  });
}

// Initialize
function init() {
  initTheme();
  buildSidebar();
  route();
  initSearch();
  buildSearchIndex();
  
  // Event listeners
  window.addEventListener('hashchange', route);
  themeToggle.addEventListener('click', toggleTheme);
  sidebarToggle.addEventListener('click', toggleSidebar);
  
  // Close sidebar on window resize
  window.addEventListener('resize', () => {
    if (window.innerWidth > 1000) {
      closeSidebar();
    }
  });
}

// Start when DOM is ready
document.addEventListener('DOMContentLoaded', init);
