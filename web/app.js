const root = document.documentElement;
const toggle = document.querySelector('#themeToggle');
const saved = localStorage.getItem('avero-theme');
const systemDark = window.matchMedia('(prefers-color-scheme: dark)').matches;

function setTheme(theme) {
  root.dataset.theme = theme;
  localStorage.setItem('avero-theme', theme);
  toggle.textContent = theme === 'dark' ? '☀' : '◐';
  toggle.setAttribute('aria-label', `Switch to ${theme === 'dark' ? 'light' : 'dark'} theme`);
}

setTheme(saved || (systemDark ? 'dark' : 'light'));
toggle.addEventListener('click', () => setTheme(root.dataset.theme === 'dark' ? 'light' : 'dark'));
