/* Story: a pinned stage whose content changes while the visitor scrolls, the classic "show the product, one step at a time".
   The section is N screens tall, the stage is position: sticky (see .story in site-base.css).

   <section class="story" data-story data-steps="3">
     <div class="story__stage"> ... one element per step with data-step-view="0|1|2" ... </div>
     <ol class="story__copy"> <li data-step-copy="0">...</li> ... </ol>
   </section>
   Story.init(el, { onStep(i, prev, el), onProgress(p, el) })

   Sets  el.dataset.step (active index)  and  --story-p (0..1 over the whole section) on the element.
   Below 720px (or with reduced motion) the pinned behaviour is switched off in CSS and all steps are stacked, so nothing depends on JS there:
   this script then does nothing. Keyboard and screen reader users get the full text in reading order either way. */
(function (global) {
  'use strict';
  function init(el, o) {
    o = o || {};
    var steps = Number(el.dataset.steps || el.querySelectorAll('[data-step-view]').length || 1), cur = -1, ticking = false;
    var mq = matchMedia('(max-width: 719px), (prefers-reduced-motion: reduce)');
    function calc() {
      ticking = false;
      if (mq.matches) { el.dataset.pinned = 'false'; return; }
      el.dataset.pinned = 'true';
      var r = el.getBoundingClientRect(), total = r.height - innerHeight;
      var p = total > 0 ? Math.min(1, Math.max(0, -r.top / total)) : 0;
      el.style.setProperty('--story-p', p.toFixed(4));
      var i = Math.min(steps - 1, Math.floor(p * steps * 0.999));
      if (i !== cur) { var prev = cur; cur = i; el.dataset.step = String(i); if (o.onStep) o.onStep(i, prev, el); }
      if (o.onProgress) o.onProgress(p, el);
    }
    function on() { if (!ticking) { ticking = true; requestAnimationFrame(calc); } }
    addEventListener('scroll', on, { passive: true }); addEventListener('resize', on, { passive: true });
    mq.addEventListener('change', on);
    calc();
    return { refresh: calc };
  }
  global.Story = { init: init };
})(window);
