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
  var LANGUAGES = ['en', 'vi'];
  var SECTION_ORDER = ['start', 'player', 'operator', 'reference'];
  var wiki = window.OMNIPET_WIKI;
  if (!wiki) return;

  var state = {
    language: readLanguage(),
    pageId: null,
    headingId: null,
    query: '',
    results: [],
    selectedResult: 0,
  };

  var dom = {
    nav: document.querySelector('#wiki-nav'),
    article: document.querySelector('#wiki-article'),
    toc: document.querySelector('#wiki-toc'),
    search: document.querySelector('#wiki-search'),
    results: document.querySelector('#wiki-results'),
    announcer: document.querySelector('#wiki-announcer'),
    languageButtons: Array.prototype.slice.call(document.querySelectorAll('[data-language]')),
    languageGroup: document.querySelector('[data-language-group]'),
    tagline: document.querySelector('#brand-tagline'),
    stats: document.querySelector('#wiki-stats'),
  };

  /** The scroll listener that keeps the contents list in step. Replaced on every render. */
  var scrollSpy = null;

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

  function pageExists(id) {
    return wiki.pages.some(function (page) { return page.id === id; });
  }

  /**
   * Parses the location hash into a page and an optional heading.
   *
   * <p>Accepts `#/page`, `#/page#heading`, and a bare `#page`. The bare form is honoured because the
   * contents list used to emit `#heading-id` alone, so links shared before this change still land
   * somewhere sensible rather than silently falling back to the first page.
   */
  function readHash() {
    var raw = window.location.hash.replace(/^#/, '');
    if (!raw) return { pageId: null, headingId: null };
    var parts = raw.replace(/^\//, '').split('#');
    var first = parts[0];
    if (pageExists(first)) return { pageId: first, headingId: parts[1] || null };
    // Not a page, so treat the whole thing as a heading on whichever page is already open.
    return { pageId: null, headingId: first || null };
  }

  function text(value) {
    if (value == null) return '';
    return typeof value === 'string' ? value : (value[state.language] || value.en || '');
  }

  /** Every language's copy of a value, for a search that should not depend on the current one. */
  function allText(value) {
    if (value == null) return '';
    if (typeof value === 'string') return value;
    return LANGUAGES.map(function (language) { return value[language] || ''; }).join(' ');
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

  /**
   * A heading's anchor id.
   *
   * <p>Diacritics are folded to their base letters rather than stripped. Dropping them turned "Chọn trang
   * cần đọc" into "ch-n-trang-c-n-c" — unreadable in a shared link, and one heading away from silently
   * colliding with another that differed only in its accents. `đ` needs its own rule because it does not
   * decompose: NFD leaves it whole, so it would otherwise vanish.
   */
  function slug(raw) {
    return raw
      .normalize('NFD')
      // Combining marks, which is what NFD split the accents into.
      .replace(/[̀-ͯ]/g, '')
      .replace(/[đĐ]/g, 'd')
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-|-$/g, '');
  }

  /**
   * Renders the Markdown subset the content uses.
   *
   * @returns {{html: string, headings: Array}} the body and its headings, so the table of contents is
   *     derived from what was actually rendered rather than parsed a second time.
   */
  function renderMarkdown(source, tableLabel) {
    var lines = source.split('\n');
    var html = [];
    var headings = [];
    var index = 0;
    var tableCount = 0;

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
        var body = code.join('\n');
        // The raw text rides along in an attribute so the copy button hands over the source rather than
        // the rendered entities -- copying `&quot;` into a YAML file would not work.
        html.push('<div class="wiki-code" data-code="' + escapeHtml(body) + '">'
          + '<pre><code>' + escapeHtml(body) + '</code></pre></div>');
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
        tableCount += 1;
        // Named after the heading it sits under, so a screen reader announcing the region says which
        // table it is rather than "region" five times on one page.
        var lastHeading = headings.length ? headings[headings.length - 1].title : (tableLabel || 'Table');
        html.push(renderTable(rows, lastHeading + ' (' + tableCount + ')'));
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
  function renderTable(rows, label) {
    var cells = function (row) {
      return row.replace(/^\||\|$/g, '').split('|').map(function (cell) { return cell.trim(); });
    };
    var header = cells(rows[0]);
    var body = rows.slice(2).map(function (row) {
      return '<tr>' + cells(row).map(function (cell) {
        return '<td>' + inline(cell) + '</td>';
      }).join('') + '</tr>';
    });
    // tabindex and a labelled region so a keyboard user can scroll a table that overflows. These are
    // wider than a phone by design -- the header cells do not wrap -- so without a tab stop the
    // right-hand columns could not be reached without a pointer.
    return '<div class="table-scroll" tabindex="0" role="region" aria-label="' + escapeHtml(label) + '">'
      + '<table><thead><tr>'
      + header.map(function (cell) { return '<th>' + inline(cell) + '</th>'; }).join('')
      + '</tr></thead><tbody>' + body.join('') + '</tbody></table></div>';
  }

  // --- search ----------------------------------------------------------------------------------

  /**
   * Finds the query across every page, in both languages.
   *
   * <p>Both languages on purpose: an English reader searching for a Vietnamese term (or the reverse) used
   * to get nothing at all, with no hint that the other language had the answer. The snippet is shown in
   * whichever language matched, so a cross-language hit is visibly in the other language rather than
   * looking like a bug.
   *
   * @returns {Array} one entry per matching heading, capped so a one-letter query cannot list everything
   */
  function search(query) {
    var needle = query.toLowerCase();
    var found = [];

    wiki.pages.forEach(function (page) {
      LANGUAGES.forEach(function (language) {
        var body = page.body[language] || '';
        // Split on headings so a hit can name the section it is in rather than only the page.
        var sections = splitSections(body);
        sections.forEach(function (section) {
          var hay = (section.title + ' ' + section.body).toLowerCase();
          var at = hay.indexOf(needle);
          if (at === -1) return;
          found.push({
            pageId: page.id,
            pageTitle: page.title[language] || page.title.en,
            headingId: section.id,
            heading: section.title,
            snippet: snippet(section.title + ' ' + section.body, at, needle.length),
            language: language,
          });
        });

        // A page whose title or summary matched but whose body did not still deserves to be listed.
        var meta = ((page.title[language] || '') + ' ' + (page.summary[language] || '')).toLowerCase();
        if (meta.indexOf(needle) !== -1 && !found.some(function (hit) {
          return hit.pageId === page.id && hit.language === language;
        })) {
          found.push({
            pageId: page.id,
            pageTitle: page.title[language] || page.title.en,
            headingId: null,
            heading: page.title[language] || page.title.en,
            snippet: escapeHtml(page.summary[language] || ''),
            language: language,
          });
        }
      });
    });

    // Prefer the reader's own language, then keep declaration order so results are stable.
    return found.sort(function (left, right) {
      if (left.language !== right.language) return left.language === state.language ? -1 : 1;
      return 0;
    }).slice(0, 24);
  }

  /** Breaks a body into its `## ` sections, so a hit can be attributed to one. */
  function splitSections(body) {
    var sections = [];
    var current = { id: null, title: '', body: '' };
    body.split('\n').forEach(function (line) {
      if (line.indexOf('## ') === 0) {
        if (current.title || current.body) sections.push(current);
        var title = line.slice(3).trim();
        current = { id: slug(title), title: title, body: '' };
      } else {
        current.body += line + ' ';
      }
    });
    if (current.title || current.body) sections.push(current);
    return sections;
  }

  /** About 60 characters either side of the hit, with the term marked. */
  function snippet(source, at, length) {
    var clean = source.replace(/\s+/g, ' ');
    // The index came from the unnormalised string, so re-find the term in the collapsed one.
    var term = source.substr(at, length);
    var found = clean.toLowerCase().indexOf(term.toLowerCase());
    if (found === -1) found = 0;
    var from = Math.max(0, found - 60);
    var to = Math.min(clean.length, found + length + 60);
    var before = escapeHtml(clean.slice(from, found));
    var hit = escapeHtml(clean.slice(found, found + length));
    var after = escapeHtml(clean.slice(found + length, to));
    return (from > 0 ? '…' : '') + before + '<mark>' + hit + '</mark>' + after
      + (to < clean.length ? '…' : '');
  }

  function renderResults() {
    if (!dom.results) return;
    if (!state.query) {
      dom.results.innerHTML = '';
      dom.results.hidden = true;
      announce('');
      return;
    }

    dom.results.hidden = false;
    if (!state.results.length) {
      dom.results.innerHTML = '<p class="result-count">' + escapeHtml(ui().noResults) + '</p>';
      announce(ui().noResults);
      return;
    }

    dom.results.innerHTML = '<p class="result-count">'
      + escapeHtml(format(ui().resultCount, state.results.length)) + '</p><ul>'
      + state.results.map(function (hit, index) {
        var selected = index === state.selectedResult ? ' class="selected"' : '';
        var href = '#/' + hit.pageId + (hit.headingId ? '#' + hit.headingId : '');
        return '<li><a href="' + href + '"' + selected + '>'
          + '<span class="result-page">' + escapeHtml(hit.pageTitle)
          + (hit.language !== state.language ? ' · ' + hit.language.toUpperCase() : '')
          + '</span>'
          + '<span class="result-heading">' + escapeHtml(hit.heading) + '</span>'
          + '<span class="result-snippet">' + hit.snippet + '</span>'
          + '</a></li>';
      }).join('') + '</ul>';

    announce(format(ui().resultCount, state.results.length));
  }

  /** Replaces `{n}` in a UI string, which is all the interpolation these strings need. */
  function format(template, value) {
    return String(template || '').replace('{n}', String(value));
  }

  /**
   * Says something out loud to a screen reader.
   *
   * <p>Result counts and page changes were previously silent: a sighted reader saw the list shrink, and
   * everyone else got no signal that anything had happened at all.
   */
  function announce(message) {
    if (!dom.announcer) return;
    dom.announcer.textContent = message;
  }

  function openResult(index) {
    var hit = state.results[index];
    if (!hit) return;
    window.location.hash = '#/' + hit.pageId + (hit.headingId ? '#' + hit.headingId : '');
    // The language the hit was found in becomes the reading language, since otherwise following a
    // cross-language result lands on a page that does not contain the word that was searched for.
    if (hit.language !== state.language) {
      state.language = hit.language;
      writeLanguage(hit.language);
    }
    clearSearch();
  }

  function clearSearch() {
    state.query = '';
    state.results = [];
    state.selectedResult = 0;
    if (dom.search) dom.search.value = '';
    renderResults();
  }

  // --- views -----------------------------------------------------------------------------------

  /** Pages in rail order: by section, then declaration order inside it. */
  function orderedPages() {
    return wiki.pages.slice().sort(function (left, right) {
      return SECTION_ORDER.indexOf(left.section) - SECTION_ORDER.indexOf(right.section);
    });
  }

  /**
   * The page rail, grouped under its section headings.
   *
   * <p>Built once and then only updated, because replacing `innerHTML` on every page change destroyed the
   * link the reader had just activated and dropped keyboard focus back to the top of the document — so a
   * keyboard user re-tabbed through the whole header on every navigation.
   */
  function buildNav() {
    var html = '';
    SECTION_ORDER.forEach(function (section) {
      var pages = orderedPages().filter(function (page) { return page.section === section; });
      if (!pages.length) return;
      html += '<h2>' + escapeHtml(ui().sections[section] || section) + '</h2><ul>'
        + pages.map(function (page) {
          return '<li><a href="#/' + page.id + '" data-page="' + page.id + '">'
            + '<span>' + escapeHtml(text(page.title)) + '</span>'
            + '<small>' + escapeHtml(text(page.summary)) + '</small>'
            + '</a></li>';
        }).join('') + '</ul>';
    });
    dom.nav.innerHTML = html;
  }

  /** Marks the open page without rebuilding the rail, so focus survives. */
  function markNavActive() {
    Array.prototype.forEach.call(dom.nav.querySelectorAll('a[data-page]'), function (link) {
      var active = link.dataset.page === state.pageId;
      link.classList.toggle('active', active);
      // aria-current is the part a screen reader uses; the class is only paint.
      if (active) link.setAttribute('aria-current', 'page');
      else link.removeAttribute('aria-current');
    });
  }

  function renderArticle(options) {
    var page = wiki.pages.filter(function (candidate) { return candidate.id === state.pageId; })[0];
    if (!page) return;
    var rendered = renderMarkdown(text(page.body), text(page.title));
    var contents = rendered.headings.length
      ? '<h3>' + escapeHtml(ui().onThisPage) + '</h3><ul>' + rendered.headings.map(function (heading) {
          return '<li><a href="#/' + page.id + '#' + heading.id + '" data-heading="' + heading.id + '">'
            + escapeHtml(heading.title) + '</a></li>';
        }).join('') + '</ul>'
      : '';

    dom.article.innerHTML =
      '<header class="article-head">'
      + '<p class="eyebrow">' + escapeHtml(ui().sections[page.section] || '') + '</p>'
      + '<h1>' + escapeHtml(text(page.title)) + '</h1>'
      + '<p class="lede">' + escapeHtml(text(page.summary)) + '</p>'
      + '</header>'
      // The same contents list, collapsed, for the widths where the aside is not shown.
      + (contents
        ? '<details class="wiki-toc-inline"><summary>' + escapeHtml(ui().onThisPage) + '</summary><ul>'
          + rendered.headings.map(function (heading) {
              return '<li><a href="#/' + page.id + '#' + heading.id + '" data-heading="' + heading.id
                + '">' + escapeHtml(heading.title) + '</a></li>';
            }).join('') + '</ul></details>'
        : '')
      + rendered.html
      + renderPager(page)
      + '<footer class="article-foot"><p>' + escapeHtml(ui().editedNote) + '</p></footer>';

    dom.toc.innerHTML = contents;
    document.title = text(page.title) + ' — OmniPet';

    decorateCodeBlocks();
    observeHeadings();

    if (!options || options.scroll !== false) scrollToTarget();
    if (options && options.announce) announce(text(page.title));
  }

  /**
   * Previous and next, in rail order.
   *
   * <p>The end of a page used to be a dead end: a static provenance line and no route onward except back
   * to the rail.
   */
  function renderPager(page) {
    var ordered = orderedPages();
    var at = ordered.map(function (candidate) { return candidate.id; }).indexOf(page.id);
    var previous = at > 0 ? ordered[at - 1] : null;
    var next = at >= 0 && at < ordered.length - 1 ? ordered[at + 1] : null;
    if (!previous && !next) return '';

    var link = function (target, kind, label) {
      if (!target) return '';
      return '<a class="' + kind + '" href="#/' + target.id + '">'
        + '<small>' + escapeHtml(label) + '</small>'
        + '<strong>' + escapeHtml(text(target.title)) + '</strong></a>';
    };
    return '<nav class="wiki-pager" aria-label="' + escapeHtml(ui().pagerLabel) + '">'
      + link(previous, 'prev', ui().previousPage)
      + link(next, 'next', ui().nextPage)
      + '</nav>';
  }

  /** Adds a copy button to every code block. */
  function decorateCodeBlocks() {
    Array.prototype.forEach.call(dom.article.querySelectorAll('.wiki-code'), function (block) {
      var button = document.createElement('button');
      button.type = 'button';
      button.textContent = ui().copy;
      button.addEventListener('click', function () {
        var source = block.dataset.code || '';
        copyText(source).then(function (ok) {
          button.textContent = ok ? ui().copied : ui().copyFailed;
          announce(ok ? ui().copied : ui().copyFailed);
          window.setTimeout(function () { button.textContent = ui().copy; }, 1600);
        });
      });
      block.appendChild(button);
    });
  }

  /**
   * Copies text, falling back to a selection-based copy.
   *
   * <p>`navigator.clipboard` is unavailable on `file://` and on plain HTTP, and this site must work from
   * both, so the older path is kept rather than leaving the button dead there.
   */
  function copyText(value) {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      return navigator.clipboard.writeText(value)
        .then(function () { return true; })
        .catch(function () { return legacyCopy(value); });
    }
    return Promise.resolve(legacyCopy(value));
  }

  function legacyCopy(value) {
    try {
      var scratch = document.createElement('textarea');
      scratch.value = value;
      scratch.setAttribute('readonly', '');
      scratch.style.position = 'fixed';
      scratch.style.opacity = '0';
      document.body.appendChild(scratch);
      scratch.select();
      var ok = document.execCommand('copy');
      document.body.removeChild(scratch);
      return ok;
    } catch (ignored) {
      return false;
    }
  }

  /**
   * Marks the heading the reader is currently inside.
   *
   * <p>Computed from geometry rather than from intersection events. An observer with a narrow band only
   * reports while a heading is *inside* that band, so a section longer than the viewport left nothing
   * current and the marker blinked out in the middle of the longest sections — exactly where a contents
   * list is most useful. The current heading is instead the last one to have scrolled above the reading
   * line, which is always defined once the reader is past the first heading.
   *
   * <p>Driven by a scroll listener throttled to one frame: a still page fires no scroll events, so this
   * costs nothing while nobody is scrolling.
   */
  function observeHeadings() {
    if (scrollSpy) {
      window.removeEventListener('scroll', scrollSpy);
      scrollSpy = null;
    }
    var headings = Array.prototype.slice.call(dom.article.querySelectorAll('h2[id]'));
    if (!headings.length) return;

    var queued = false;
    var update = function () {
      queued = false;
      // The sticky header's height, so a heading scrolled underneath it stops counting as current.
      var line = 110;
      var current = null;
      for (var index = 0; index < headings.length; index++) {
        if (headings[index].getBoundingClientRect().top <= line) current = headings[index].id;
        else break;
      }
      // Before the first heading, mark the first one rather than nothing: the reader is in its lede.
      markCurrentHeading(current || headings[0].id);
    };

    scrollSpy = function () {
      if (queued) return;
      queued = true;
      if (window.requestAnimationFrame) window.requestAnimationFrame(update);
      else update();
    };

    window.addEventListener('scroll', scrollSpy);
    update();
  }

  function markCurrentHeading(id) {
    Array.prototype.forEach.call(
      document.querySelectorAll('[data-heading]'),
      function (link) {
        if (link.dataset.heading === id) link.setAttribute('aria-current', 'location');
        else link.removeAttribute('aria-current');
      },
    );
  }

  /**
   * Puts the reader at the top of the new page, or at the heading they asked for.
   *
   * <p>The old code set `article.scrollTop = 0`, but the article is not a scroll container — the window
   * is — so it did nothing and finishing one page dropped the reader into the middle of the next. Focus
   * moves too, so a screen reader starts reading the new page instead of staying where it was.
   */
  function scrollToTarget() {
    var target = state.headingId ? document.getElementById(state.headingId) : null;
    if (target) {
      // Smooth for an anchor on the page you are already reading: the animation shows the reader where
      // they were taken from, which is the whole value of scrolling rather than jumping.
      target.scrollIntoView();
    } else {
      /*
       * Instant for a page change. The article's content was replaced a moment ago, so animating back to
       * the top scrolls through a page the reader never saw — several hundred milliseconds of unrelated
       * text sliding past. `behavior: 'instant'` overrides the stylesheet's `scroll-behavior: smooth`,
       * with the two-argument call kept for browsers that do not take the options object.
       */
      try {
        window.scrollTo({ top: 0, left: 0, behavior: 'instant' });
      } catch (ignored) {
        window.scrollTo(0, 0);
      }
    }
    // Focus without scrolling again: the call above has already put us in the right place.
    try {
      dom.article.focus({ preventScroll: true });
    } catch (ignored) {
      dom.article.focus();
    }
  }

  function renderChrome() {
    document.documentElement.lang = state.language;
    if (dom.tagline) dom.tagline.textContent = ui().brandTagline + ' / ' + wiki.meta.version;
    if (dom.search) {
      dom.search.placeholder = ui().searchPlaceholder;
      dom.search.setAttribute('aria-label', ui().searchLabel);
    }
    // The group label was defined in both languages and never read, so it stayed English in VI mode.
    if (dom.languageGroup) dom.languageGroup.setAttribute('aria-label', ui().languageLabel);
    dom.languageButtons.forEach(function (button) {
      var selected = button.dataset.language === state.language;
      button.classList.toggle('active', selected);
      button.setAttribute('aria-pressed', selected ? 'true' : 'false');
    });
  }

  function render() {
    renderChrome();
    buildNav();
    markNavActive();
    renderArticle({ scroll: false });
    renderResults();
  }

  // --- events ----------------------------------------------------------------------------------

  dom.languageButtons.forEach(function (button) {
    button.addEventListener('click', function () {
      state.language = button.dataset.language;
      writeLanguage(state.language);
      if (state.query) state.results = search(state.query);
      render();
    });
  });

  if (dom.search) {
    dom.search.addEventListener('input', function () {
      state.query = dom.search.value.trim();
      state.results = state.query ? search(state.query) : [];
      state.selectedResult = 0;
      renderResults();
    });

    /*
     * Arrow keys move through the results and Enter opens one, so a search can be completed without
     * leaving the keyboard. Escape clears, which is the conventional way out of a search field.
     */
    dom.search.addEventListener('keydown', function (event) {
      if (event.key === 'Escape') {
        clearSearch();
        return;
      }
      if (!state.results.length) return;
      if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
        event.preventDefault();
        var step = event.key === 'ArrowDown' ? 1 : -1;
        var count = state.results.length;
        state.selectedResult = (state.selectedResult + step + count) % count;
        renderResults();
        return;
      }
      if (event.key === 'Enter') {
        event.preventDefault();
        openResult(state.selectedResult);
      }
    });
  }

  if (dom.results) {
    dom.results.addEventListener('click', function (event) {
      var link = event.target.closest ? event.target.closest('a') : null;
      // The hash change does the navigating; this only has to put the search away.
      if (link) window.setTimeout(clearSearch, 0);
    });
  }

  /**
   * All in-page navigation runs through the hash, including contents links.
   *
   * <p>The contents list used to emit a bare `#heading-id`, which replaced the hash outright and left the
   * URL with no page in it — so reloading or sharing that link dropped the reader on the first page
   * instead of the section they were sent to.
   */
  window.addEventListener('hashchange', function () {
    var parsed = readHash();
    var pageChanged = parsed.pageId && parsed.pageId !== state.pageId;
    if (parsed.pageId) state.pageId = parsed.pageId;
    state.headingId = parsed.headingId;
    if (pageChanged) {
      markNavActive();
      renderArticle({ announce: true });
    } else {
      // Same page, different heading: no need to re-render, just move.
      scrollToTarget();
      markCurrentHeading(state.headingId);
    }
  });

  /** `/` focuses the search box, the shortcut readers expect from a documentation site. */
  document.addEventListener('keydown', function (event) {
    if (event.key !== '/' || event.metaKey || event.ctrlKey || event.altKey) return;
    var tag = (event.target.tagName || '').toLowerCase();
    if (tag === 'input' || tag === 'textarea' || event.target.isContentEditable) return;
    if (!dom.search) return;
    event.preventDefault();
    dom.search.focus();
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

  var initial = readHash();
  state.pageId = initial.pageId || wiki.pages[0].id;
  state.headingId = initial.headingId;

  render();
  // Only after the first render, since the target heading does not exist before the article is in the DOM.
  if (state.headingId) scrollToTarget();
  loadStats();
})();
