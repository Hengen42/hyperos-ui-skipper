// Run with node --test tools/test_boot_status.cjs. On Windows, set TEST_SHELL
// to Git's sh.exe; its sibling Unix utilities are added to PATH automatically.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const { test } = require('node:test');

const shell = process.env.TEST_SHELL || 'sh';
const posix = value => value.replaceAll('\\', '/').replace(/^([A-Za-z]):/, (_, drive) => '/' + drive.toLowerCase());

function fixture(t, { readyAt = 0, apk = true, disabled = false, removeAt = 10000, disableAt = 10000, unlockAt = Infinity, userState = 'RUNNING_UNLOCKED', waitUserState = userState, providerStarts = true } = {}) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'uiskipper-'));
  t.after(() => fs.rmSync(root, { recursive: true, force: true }));
  const write = (name, content) => {
    fs.mkdirSync(path.dirname(path.join(root, name)), { recursive: true });
    fs.writeFileSync(path.join(root, name), content);
  };
  const mod = 'data/adb/modules/hyperos_ui_skipper';
  write(`${mod}/module.prop`, 'id=hyperos_ui_skipper\ndescription=old\n');
  if (disabled) write(`${mod}/disable`, '');
  write('clock', '0\n');
  write('calls', '');
  // A stale success from a dead process must never count as current proof.
  write('data/adb/vector/log/modules_test.log', ': 99: [HyperOSUiSkipper] intercept timer hooked successfully\n');
  write('data/adb/modules/zygisk_vector/cli', `echo cli >> "$TEST_ROOT/calls"
case "$1" in
  modules) echo 'dev.hengen.uiskipper enabled' ;;
  scope) echo 'com.miui.securitycenter/0' ;;
esac
`);
  write('mocks.sh', `
getprop() { echo 1; }
pm() { ${apk ? "echo 'package:/data/app/hook.apk'" : 'return 1'}; }
settings() { echo 1; }
cmd() {
  echo user-state >> "$TEST_ROOT/calls"
  read clock < "$TEST_ROOT/clock"
  if [ "$clock" -ge ${Number.isFinite(unlockAt) ? unlockAt : 100000} ]; then
    echo RUNNING_UNLOCKED
  elif [ -f "$TEST_ROOT/user-checked" ]; then
    echo '${waitUserState}'
  else
    touch "$TEST_ROOT/user-checked"
    echo '${userState}'
  fi
}
pidof() {
  case "$1" in
    vectord) echo 42 ;;
    com.miui.securitycenter) [ -f "$TEST_ROOT/target" ] && echo 123 ;;
    *) return 1 ;;
  esac
}
publish_hook() {
  read clock < "$TEST_ROOT/clock"
  if [ "$clock" -ge ${readyAt} ] && [ -f "$TEST_ROOT/target" ]; then
    echo ': 123: [HyperOSUiSkipper] intercept timer hooked successfully' > "$TEST_ROOT/data/adb/vector/log/modules_test.log"
  fi
}
sleep() {
  read clock < "$TEST_ROOT/clock"
  echo "$((clock + $1))" > "$TEST_ROOT/clock"
  if [ "$((clock + $1))" -ge ${removeAt} ]; then
    touch "$TEST_ROOT/${mod}/remove"
  fi
  if [ "$((clock + $1))" -ge ${disableAt} ]; then
    touch "$TEST_ROOT/${mod}/disable"
  fi
  publish_hook
}
content() { echo content >> "$TEST_ROOT/calls"; ${providerStarts ? 'touch "$TEST_ROOT/target"; publish_hook' : 'return 1'}; }
timeout() { shift; "$@"; }
`);
  for (const name of ['service.sh', 'status.sh']) {
    const source = fs.readFileSync(path.join(__dirname, '../zygisk-module', name), 'utf8')
      .replaceAll('\r\n', '\n').replaceAll('/data/adb/', `${posix(root)}/data/adb/`);
    write(`${mod}/${name}`, source.replace('\n', `\n. "$TEST_ROOT/mocks.sh"\n${name === 'status.sh' ? '[ "$1" != --retry-prop ] && echo status >> "$TEST_ROOT/calls"\n' : ''}`));
  }
  const env = { ...process.env, TEST_ROOT: posix(root), BOOTCLASSPATH: 'fixture', ANDROID_I18N_ROOT: 'fixture' };
  if (process.env.TEST_SHELL) env.PATH = path.dirname(shell) + path.delimiter + env.PATH;
  const run = (name, args = []) => {
    const result = spawnSync(shell, [posix(path.join(root, mod, name)), ...args], {
      env, encoding: 'utf8', timeout: 20000,
    });
    assert.ifError(result.error);
    assert.equal(result.status, 0, result.stderr);
    return result.stdout;
  };
  return {
    run,
    description: () => fs.readFileSync(path.join(root, mod, 'module.prop'), 'utf8'),
    probes: () => fs.readFileSync(path.join(root, 'calls'), 'utf8').split('\n').filter(line => line === 'status').length,
    elapsed: () => Number(fs.readFileSync(path.join(root, 'clock'), 'utf8')),
    acquisitions: () => fs.readFileSync(path.join(root, 'calls'), 'utf8').split('\n').filter(line => line === 'content').length,
    cliCalls: () => fs.readFileSync(path.join(root, 'calls'), 'utf8').split('\n').filter(line => line === 'cli').length,
  };
}

test('boot automatically refreshes once after first unlock, without health polling while locked', t => {
  const f = fixture(t, { userState: 'RUNNING_LOCKED', unlockAt: 15 });
  f.run('service.sh');
  assert.match(f.description(), /description=✅ Active/u);
  assert.equal(f.probes(), 2, 'one initial probe and one unlock follow-up');
  assert.equal(f.cliCalls(), 4, 'only the two health probes may run Java CLI queries');
  assert.equal(f.acquisitions(), 1, 'only acquire provider after unlock');
  assert.equal(f.elapsed(), 15, 'exit immediately after the follow-up');
});

test('unlock in progress keeps waiting until the user is fully unlocked', t => {
  const f = fixture(t, { userState: 'RUNNING_LOCKED', waitUserState: 'RUNNING_UNLOCKING', unlockAt: 10 });
  f.run('service.sh');
  assert.match(f.description(), /description=✅ Active/u);
  assert.equal(f.probes(), 2);
  assert.equal(f.elapsed(), 10);
});

for (const state of ['', 'UNKNOWN', 'STOPPING']) {
  test(`unexpected user state '${state}' exits without assuming unlock`, t => {
    const f = fixture(t, { userState: 'RUNNING_LOCKED', waitUserState: state });
    f.run('service.sh');
    assert.match(f.description(), /description=⏳ Unlock device/u);
    assert.equal(f.probes(), 1);
    assert.equal(f.acquisitions(), 0);
    assert.equal(f.elapsed(), 0);
  });
}

for (const marker of ['disable', 'remove']) {
  test(`${marker} during first-unlock wait aborts without another health probe`, t => {
    const f = fixture(t, { userState: 'RUNNING_LOCKED', unlockAt: 15, [`${marker}At`]: 5 });
    f.run('service.sh');
    assert.match(f.description(), /description=⏳ Unlock device/u);
    assert.equal(f.probes(), 1);
    assert.equal(f.cliCalls(), 2);
    assert.equal(f.acquisitions(), 0);
    assert.equal(f.elapsed(), 5);
  });
}

test('boot before first unlock stays unverified without provider probes or retries', t => {
  const f = fixture(t, { userState: 'RUNNING_LOCKED', providerStarts: false });
  const status = f.run('status.sh', ['--retry-prop']);
  assert.match(status, /^state=waiting_unlock$/m);
  assert.match(status, /^hook=unknown$/m);
  assert.match(f.description(), /description=⏳ Unlock device/u);
  assert.equal(f.acquisitions(), 0);
  assert.equal(f.probes(), 1);
  assert.equal(f.elapsed(), 0);
});

test('unavailable provider and absent target are pending, not failed injection', t => {
  const f = fixture(t, { providerStarts: false });
  const status = f.run('status.sh', ['--retry-prop']);
  assert.match(status, /^state=waiting_target$/m);
  assert.match(status, /^hook=unknown$/m);
  assert.match(f.description(), /description=⏳ SecurityCenter is not running/u);
  assert.equal(f.acquisitions(), 1);
  assert.equal(f.probes(), 1);
  assert.equal(f.elapsed(), 0);
});

test('boot reconciles a delayed hook without opening WebUI', t => {
  const f = fixture(t, { readyAt: 12 });
  f.run('service.sh');
  assert.match(f.description(), /description=✅ Active \(Vector\) — Skip Countdown Only/u);
  assert.ok(f.probes() > 1, 'must recheck after the initial verification window');
});

test('ready hook exits after one boot probe', t => {
  const f = fixture(t);
  f.run('service.sh');
  assert.match(f.description(), /description=✅ Active/u);
  assert.equal(f.probes(), 1);
  assert.equal(f.elapsed(), 0, 'ready system must not have an unconditional boot delay');
});

test('install refresh reconciles a delayed hook without a boot or WebUI', t => {
  const f = fixture(t, { readyAt: 6 });
  f.run('status.sh', ['--retry-prop']);
  assert.match(f.description(), /description=✅ Active/u);
  assert.ok(f.probes() > 1);
});

test('interactive refresh retains a single probe and reports missing proof', t => {
  const f = fixture(t, { readyAt: 6 });
  const status = f.run('status.sh', ['--prop']);
  assert.match(status, /^state=no_hook$/m);
  assert.match(status, /^hook=0$/m);
  assert.equal(f.probes(), 1);
});

test('missing hook stays failed, ignores stale PID logs, and stops retrying', t => {
  const f = fixture(t, { readyAt: 10000 });
  f.run('service.sh');
  assert.match(f.description(), /description=❌ Automatic hook verification failed/u);
  assert.equal(f.probes(), 3);
  const status = f.run('status.sh', ['--prop']);
  assert.match(status, /^state=no_hook$/m);
  assert.match(status, /^hook=0$/m);
});

test('missing APK remains a distinct failure and is not retried', t => {
  const f = fixture(t, { apk: false });
  f.run('service.sh');
  assert.match(f.description(), /description=❌ Hook APK is not installed/u);
  assert.equal(f.probes(), 1);
});

test('disabled module is not probed by the boot retry', t => {
  const f = fixture(t, { disabled: true });
  f.run('service.sh');
  assert.equal(f.probes(), 0);
  assert.match(f.description(), /description=old/);
});

test('removal during the retry interval stops further probes', t => {
  const f = fixture(t, { readyAt: 10000, removeAt: 5 });
  f.run('service.sh');
  assert.equal(f.probes(), 1);
  assert.match(f.description(), /description=❌ Automatic hook verification failed/u);
});
