const search = document.querySelector('#doc-search');
const cards = [...document.querySelectorAll('.doc-card')];
const empty = document.querySelector('#empty-state');

search?.addEventListener('input', () => {
  const query = search.value.trim().toLowerCase();
  let visible = 0;

  for (const card of cards) {
    const haystack = `${card.dataset.search} ${card.textContent}`.toLowerCase();
    const matches = !query || haystack.includes(query);
    card.hidden = !matches;
    if (matches) visible += 1;
  }

  empty.hidden = visible !== 0;
});

