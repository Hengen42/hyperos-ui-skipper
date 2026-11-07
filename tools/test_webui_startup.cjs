const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { test } = require('node:test');

const html = fs.readFileSync(path.join(__dirname, '../zygisk-module/webroot/index.html'), 'utf8');
const script = html.match(/<script>([\s\S]*?)<\/script>/)[1];

function page() {
  const elements = new Map();
  function element(id) {
    if (!elements.has(id)) elements.set(id, {
      textContent: '', innerHTML: '', style: {}, dataset: {}, checked: false,
      disabled: false, listeners: {},
      addEventListener(event, handler) { this.listeners[event] = handler; },
      classList: { add() {}, remove() {} },
    });
    return elements.get(id);
  }
  const radios = ['1', '2'].map(value => Object.assign(element('radio' + value), { value, disabled: true }));
  const frames = [];
  const timers = new Map();
  const calls = [];
  let timerId = 0;
  const context = {
    document: { getElementById: element, querySelectorAll: () => radios, documentElement: {} },
    navigator: { language: 'tr' },
    localStorage: { getItem: () => null, setItem() {} },
    alert() {},
    requestAnimationFrame: callback => frames.push(callback),
    setTimeout(callback, delay) { timers.set(++timerId, { callback, delay }); return timerId; },
    clearTimeout: id => timers.delete(id),
    ksu: { exec(...args) { calls.push(args); } },
  };
  context.window = context;
  vm.runInNewContext(script, context);
  return {
    context, calls, radios, element,
    paint() {
      const frame = frames.shift();
      if (frame) frame();
      for (const [id, timer] of [...timers]) if (timer.delay === 0) {
        timers.delete(id);
        timer.callback();
      }
    },
    reply(index, output) { context[calls[index][2]](0, output, ''); },
    timeout() {
      const [id, timer] = [...timers].find(([, timer]) => timer.delay === 10000);
      timers.delete(id);
      timer.callback();
    },
  };
}

test('first paint does no root work and makes no unverified health claims', () => {
  const p = page();
  assert.equal(p.calls.length, 0);
  assert.ok(!/[✅❌]/u.test(p.element('checks').innerHTML));
  assert.equal(p.element('reinstall').style.display, 'none');
  assert.ok(p.radios.every(radio => radio.disabled));
  p.context.selectOption(2);
  assert.equal(p.calls.length, 0);
});

test('saved Mode 2 becomes ready before the full check, without a separate settings read', () => {
  const p = page();
  p.paint();
  assert.equal(p.calls.length, 1);
  assert.match(p.calls[0][0], /status\.sh --fast/);
  assert.equal(p.calls[0].length, 3);
  p.reply(0, 'state=checking\nmode=2\napk=1\ndisabled=0\n');
  assert.equal(p.radios[1].checked, true);
  assert.equal(p.element('consent').checked, true);
  assert.ok(p.radios.every(radio => !radio.disabled));
  assert.equal(p.calls.length, 1); // fast result gets a paint before the next root call
  p.paint();
  assert.equal(p.calls.length, 2);
  assert.match(p.calls[1][0], /status\.sh --prop/);
  p.reply(1, 'state=ok\nmode=2\nhook=1\napk=1\n');
  assert.match(p.element('banner').textContent, /^✅/u);
  assert.equal(p.element('refresh').disabled, false);
});

test('failed checks never invent success or enable unknown mode settings', () => {
  const p = page();
  p.paint();
  p.timeout();
  assert.ok(p.radios.every(radio => radio.disabled));
  assert.equal(p.element('refresh').disabled, false);
  assert.match(p.element('banner').textContent, /^❌/u);

  const q = page();
  q.paint();
  q.reply(0, 'state=checking\nmode=1\napk=1\n');
  q.paint();
  q.reply(1, 'state=no_hook\nmode=1\nhook=0\napk=1\n');
  assert.match(q.element('banner').textContent, /^❌/u);
});
