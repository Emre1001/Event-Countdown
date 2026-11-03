/* Live-Countdown wie in der App: dein Datum, deine Animation. Rechnet wie Countdown.kt
   (Countdown rundet auf, "seit" rundet ab) und wechselt Ziffern einzeln, von rechts verankert. */
(function (global) {
  'use strict';
  var card = document.getElementById('demo-card');
  if (!card) return;
  var dateInput = document.getElementById('demo-date');
  var titleInput = document.getElementById('demo-title');
  var stateEl = document.getElementById('demo-state');
  var nums = {};
  card.querySelectorAll('[data-unit]').forEach(function (u) { nums[u.dataset.unit] = u.querySelector('.num'); });
  var reduce = matchMedia('(prefers-reduced-motion: reduce)');
  var STORE = 'demo';
  var timer = null;
  var past = false;

  function t(key, fallback) {
    if (global.I18n) { var v = I18n.t(key); if (v && v !== key) return v; }
    return fallback;
  }
  function load() { try { return JSON.parse(localStorage.getItem(STORE) || 'null') || {}; } catch (e) { return {}; } }
  function save(o) { try { localStorage.setItem(STORE, JSON.stringify(o)); } catch (e) { /* egal */ } }

  function nextNewYear() { var d = new Date(); return new Date(d.getFullYear() + 1, 0, 1, 0, 0, 0, 0); }
  function toInput(d) {
    function p(n) { return String(n).padStart(2, '0'); }
    return d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate()) + 'T' + p(d.getHours()) + ':' + p(d.getMinutes());
  }
  function target() {
    var v = dateInput.value ? new Date(dateInput.value) : null;
    return v && !isNaN(v) ? v.getTime() : nextNewYear().getTime();
  }

  /* Eine Zahl schreiben: Länge geändert -> neu aufbauen; sonst nur geänderte Ziffern animieren. */
  function write(el, text, animate) {
    var slots = el.children;
    if (slots.length !== text.length) {
      el.textContent = '';
      for (var i = 0; i < text.length; i++) el.appendChild(slot(text[i]));
      return;
    }
    for (var j = 0; j < text.length; j++) {
      var s = slots[j], cur = s.querySelector('.g:not(.out)');
      if (cur && cur.textContent === text[j]) continue;
      if (!animate) { if (cur) cur.textContent = text[j]; continue; }
      var next = document.createElement('span');
      next.className = 'g in';
      next.textContent = text[j];
      if (cur) {
        cur.classList.remove('in');
        cur.classList.add('out');
        (function (c) { setTimeout(function () { c.remove(); }, 900); })(cur);
      }
      s.appendChild(next);
      if (card.dataset.anim === 'melt') {
        s.classList.add('melting');
        (function (x) { clearTimeout(x._m); x._m = setTimeout(function () { x.classList.remove('melting'); }, 840); })(s);
      }
    }
  }
  function slot(ch) {
    var d = document.createElement('span'); d.className = 'd';
    var ph = document.createElement('span'); ph.className = 'ph'; ph.textContent = '0';
    var g = document.createElement('span'); g.className = 'g'; g.textContent = ch;
    d.appendChild(ph); d.appendChild(g);
    return d;
  }

  function render(animate) {
    var now = Date.now(), tgt = target();
    past = tgt < now;
    var secs = past ? Math.floor((now - tgt) / 1000) : Math.floor((tgt - now + 999) / 1000);
    var d = Math.floor(secs / 86400); secs %= 86400;
    var h = Math.floor(secs / 3600); secs %= 3600;
    var m = Math.floor(secs / 60), s = secs % 60;
    var a = animate && !reduce.matches && card.dataset.anim !== 'none';
    write(nums.d, String(d), a);
    write(nums.h, String(h).padStart(2, '0'), a);
    write(nums.m, String(m).padStart(2, '0'), a);
    write(nums.s, String(s).padStart(2, '0'), a);
    stateEl.textContent = past ? t('demo.past', 'Vergangen – jetzt zählt es, seit wann.') : '';
  }

  function loop() {
    render(true);
    clearTimeout(timer);
    timer = setTimeout(loop, 1000 - (Date.now() % 1000) + 8);
  }

  function setAnim(name) {
    card.dataset.anim = name;
    document.querySelectorAll('.chip[data-anim]').forEach(function (b) { b.setAttribute('aria-checked', String(b.dataset.anim === name)); });
  }

  var saved = load();
  dateInput.value = saved.date || toInput(nextNewYear());
  if (saved.title != null) titleInput.value = saved.title;
  setAnim(saved.anim || 'up');

  document.querySelectorAll('.chip[data-anim]').forEach(function (b) {
    b.addEventListener('click', function () {
      setAnim(b.dataset.anim);
      persist();
    });
  });
  // Pfeiltasten in der Radiogruppe
  var group = document.querySelector('.chips');
  if (group) group.addEventListener('keydown', function (e) {
    if (e.key !== 'ArrowRight' && e.key !== 'ArrowLeft') return;
    var list = Array.prototype.slice.call(group.querySelectorAll('.chip'));
    var i = list.indexOf(document.activeElement);
    if (i < 0) return;
    var n = list[(i + (e.key === 'ArrowRight' ? 1 : list.length - 1)) % list.length];
    n.focus(); n.click(); e.preventDefault();
  });
  function persist() { save({ date: dateInput.value, title: titleInput.value, anim: card.dataset.anim }); }
  dateInput.addEventListener('change', function () { persist(); render(true); });
  titleInput.addEventListener('input', persist);

  render(false);
  loop();
  // Hintergrund-Tab: nicht im Leerlauf rechnen, beim Zurückkommen sofort richtig
  document.addEventListener('visibilitychange', function () {
    if (document.hidden) clearTimeout(timer); else { render(false); loop(); }
  });

  global.Demo = { refresh: function () { render(false); } };
})(window);
