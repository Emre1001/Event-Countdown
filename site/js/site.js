(function () {
  'use strict';

  /* Sprache + Theme */
  I18n.init();
  initLanguagePicker(document.getElementById('lang-trigger'));
  function labels() { return { system: I18n.t('theme.system'), light: I18n.t('theme.light'), dark: I18n.t('theme.dark') }; }
  SiteTheme.init({ labels: labels(), title: I18n.t('theme.label') });
  function syncMeta() {
    var title = I18n.t('meta.title');
    if (title && title !== 'meta.title' && !document.body.dataset.page) document.title = title;
    var d = document.querySelector('meta[name="description"]');
    var desc = I18n.t('meta.description');
    if (d && desc !== 'meta.description' && !document.body.dataset.page) d.content = desc;
    SiteTheme.relabel(labels(), I18n.t('theme.label'));
    if (window.DownloadKit) DownloadKit.refresh();
    if (window.Demo) Demo.refresh();
  }
  I18n.onChange(syncMeta); syncMeta();

  /* Kopfzeile bekommt beim Scrollen ihre Haarlinie */
  var header = document.querySelector('.site-header');
  function stuck() { header.classList.toggle('is-stuck', scrollY > 8); }
  addEventListener('scroll', stuck, { passive: true }); stuck();

  if (!window.DownloadKit) return; // Unterseiten (Impressum, Datenschutz)

  /* Downloads: echte Daten von GitHub, die Seite funktioniert auch ohne */
  DownloadKit.init({ repo: 'Emre1001/Event-Countdown', t: function (k, p) { return I18n.t(k, p); }, lang: I18n.current() });
  /* Am Computer gibt es keine Datei für das eigene System: dann direkt die APK anbieten (mit QR-Code daneben). */
  DownloadKit.onChange(function (m) {
    if (m.state !== 'ready' || m.primary || m.os === 'ios') return; // iPhone: ehrlicher Hinweis statt nutzloser APK
    var apk = m.files && m.files.android && m.files.android[0];
    if (!apk) return;
    document.querySelectorAll('[data-dl-primary]').forEach(function (a) {
      a.href = apk.url;
      var label = a.querySelector('[data-dl-label]'), meta = a.querySelector('[data-dl-meta]');
      if (label) label.textContent = I18n.t('download.apk');
      var mb = apk.size ? (apk.size / 1048576).toLocaleString(I18n.current(), { maximumFractionDigits: 1 }) + ' MB' : '';
      if (meta) meta.textContent = [m.release && m.release.tag_name, mb].filter(Boolean).join(' · ');
    });
    document.querySelectorAll('[data-dl-hint]').forEach(function (el) { el.textContent = m.os === 'ios' ? I18n.t('download.ios') : ''; });
  });
  I18n.onChange(function () { DownloadKit.refresh(); });

  Reveal.init();
  Story.init(document.querySelector('[data-story]'));
})();
