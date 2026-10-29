// Builds the documentation site: Markdown pages in content/ are rendered into the layout below and written, with the
// files in public/, to dist/. Tailwind then compiles the stylesheet from the generated pages (see package.json).
//
//   npm run build     builds dist/
//
// Every page lives at the top level of dist/ and links relatively, so the site works on GitHub Pages under any path and
// when opened from disk.

import { cpSync, existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { Marked } from "marked";

const root = dirname(fileURLToPath(import.meta.url));
const dist = join(root, "dist");
const repo = "https://github.com/M-U-C-K-A/WorldPainter";

// The navigation, in order. Each page is content/<slug>.md, published as <slug>.html (index for the home page).
const sections = [
  { title: "Getting started", pages: ["index", "installation", "whats-new"] },
  { title: "Features", pages: ["3d-view", "surroundings-and-seeds", "node-graph", "paths", "trees-and-materials", "edge-blending", "interface"] },
  { title: "Tutorials", pages: ["tutorial-first-archipelago", "tutorial-floating-islands", "tutorial-coral-archipelago"] },
  { title: "Project", pages: ["roadmap", "fork-and-credits"] },
];

// Front matter: a block of "key: value" lines between --- lines at the top of a page
function parse(source) {
  const match = /^---\n([\s\S]*?)\n---\n/.exec(source);
  const meta = {};
  if (match) {
    for (const line of match[1].split("\n")) {
      const colon = line.indexOf(":");
      if (colon > 0) {
        meta[line.slice(0, colon).trim()] = line.slice(colon + 1).trim();
      }
    }
  }
  return { meta, body: match ? source.slice(match[0].length) : source };
}

function slugify(text) {
  return text.toLowerCase().replace(/<[^>]+>/g, "").replace(/&[a-z]+;/g, "").replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");
}

function escape(text) {
  return text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}

function renderMarkdown(body) {
  const toc = [];
  const marked = new Marked({
    gfm: true,
    renderer: {
      heading({ tokens, depth }) {
        const text = this.parser.parseInline(tokens);
        if (depth === 1) {
          return `<h1>${text}</h1>\n`;
        }
        const id = slugify(text);
        if (depth <= 3) {
          toc.push({ id, text: text.replace(/<[^>]+>/g, ""), depth });
        }
        return `<h${depth} id="${id}">${text}<a class="heading-anchor" href="#${id}" aria-label="Link to this section">#</a></h${depth}>\n`;
      },
      // GitHub style callouts: > [!NOTE], > [!TIP], > [!WARNING]
      blockquote({ tokens }) {
        const html = this.parser.parse(tokens);
        const match = /^<p>\[!(NOTE|TIP|WARNING)\]\s*/.exec(html);
        if (match) {
          const kind = match[1].toLowerCase();
          const title = { note: "Note", tip: "Tip", warning: "Careful" }[kind];
          return `<div class="callout callout-${kind}"><span class="callout-title">${title}</span><p>${html.slice(match[0].length)}</div>\n`;
        }
        return `<blockquote>${html}</blockquote>\n`;
      },
      image({ href, title, text }) {
        const caption = title ? `<figcaption>${escape(title)}</figcaption>` : "";
        return `<figure><img src="${href}" alt="${escape(text)}" loading="lazy">${caption}</figure>`;
      },
    },
  });
  return { html: marked.parse(body), toc };
}

const pages = new Map();
for (const section of sections) {
  for (const slug of section.pages) {
    const { meta, body } = parse(readFileSync(join(root, "content", `${slug}.md`), "utf8"));
    pages.set(slug, { slug, section: section.title, meta, body, href: `${slug}.html` });
  }
}
const order = [...pages.keys()];

const icons = {
  github: `<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M12 .5a12 12 0 0 0-3.8 23.4c.6.1.8-.3.8-.6v-2.1c-3.3.7-4-1.6-4-1.6-.6-1.4-1.4-1.8-1.4-1.8-1.1-.8.1-.8.1-.8 1.2.1 1.9 1.3 1.9 1.3 1.1 1.9 2.9 1.3 3.6 1 .1-.8.4-1.3.8-1.6-2.7-.3-5.5-1.3-5.5-6a4.7 4.7 0 0 1 1.3-3.2 4.3 4.3 0 0 1 .1-3.2s1-.3 3.3 1.2a11.5 11.5 0 0 1 6 0C17.3 4.7 18.3 5 18.3 5a4.3 4.3 0 0 1 .1 3.2 4.7 4.7 0 0 1 1.3 3.2c0 4.7-2.8 5.7-5.5 6 .4.4.8 1.1.8 2.2v3.3c0 .3.2.7.8.6A12 12 0 0 0 12 .5Z"/></svg>`,
  sun: `<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="hidden dark:block" aria-hidden="true"><circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M6.34 17.66l-1.41 1.41M19.07 4.93l-1.41 1.41"/></svg>`,
  moon: `<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="block dark:hidden" aria-hidden="true"><path d="M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z"/></svg>`,
  menu: `<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="M4 6h16M4 12h16M4 18h16"/></svg>`,
  arrowLeft: `<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m15 18-6-6 6-6"/></svg>`,
  arrowRight: `<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m9 18 6-6-6-6"/></svg>`,
};

function nav(current) {
  return sections.map((section) => `
      <div class="mb-6">
        <p class="mb-1.5 px-2 text-xs font-medium text-muted-foreground">${section.title}</p>
        <ul class="space-y-0.5">
          ${section.pages.map((slug) => {
            const page = pages.get(slug);
            const label = page.meta.nav || page.meta.title;
            return `<li><a class="nav-link block rounded-md px-2 py-1.5 text-sm text-muted-foreground transition-colors hover:text-foreground" href="${page.href}"${slug === current ? ' aria-current="page"' : ""}>${label}</a></li>`;
          }).join("")}
        </ul>
      </div>`).join("");
}

function pager(slug) {
  const index = order.indexOf(slug);
  const previous = (index > 0) ? pages.get(order[index - 1]) : null;
  const next = (index < order.length - 1) ? pages.get(order[index + 1]) : null;
  const link = (page, direction) => page ? `
    <a href="${page.href}" class="group flex flex-col gap-1 rounded-xl border px-4 py-3 transition-colors hover:bg-muted ${direction === "next" ? "items-end text-right" : ""}">
      <span class="flex items-center gap-1 text-xs text-muted-foreground">${direction === "next" ? `Next ${icons.arrowRight}` : `${icons.arrowLeft} Previous`}</span>
      <span class="text-sm font-medium">${page.meta.nav || page.meta.title}</span>
    </a>` : "<span></span>";
  return `<nav class="mt-14 grid grid-cols-2 gap-3" aria-label="Pages">${link(previous, "previous")}${link(next, "next")}</nav>`;
}

function toc(entries) {
  if (entries.length < 2) {
    return "";
  }
  return `
    <aside class="hidden xl:block">
      <div class="sticky top-20">
        <p class="mb-2 text-xs font-medium text-muted-foreground">On this page</p>
        <ul class="space-y-1.5 text-sm">
          ${entries.map((entry) => `<li class="${entry.depth === 3 ? "pl-3" : ""}"><a class="text-muted-foreground transition-colors hover:text-foreground" href="#${entry.id}">${entry.text}</a></li>`).join("")}
        </ul>
      </div>
    </aside>`;
}

function layout(page, content, entries) {
  const title = (page.slug === "index") ? "WorldPainter Modernised" : `${page.meta.title} · WorldPainter Modernised`;
  const description = page.meta.description || "A modernised fork of WorldPainter, the Minecraft map editor.";
  return `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>${escape(title)}</title>
  <meta name="description" content="${escape(description)}">
  <meta property="og:title" content="${escape(title)}">
  <meta property="og:description" content="${escape(description)}">
  <link rel="icon" type="image/png" href="assets/images/app-icon.png">
  <link rel="stylesheet" href="assets/styles.css">
  <script>
    // Apply the theme before the page is drawn: the saved choice, otherwise the system's
    (function () {
      var saved = null;
      try { saved = localStorage.getItem("theme"); } catch (e) {}
      var dark = saved ? saved === "dark" : window.matchMedia("(prefers-color-scheme: dark)").matches;
      document.documentElement.classList.toggle("dark", dark);
    })();
  </script>
</head>
<body class="min-h-screen font-sans antialiased">
  <header class="sticky top-0 z-40 border-b bg-background/80 backdrop-blur">
    <div class="mx-auto flex h-14 max-w-7xl items-center gap-3 px-4 sm:px-6">
      <button id="menu-button" class="-ml-1 rounded-md p-1.5 text-muted-foreground hover:bg-muted hover:text-foreground lg:hidden" aria-label="Open the navigation" aria-expanded="false" aria-controls="mobile-nav">${icons.menu}</button>
      <a href="index.html" class="flex items-center gap-2 font-semibold tracking-tight">
        <img src="assets/images/app-icon.png" alt="" width="24" height="24" class="rounded-md">
        <span>WorldPainter <span class="font-normal text-muted-foreground">Modernised</span></span>
      </a>
      <span class="hidden rounded-full border px-2 py-0.5 font-mono text-[11px] text-muted-foreground sm:inline">preview</span>
      <div class="ml-auto flex items-center gap-1">
        <a href="${repo}" class="rounded-md p-2 text-muted-foreground hover:bg-muted hover:text-foreground" aria-label="GitHub repository">${icons.github}</a>
        <button id="theme-button" class="rounded-md p-2 text-muted-foreground hover:bg-muted hover:text-foreground" aria-label="Toggle the dark theme">${icons.sun}${icons.moon}</button>
      </div>
    </div>
  </header>
  <div id="mobile-nav" class="fixed inset-x-0 top-14 bottom-0 z-30 hidden overflow-y-auto border-t bg-background px-4 py-6 lg:hidden">${nav(page.slug)}</div>
  <div class="mx-auto grid max-w-7xl grid-cols-1 gap-10 px-4 sm:px-6 lg:grid-cols-[14rem_minmax(0,1fr)] xl:grid-cols-[14rem_minmax(0,1fr)_12rem]">
    <aside class="hidden lg:block">
      <nav class="sidebar-scroll sticky top-14 max-h-[calc(100vh-3.5rem)] overflow-y-auto py-8" aria-label="Documentation">${nav(page.slug)}</nav>
    </aside>
    <main class="min-w-0 py-8 lg:py-10">
      ${page.slug === "index" ? "" : `<p class="mb-2 text-sm text-muted-foreground">${page.section}</p>`}
      <article class="prose prose-zinc dark:prose-invert prose-headings:scroll-mt-20 prose-pre:rounded-xl">
        ${content}
      </article>
      ${pager(page.slug)}
      <footer class="mt-14 border-t pt-6 text-xs text-muted-foreground">
        <p>WorldPainter Modernised is a fork of <a class="underline underline-offset-2 hover:text-foreground" href="https://github.com/Captain-Chaos/WorldPainter">WorldPainter</a> by Pepijn Schmitz and contributors, under the GPL v3. Not affiliated with Mojang or Microsoft.</p>
        <p class="mt-1"><a class="underline underline-offset-2 hover:text-foreground" href="${repo}/edit/master/site/content/${page.slug}.md">Edit this page on GitHub</a></p>
      </footer>
    </main>
    ${toc(entries)}
  </div>
  <script>
    document.getElementById("theme-button").addEventListener("click", function () {
      var dark = !document.documentElement.classList.contains("dark");
      document.documentElement.classList.toggle("dark", dark);
      try { localStorage.setItem("theme", dark ? "dark" : "light"); } catch (e) {}
    });
    var menuButton = document.getElementById("menu-button"), mobileNav = document.getElementById("mobile-nav");
    menuButton.addEventListener("click", function () {
      var open = mobileNav.classList.toggle("hidden") === false;
      menuButton.setAttribute("aria-expanded", String(open));
      document.body.style.overflow = open ? "hidden" : "";
    });
  </script>
</body>
</html>
`;
}

// Build
if (existsSync(dist)) {
  rmSync(dist, { recursive: true });
}
mkdirSync(join(dist, "assets", "fonts"), { recursive: true });
cpSync(join(root, "public"), dist, { recursive: true });
const fonts = join(root, "node_modules", "geist", "dist", "fonts");
cpSync(join(fonts, "geist-sans", "Geist-Variable.woff2"), join(dist, "assets", "fonts", "Geist-Variable.woff2"));
cpSync(join(fonts, "geist-mono", "GeistMono-Variable.woff2"), join(dist, "assets", "fonts", "GeistMono-Variable.woff2"));
for (const page of pages.values()) {
  const { html, toc: entries } = renderMarkdown(page.body);
  writeFileSync(join(dist, page.href), layout(page, html, entries));
}
console.log(`Built ${pages.size} pages into ${dist}`);
