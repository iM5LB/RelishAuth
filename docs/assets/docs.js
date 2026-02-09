// GitBook-style Documentation System for RelishAuth
// Enhanced with theme toggle, mobile sidebar, and improved navigation

const PAGES = [
  { file: 'README.md', title: 'Home', section: null },
  { file: 'QuickStart.md', title: 'Quick Start', section: null },
  { file: 'Installation.md', title: 'Installation', section: null },
  
  { file: 'Configuration.md', title: 'Configuration Guide', section: 'Configuration' },
  { file: 'AuthMethods.md', title: 'Authentication Methods', section: 'Configuration' },
  { file: 'Security.md', title: 'Security Settings', section: 'Configuration' },
  { file: 'Database.md', title: 'Database Setup', section: 'Configuration' },
  
  { file: 'DiscordBot.md', title: 'Discord Bot Setup', section: 'Features' },
  { file: 'Commands.md', title: 'Commands', section: 'Features' },
  { file: 'Permissions.md', title: 'Permissions', section: 'Features' },
  
  { file: 'Troubleshooting.md', title: 'Troubleshooting', section: 'Reference' },
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

// Sidebar Builder
function buildSidebar(filter = '') {
  const q = filter.trim().toLowerCase();
  let html = '';
  let currentSection = null;
  
  PAGES.forEach(p => {
    // Filter check
    if (q && !p.title.toLowerCase().includes(q) && !p.file.toLowerCase().includes(q)) {
      return;
    }
    
    // Add section header if new section
    if (p.section !== currentSection) {
      if (p.section) {
        html += `<div class="nav-section">${p.section}</div>`;
      }
      currentSection = p.section;
    }
    
    // Add page link
    html += `<a href="#/${encodeURIComponent(p.file)}" data-file="${p.file}">${p.title}</a>`;
  });
  
  nav.innerHTML = html;
  
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
  search.addEventListener('input', (e) => buildSidebar(e.target.value));
  
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
