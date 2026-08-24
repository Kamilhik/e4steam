(() => {
  "use strict";

  const views = {
    github: {
      file: "/README.md",
      label: "GitHub",
      title: "README.md",
      className: "github-surface"
    },
    curseforge: {
      file: "/curseforge.md",
      label: "CurseForge",
      title: "curseforge.md",
      className: "curseforge-surface"
    }
  };

  const preview = document.getElementById("preview");
  const status = document.getElementById("status");
  const platformLabel = document.getElementById("platformLabel");
  const documentTitle = document.getElementById("documentTitle");
  const openSource = document.getElementById("openSource");
  const copySource = document.getElementById("copySource");
  const buttons = Array.from(document.querySelectorAll(".view-switcher button[data-view]"));
  let activeView = "github";
  let activeMarkdown = "";

  function escapeHtml(value) {
    return value.replace(/[&<>"']/g, character => ({
      "&": "&amp;",
      "<": "&lt;",
      ">": "&gt;",
      "\"": "&quot;",
      "'": "&#039;"
    })[character]);
  }

  function bindInternalLinks() {
    preview.querySelectorAll("a[href^='#']").forEach(anchor => {
      anchor.addEventListener("click", event => {
        event.preventDefault();

        let targetId = "";
        try {
          targetId = decodeURIComponent(anchor.getAttribute("href").slice(1));
        } catch {
          // Fall back to the only collapsible language section below.
        }
        let target = Array.from(preview.querySelectorAll("[id]"))
          .find(element => element.id === targetId);
        if (!target) target = preview.querySelector("details");
        if (!target) return;

        let details = target.matches("details") ? target : target.closest("details");
        if (!details) details = preview.querySelector("details");
        if (details) {
          details.open = true;
          if (!details.contains(target)) target = details;
        }
        target.scrollIntoView({ behavior: "smooth", block: "start" });
      });
    });
  }

  async function render(viewName, updateHash = true) {
    const view = views[viewName] || views.github;
    activeView = viewName in views ? viewName : "github";
    buttons.forEach(button => button.setAttribute("aria-pressed", String(button.dataset.view === activeView)));
    platformLabel.textContent = view.label;
    documentTitle.textContent = view.title;
    openSource.href = view.file;
    status.hidden = false;
    status.textContent = `Загрузка ${view.title}…`;
    preview.replaceChildren();
    preview.className = `preview-surface markdown-body ${view.className}`;
    preview.dataset.view = activeView;

    try {
      const response = await fetch(`${view.file}?preview=${Date.now()}`, { cache: "no-store" });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      activeMarkdown = await response.text();
      if (!window.marked) throw new Error("Markdown renderer is unavailable");
      window.marked.setOptions({ gfm: true, breaks: false });
      preview.innerHTML = window.marked.parse(activeMarkdown);
      bindInternalLinks();
      status.hidden = true;
      if (updateHash) {
        history.replaceState(null, "", `/tools/publication-preview/#${activeView}`);
      }
    } catch (error) {
      status.hidden = false;
      status.innerHTML = `<strong>Не удалось открыть ${escapeHtml(view.title)}.</strong><br>Запустите страницу через start-preview.cmd и обновите вкладку.<br><small>${escapeHtml(String(error.message || error))}</small>`;
    }
  }

  buttons.forEach(button => button.addEventListener("click", () => render(button.dataset.view)));

  copySource.addEventListener("click", async () => {
    if (!activeMarkdown) return;
    try {
      await navigator.clipboard.writeText(activeMarkdown);
      copySource.textContent = "Скопировано";
      window.setTimeout(() => { copySource.textContent = "Копировать Markdown"; }, 1400);
    } catch {
      copySource.textContent = "Не удалось скопировать";
      window.setTimeout(() => { copySource.textContent = "Копировать Markdown"; }, 1800);
    }
  });

  const initialView = location.hash.slice(1);
  render(initialView in views ? initialView : "github", false);
})();
