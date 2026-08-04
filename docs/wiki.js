/**
 * The wiki shell: language switching, navigation, search, and a small Markdown renderer.
 *
 * <p>No framework and no build step. The site is served straight from `docs/` by GitHub Pages and must
 * also work when opened from `file://`, so everything is plain ES modules-free script and every asset is
 * a relative path.
 *
 * <p>The Markdown subset is deliberate: headings, paragraphs, lists, tables, and fenced code are all the
 * content uses, and a library for that would be a dependency the docs site does not otherwise need.
 */
(function () {
  'use strict';

  var STORAGE_KEY = 'omnipet-wiki-language';
  var DEFAULT_LANGUAGE = 'en';
  var wiki = window.OMNIPET_WIKI;
  if (!wiki) return;

  var state = {
    language: readLanguage(),
    pageId: readPageFromHash() || wiki.pages[0].id,
    query: '',
  };

  var dom = {
    nav: document.querySelector('#wiki-nav'),
    article: document.querySelector('#wiki-article'),
    toc: document.querySelector('#wiki-toc'),
    search: document.querySelector('#wiki-search'),
    languageButtons: Array.prototype.slice.call(document.querySelectorAll('[data-language]')),
    tagline: document.querySelector('#brand-tagline'),
    stats: document.querySelector('#wiki-stats'),
  };

  /** Persisted so a Vietnamese reader does not re-pick the language on every page. */
  function readLanguage() {
    try {
      var stored = window.localStorage.getItem(STORAGE_KEY);
      if (stored === 'en' || stored === 'vi') return stored;
    } catch (ignored) {
      // Private browsing or a file:// origin can refuse storage. The default is still correct.
    }
    var browser = (navigator.language || '').toLowerCase();
    return browser.indexOf('vi') === 0 ? 'vi' : DEFAULT_LANGUAGE;
  }

  function writeLanguage(language) {
    try {
      window.localStorage.setItem(STORAGE_KEY, language);
    } catch (ignored) {
      // Not being able to remember the choice is survivable; failing the click is not.
    }
  }

  function readPageFromHash() {
    var id = window.location.hash.replace(/^#\/?/, '');
    return wiki.pages.some(function (page) { return page.id === id; }) ? id : null;
  }

  function text(value) {
    if (value == null) return '';
    return typeof value === 'string' ? value : (value[state.language] || value.en || '');
  }

  function ui() {
    return wiki.ui[state.language] || wiki.ui.en;
  }

  // --- rendering -------------------------------------------------------------------------------

  function escapeHtml(raw) {
    return raw
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  /**
   * Inline spans: code, bold, images, links. Applied after escaping, so content cannot inject markup.
   *
   * <p>Images are matched before links because the syntaxes differ by one leading character, and a link
   * rule applied first would consume the image and leave a stray exclamation mark.
   */
  function inline(raw) {
    return escapeHtml(raw)
      .replace(/`([^`]+)`/g, '<code>$1</code>')
      .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
      .replace(/!\[([^\]]*)\]\(([^)]+)\)/g,
        '<img class="wiki-figure" src="$2" alt="$1" loading="lazy">')
      .replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2">$1</a>');
  }

  function slug(raw) {
    return raw.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
  }

  /**
   * Renders the Markdown subset the content uses.
   *
   * @returns {{html: string, headings: Array}} the body and its headings, so the table of contents is
   *     derived from what was actually rendered rather than parsed a second time.
   */
  function renderMarkdown(source) {
    var lines = source.split('\n');
    var html = [];
    var headings = [];
    var index = 0;

    while (index < lines.length) {
      var line = lines[index];

      if (line.indexOf('```') === 0) {
        var code = [];
        index += 1;
        while (index < lines.length && lines[index].indexOf('```') !== 0) {
          code.push(lines[index]);
          index += 1;
        }
        index += 1;
        html.push('<pre><code>' + escapeHtml(code.join('\n')) + '</code></pre>');
        continue;
      }

      if (line.indexOf('## ') === 0) {
        var title = line.slice(3).trim();
        var id = slug(title);
        headings.push({ id: id, title: title });
        html.push('<h2 id="' + id + '">' + inline(title) + '</h2>');
        index += 1;
        continue;
      }

      if (line.indexOf('| ') === 0) {
        var rows = [];
        while (index < lines.length && lines[index].indexOf('|') === 0) {
          rows.push(lines[index]);
          index += 1;
        }
        html.push(renderTable(rows));
        continue;
      }

      if (/^[-*] /.test(line)) {
        var items = [];
        while (index < lines.length && /^[-*] /.test(lines[index])) {
          items.push('<li>' + inline(lines[index].slice(2)) + '</li>');
          index += 1;
        }
        html.push('<ul>' + items.join('') + '</ul>');
        continue;
      }

      if (/^\d+\. /.test(line)) {
        var ordered = [];
        while (index < lines.length && /^\d+\. /.test(lines[index])) {
          ordered.push('<li>' + inline(lines[index].replace(/^\d+\.\s*/, '')) + '</li>');
          index += 1;
        }
        html.push('<ol>' + ordered.join('') + '</ol>');
        continue;
      }

      if (line.trim() === '') {
        index += 1;
        continue;
      }

      var paragraph = [];
      while (index < lines.length
          && lines[index].trim() !== ''
          && lines[index].indexOf('## ') !== 0
          && lines[index].indexOf('```') !== 0
          && lines[index].indexOf('|') !== 0
          && !/^[-*] /.test(lines[index])
          && !/^\d+\. /.test(lines[index])) {
        paragraph.push(lines[index]);
        index += 1;
      }
      html.push('<p>' + inline(paragraph.join(' ')) + '</p>');
    }

    return { html: html.join('\n'), headings: headings };
  }

  /** The second row of a Markdown table is its alignment rule and carries no content. */
  function renderTable(rows) {
    var cells = function (row) {
      return row.replace(/^\||\|$/g, '').split('|').map(function (cell) { return cell.trim(); });
    };
    var header = cells(rows[0]);
    var body = rows.slice(2).map(function (row) {
      return '<tr>' + cells(row).map(function (cell) {
        return '<td>' + inline(cell) + '</td>';
      }).join('') + '</tr>';
    });
    return '<div class="table-scroll"><table><thead><tr>'
      + header.map(function (cell) { return '<th>' + inline(cell) + '</th>'; }).join('')
      + '</tr></thead><tbody>' + body.join('') + '</tbody></table></div>';
  }

  // --- views -----------------------------------------------------------------------------------

  function matchesQuery(page) {
    if (!state.query) return true;
    var haystack = [
      text(page.title), text(page.summary), text(page.body),
    ].join(' ').toLowerCase();
    return haystack.indexOf(state.query.toLowerCase()) !== -1;
  }

  /**
   * The page bar: one row of tabs, in reading order.
   *
   * <p>A horizontal bar rather than a sidebar because six pages do not need a rail, and the article is
   * what the reader came for — the width the sidebar took is better spent on prose. Section headings and
   * per-page summaries are dropped for the same reason: a tab has room for a name, and the summary is
   * already the first thing the page itself says.
   *
   * <p>Pages stay in declaration order, which groups them start / player / operator / reference without
   * needing the labels to say so.
   */
  function renderNav() {
    var order = ['start', 'player', 'operator', 'reference'];
    var matches = wiki.pages.filter(matchesQuery).slice().sort(function (left, right) {
      return order.indexOf(left.section) - order.indexOf(right.section);
    });

    if (!matches.length) {
      dom.nav.innerHTML = '<p class="empty">' + escapeHtml(ui().noResults) + '</p>';
      return;
    }

    dom.nav.innerHTML = '<ul>' + matches.map(function (page) {
      var active = page.id === state.pageId ? ' class="active"' : '';
      // The summary becomes the tooltip: still reachable, no longer occupying the bar.
      return '<li><a href="#/' + page.id + '"' + active
        + ' title="' + escapeHtml(text(page.summary)) + '">'
        + escapeHtml(text(page.title)) + '</a></li>';
    }).join('') + '</ul>';
  }

  function renderArticle() {
    var page = wiki.pages.filter(function (candidate) { return candidate.id === state.pageId; })[0];
    if (!page) return;
    var rendered = renderMarkdown(text(page.body));

    dom.article.innerHTML =
      '<header class="article-head">'
      + '<p class="eyebrow">' + escapeHtml(ui().sections[page.section] || '') + '</p>'
      + '<h1>' + escapeHtml(text(page.title)) + '</h1>'
      + '<p class="lede">' + escapeHtml(text(page.summary)) + '</p>'
      + '</header>' + rendered.html
      + '<footer class="article-foot"><p>' + escapeHtml(ui().editedNote) + '</p></footer>';

    dom.toc.innerHTML = rendered.headings.length
      ? '<h3>' + escapeHtml(ui().onThisPage) + '</h3><ul>' + rendered.headings.map(function (heading) {
          return '<li><a href="#' + heading.id + '">' + escapeHtml(heading.title) + '</a></li>';
        }).join('') + '</ul>'
      : '';

    document.title = text(page.title) + ' — OmniPet';
    dom.article.scrollTop = 0;
  }

  function renderChrome() {
    document.documentElement.lang = state.language;
    if (dom.tagline) dom.tagline.textContent = ui().brandTagline + ' / ' + wiki.meta.version;
    if (dom.search) {
      dom.search.placeholder = ui().searchPlaceholder;
      dom.search.setAttribute('aria-label', ui().searchLabel);
    }
    dom.languageButtons.forEach(function (button) {
      var selected = button.dataset.language === state.language;
      button.classList.toggle('active', selected);
      button.setAttribute('aria-pressed', selected ? 'true' : 'false');
    });
  }

  function render() {
    renderChrome();
    renderNav();
    renderArticle();
  }

  // --- events ----------------------------------------------------------------------------------

  dom.languageButtons.forEach(function (button) {
    button.addEventListener('click', function () {
      state.language = button.dataset.language;
      writeLanguage(state.language);
      render();
    });
  });

  if (dom.search) {
    dom.search.addEventListener('input', function () {
      state.query = dom.search.value.trim();
      renderNav();
    });
  }

  window.addEventListener('hashchange', function () {
    var id = readPageFromHash();
    if (id && id !== state.pageId) {
      state.pageId = id;
      render();
    }
  });

  /**
   * Loads the generated build statistics.
   *
   * <p>Fetched rather than inlined so the number cannot be hand-edited into a stale claim, and failing
   * softly because `file://` refuses `fetch` — the panel is informative, not load-bearing.
   */
  function loadStats() {
    if (!dom.stats || !window.fetch) return;
    fetch(wiki.meta.statsFile)
      .then(function (response) { return response.ok ? response.json() : null; })
      .then(function (stats) {
        if (!stats) return;
        dom.stats.innerHTML = [
          ['Tests', stats.tests],
          ['Suites', stats.suites],
          ['Skipped', stats.skipped],
          ['Generated', stats.generatedAt],
        ].map(function (pair) {
          return '<div><dt>' + pair[0] + '</dt><dd>' + escapeHtml(String(pair[1])) + '</dd></div>';
        }).join('');
      })
      .catch(function () {
        // No statistics is better than wrong statistics.
      });
  }

  render();
  loadStats();
})();
