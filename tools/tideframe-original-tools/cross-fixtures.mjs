// Writes the shared Probar = plugin cases (idea 27) to src/test/resources/cross/: each case is a few pages, the
// clicks, keys and game events to replay, and what must come out. editor/cross.test.mjs runs them through Probar
// and CrossProbarTest.java through the plugin's ScreenRuntime; both must give exactly the expected result.
// Run: node tools/cross-fixtures.mjs (then commit the JSON files it writes).
import { mkdirSync, writeFileSync, readdirSync, unlinkSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { validateScreen } from '../editor/model.mjs';

const out = join(dirname(fileURLToPath(import.meta.url)), '..', 'src', 'test', 'resources', 'cross');
const style = { opacity: 1, fillColor: '#28384b', borderColor: '#ffffff', textColor: '#ffffff', shadow: false, rotation: 0, radius: .08, outlineWidth: 1, hoverColor: null,
  hoverEffect: 'none', textAlign: 'center', wrapWidth: 0, brightness: 1, textScale: 1, textOutline: null, textShadow: false, gradientColor: null, gradientDirection: 'vertical', textGradient: null };
let row = 0;
function element(id, kind, logic, { text = id.toUpperCase(), action = '', y = null, height = .14, x = 0, width = .6 } = {}) {
  const e = { id, text, x, y: y ?? .75 - row++ * .18, width, height, kind, action, shape: 'rectangle', border: true, messageCondition: null, background: false, image: null,
    style: { ...style }, layer: 0, actionArea: null };
  if (logic) e.logic = logic;
  return e;
}
function page(id, logic, elements) {
  row = 0;
  const screen = { version: 9, id, title: id.toUpperCase(), quality: { preset: 'low', width: 512, height: 288 }, elements: elements(), presentation: { mode: 'menu', scale: 1, cursor: 'cross' }, ...(logic ? { logic } : {}) };
  validateScreen(screen);
  return screen;
}
const say = value => ({ type: 'message', value });
const set = (variable, operation, value) => ({ type: 'variable', variable, operation, value });

const cases = {
  'variables-conditions-functions': {
    about: 'Conditions pick their branch, variables count down, functions take defaults and arguments.',
    pages: [page('shop', { variables: { coins: '100', price: '30' }, functions: { receipt: { params: { item: 'thing' }, actions: [say('Receipt: {item} for {price}')] } } }, () => [
      element('buy', 'button', { actions: [{ type: 'condition', value: '{coins} >= {price}', then: [set('coins', 'subtract', '{price}'), say('Bought! {coins} left'),
        { type: 'function', function: 'receipt', arguments: { item: 'sword' } }], else: [say('Not enough: {coins}')] }] })])],
    steps: [{ click: 'buy' }, { click: 'buy' }, { click: 'buy' }, { click: 'buy' }],
    expect: { events: ['message Bought! 70 left', 'message Receipt: sword for 30', 'message Bought! 40 left', 'message Receipt: sword for 30',
      'message Bought! 10 left', 'message Receipt: sword for 30', 'message Not enough: 10'], variables: { coins: '10' }, page: 'shop', closed: false }
  },
  'calculated-text': {
    about: 'Calculated tokens, six-decimal arithmetic, built-in values before variables, ${name} skipping the scope.',
    pages: [page('calc', { variables: { coins: '7', rate: '3', level: '99' }, functions: { show: { params: { coins: 'scoped' }, actions: [say('{coins} ${coins}')] } } }, () => [
      element('calc', 'button', { actions: [say('{coins * rate} / {round(coins / rate, 2)} / {coins >= 5 ? "many" : "few"} / {player + "!"} / {coins / 0}'),
        set('ratio', 'set', '10'), set('ratio', 'divide', '3'), say('{ratio} {level} {balance}'), set('ratio', 'multiply', 'abc'), say('still {ratio}'),
        { type: 'function', function: 'show' }] })])],
    steps: [{ click: 'calc' }],
    expect: { events: ['message 21 / 2.33 / many / Steve! / {coins / 0}', 'message 3.333333 12 1250', 'message still 3.333333', 'message scoped 7'],
      variables: { ratio: '3.333333', level: '99' }, page: 'calc', closed: false }
  },
  'cooldown': {
    about: 'A click inside the cooldown runs on-cooldown with the seconds left; the wait starts only when a click runs something.',
    pages: [page('daily', null, () => [
      element('claim', 'button', { actions: [say('reward')], cooldown: '2s', 'on-cooldown': [say('wait {cooldown}')] }),
      element('quiet', 'button', { 'right-actions': [say('right')], cooldown: '500ms' })])],
    steps: [{ click: 'claim' }, { wait: 500 }, { click: 'claim' }, { wait: 1400 }, { click: 'claim' }, { wait: 100 }, { click: 'claim' },
      { click: 'quiet' }, { right: 'quiet' }, { right: 'quiet' }],
    expect: { events: ['message reward', 'message wait 1.5', 'message wait 0.1', 'message reward', 'message right'], page: 'daily', closed: false }
  },
  'confirm': {
    about: 'confirm runs then on yes and else on no; the rest of the list goes on after the answer.',
    pages: [page('sell', { variables: { coins: '0', price: '40' } }, () => [
      element('sell', 'button', { actions: [{ type: 'confirm', title: 'Sure?', value: 'Sell for {price}?', yes: 'Sell', no: 'Keep',
        then: [set('coins', 'add', '{price}'), say('sold')], else: [say('kept')] }, say('coins {coins}')] })])],
    steps: [{ answers: ['yes', 'no'] }, { click: 'sell' }, { click: 'sell' }],
    expect: { events: ['confirm Sell for 40?', 'message sold', 'message coins 40', 'confirm Sell for 40?', 'message kept', 'message coins 40'], variables: { coins: '40' }, page: 'sell', closed: false }
  },
  'on-change': {
    about: 'on-change sees {old} and {new} and never sets itself off again.',
    pages: [page('bank', { variables: { coins: '10' }, 'on-change': { coins: [say('{old} -> {new}'), set('coins', 'add', '1')] } }, () => [
      element('add', 'button', { actions: [set('coins', 'add', '5'), say('now {coins}')] })])],
    steps: [{ click: 'add' }],
    expect: { events: ['message 10 -> 15', 'message now 16'], variables: { coins: '16' }, page: 'bank', closed: false }
  },
  'triggers': {
    about: 'on-damage, on-world-change and timers with their values.',
    pages: [page('arena', { variables: { ticks: '0' }, 'on-damage': [say('ouch {damage} {cause}')], 'on-world-change': [say('{from} -> {world}')],
      timers: [{ every: '1s', actions: [set('ticks', 'add', '1'), say('tick {ticks}')] }] }, () => [element('info', 'label')])],
    steps: [{ damage: 3.456, cause: 'fall' }, { world: 'world_nether' }, { timer: 0 }, { timer: 0 }],
    expect: { events: ['message ouch 3.46 fall', 'message world -> world_nether', 'message tick 1', 'message tick 2'], variables: { ticks: '2' }, page: 'arena', closed: false }
  },
  'navigation': {
    about: 'Leaving a page runs its on-close, back returns, globals stay, closing runs on-close once.',
    pages: [
      page('home', { variables: { '*visits': '0' }, 'on-open': [set('*visits', 'add', '1'), say('hello home {visits}')], 'on-close': [say('leaving home')] }, () => [
        element('go', 'button', { actions: [{ type: 'redirect', value: 'shop' }, say('never runs')] }),
        element('quit', 'button', { actions: [{ type: 'close' }] })]),
      page('shop', { 'on-open': [say('hello shop {visits}')], 'on-close': [say('leaving shop')] }, () => [
        element('back', 'button', { actions: [{ type: 'back' }] })])],
    steps: [{ click: 'go' }, { click: 'back' }, { click: 'quit' }],
    expect: { events: ['message hello home 1', 'open shop', 'message leaving home', 'message hello shop 1', 'back', 'message leaving shop', 'message hello home 2',
      'close', 'message leaving home'], page: 'home', closed: true }
  },
  'visibility-and-lists': {
    about: 'hidden, toggle, visible-if and list rows with {item} and {index}.',
    pages: [page('kits', { variables: { coins: '9', kits: 'starter,builder,warrior' } }, () => [
      element('panel', 'label', { hidden: true }),
      element('rich', 'label', { 'visible-if': '{coins} > 5' }),
      element('toggle', 'button', { actions: [{ type: 'toggle', elements: ['panel'] }] }),
      element('spend', 'button', { actions: [set('coins', 'set', '3')] }),
      element('list', 'button', { list: { source: 'kits', rows: 3, spacing: 16, 'on-select': [say('kit {item} #{index}')] } }, { y: -.3 })])],
    steps: [{ click: 'toggle' }, { row: ['list', 1] }, { click: 'spend' }],
    expect: { events: ['message kit builder #2'], shown: { panel: true, rich: false }, variables: { coins: '3' }, page: 'kits', closed: false }
  },
  'closing-rules': {
    about: 'On-close cannot navigate or ask: confirm answers no without asking, and a redirect ends the list.',
    pages: [page('bye', { 'on-close': [say('bye'), { type: 'confirm', value: 'Really?', then: [say('yes')], else: [say('no')] }, say('after confirm'),
      { type: 'redirect', value: 'bye' }, say('never')] }, () => [
      element('quit', 'button', { actions: [{ type: 'close' }, say('not after close')] })])],
    steps: [{ answers: ['yes'] }, { click: 'quit' }],
    expect: { events: ['close', 'message bye', 'message no', 'message after confirm'], page: 'bye', closed: true }
  },
  'closing-wait': {
    about: 'A wait inside on-close ends the list: the screen is already gone.',
    pages: [page('wait', { 'on-close': [say('bye'), { type: 'delay', value: '1s' }, say('never')] }, () => [element('quit', 'button', { actions: [{ type: 'close' }] })])],
    steps: [{ click: 'quit' }],
    expect: { events: ['close', 'message bye'], page: 'wait', closed: true }
  },
  'hud-and-toasts': {
    about: 'hud adds or takes HUD pages and toast shows one for a while (tokens resolved); both still run while the page closes.',
    pages: [page('base', { variables: { which: 'vida' }, 'on-close': [{ type: 'toast', value: 'bye', seconds: 'abc' }, say('closed')] }, () => [
      element('show', 'button', { actions: [{ type: 'hud', value: 'hud_{which}' }, { type: 'toast', value: 'aviso', seconds: '90', values: { titulo: 'Hi {player}' } }] }),
      element('hide', 'button', { actions: [{ type: 'hud', page: 'hud_vida', mode: 'remove' }, { type: 'close' }, say('not after close')] })])],
    steps: [{ click: 'show' }, { click: 'hide' }],
    expect: { events: ['hud add hud_vida', 'toast aviso', 'hud remove hud_vida', 'close', 'toast bye', 'message closed'], page: 'base', closed: true }
  },
  'legacy-fallback': {
    about: 'An element with an empty action list falls back to its schema 1-8 action; a cooldown applies to it too.',
    pages: [page('old', null, () => [element('hi', 'button', { actions: [], cooldown: '1s' }, { action: 'message:hi' })])],
    steps: [{ click: 'hi' }, { click: 'hi' }],
    expect: { events: ['legacy message:hi'], page: 'old', closed: false }
  }
};

mkdirSync(out, { recursive: true });
for (const name of readdirSync(out)) if (name.endsWith('.json')) unlinkSync(join(out, name));
for (const [name, value] of Object.entries(cases)) writeFileSync(join(out, `${name}.json`), `${JSON.stringify({ name, ...value }, null, 2)}\n`);
writeFileSync(join(out, 'index.txt'), `${Object.keys(cases).join('\n')}\n`);
console.log(`wrote ${Object.keys(cases).length} cases to ${out}`);
