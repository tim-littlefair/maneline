'use strict';
let state = { connected: false, presets: [] };
let switching = false;
let requestRunning = false;
let renderedNames = '';
let selectionRevision = 0;
const grid = document.getElementById('presets');
const filter = document.getElementById('filter');
const message = document.getElementById('message');

function render() {
  document.getElementById('connection').textContent = state.connected
    ? 'Connected over Bluetooth'
    : state.error || 'Connecting to your Micro Plus…';
  document.getElementById('current').textContent = state.connected && state.preset
    ? `Preset ${state.preset} · ${state.name || 'Unnamed preset'}`
    : 'Reading the current preset…';
  const names = JSON.stringify(state.presets);
  if (names !== renderedNames) {
    grid.replaceChildren();
    for (const preset of state.presets) {
      const button = document.createElement('button');
      button.type = 'button';
      button.dataset.preset = preset.preset;
      const number = document.createElement('span');
      number.textContent = `Preset ${preset.preset}`;
      const name = document.createElement('strong');
      name.textContent = preset.name || 'Unnamed preset';
      button.append(number, name);
      button.addEventListener('click', () => selectPreset(preset.preset));
      grid.append(button);
    }
    renderedNames = names;
  }
  const query = filter.value.toLocaleLowerCase().trim();
  let visible = 0;
  for (const button of grid.children) {
    button.disabled = switching || !state.connected;
    button.setAttribute('aria-pressed', String(Number(button.dataset.preset) === state.preset));
    button.hidden = !button.textContent.toLocaleLowerCase().includes(query);
    if (!button.hidden) visible++;
  }
  grid.setAttribute('aria-busy', String(switching || !state.connected));
  document.getElementById('empty').hidden = !state.connected || visible > 0;
}

async function readState() {
  if (requestRunning || switching) return;
  requestRunning = true;
  const revision = selectionRevision;
  try {
    const response = await fetch('/api/micro-plus', { signal: AbortSignal.timeout(5000) });
    if (!response.ok) throw new Error('Could not read the connection. Trying again…');
    const nextState = await response.json();
    if (revision === selectionRevision) state = nextState;
  } catch (error) {
    if (revision === selectionRevision) {
      state = { ...state, connected: false, error: 'Cannot reach Maneline. Check that it is still running.' };
    }
  } finally {
    requestRunning = false;
    render();
  }
}

async function selectPreset(preset) {
  if (switching || !state.connected) return;
  switching = true;
  selectionRevision++;
  message.className = '';
  message.textContent = `Selecting preset ${preset}…`;
  render();
  try {
    const response = await fetch('/api/micro-plus', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ preset }), signal: AbortSignal.timeout(15000)
    });
    const result = await response.json();
    if (!response.ok) throw new Error(result.error || 'The amp did not confirm the preset.');
    state = result;
    message.textContent = `Selected preset ${state.preset} · ${state.name || 'Unnamed preset'}`;
  } catch (error) {
    state = { ...state, connected: false };
    message.className = 'error';
    message.textContent = error.message || 'Could not confirm the preset. Checking the connection…';
  } finally {
    switching = false;
    render();
    readState();
  }
}

filter.addEventListener('input', render);
readState();
setInterval(readState, 1000);
