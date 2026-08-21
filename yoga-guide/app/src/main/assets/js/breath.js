/*
 * The breath pacer.
 *
 * An expanding circle with a per-phase countdown. It drives the pranayama
 * steps inside the session player and also runs standalone from the home
 * screen, which is why it owns its own DOM rather than being drawn by the
 * player.
 *
 * The pacer always finishes on a completed exhale. Cutting a practice off
 * mid-inhale because a timer expired is jarring in a way that matters here, so
 * the duration is treated as "at least this long, then finish the round".
 */
(function (YG) {
  'use strict';

  var MIN_SCALE = 0.42;
  var MAX_SCALE = 1;

  var LABELS = {
    in: 'Breathe in',
    hold1: 'Hold',
    out: 'Breathe out',
    hold2: 'Hold'
  };

  /** Expands a pace object into the ordered, non-empty phases of one cycle. */
  function phasesFor(pace) {
    var order = ['in', 'hold1', 'out', 'hold2'];
    var out = [];
    for (var i = 0; i < order.length; i++) {
      var k = order[i];
      var secs = pace[k] || 0;
      if (secs > 0) out.push({ key: k, secs: secs });
    }
    return out;
  }

  function scaleAt(key, t) {
    switch (key) {
      case 'in': return MIN_SCALE + (MAX_SCALE - MIN_SCALE) * t;
      case 'hold1': return MAX_SCALE;
      case 'out': return MAX_SCALE - (MAX_SCALE - MIN_SCALE) * t;
      default: return MIN_SCALE;
    }
  }

  /**
   * Alternate-nostril cueing. One full round of Anulom Vilom is two breath
   * cycles - in left / out right, then in right / out left - so the side
   * depends on the parity of the cycle, not of the phase.
   */
  function sideFor(key, cycle) {
    var evenCycle = cycle % 2 === 0;
    if (key === 'in') return evenCycle ? 'left' : 'right';
    if (key === 'out') return evenCycle ? 'right' : 'left';
    return null;
  }

  YG.Breath = {

    /**
     * @param el       container element, emptied and taken over
     * @param pace     {in, hold1, out, hold2, alternate}
     * @param opts     {duration, onDone, announce}
     * @returns        {start, pause, resume, stop, isRunning}
     */
    create: function (el, pace, opts) {
      opts = opts || {};
      var phases = phasesFor(pace);
      if (!phases.length) phases = [{ key: 'in', secs: 4 }, { key: 'out', secs: 6 }];

      var duration = opts.duration || 0;
      var announce = opts.announce !== false;

      el.innerHTML =
        '<div class="orb-wrap">' +
          '<div class="orb">' +
            '<i class="halo"></i>' +
            '<div class="fill"></div>' +
            '<div class="count">0</div>' +
          '</div>' +
          '<div>' +
            '<div class="breath-phase"></div>' +
            '<div class="breath-sub"></div>' +
          '</div>' +
        '</div>';

      var fill = el.querySelector('.fill');
      var count = el.querySelector('.count');
      var phaseEl = el.querySelector('.breath-phase');
      var subEl = el.querySelector('.breath-sub');

      var raf = 0;
      var running = false;
      var phaseIndex = -1;
      var cycle = 0;
      var phaseStart = 0;
      var elapsedTotal = 0;
      var lastTick = 0;
      var lastShownCount = -1;
      var pausedPhaseElapsed = 0;

      function enterPhase(index, now) {
        // Wrapping past the last phase completes one breath cycle.
        if (index >= phases.length) {
          index = 0;
          cycle++;
          // Finish here if we have served the requested duration: this point is
          // always the end of an exhale (or of the pause after it).
          if (duration && elapsedTotal >= duration) { finish(); return; }
        }
        phaseIndex = index;
        phaseStart = now;
        lastShownCount = -1;
        pausedPhaseElapsed = 0;

        var ph = phases[phaseIndex];
        var side = pace.alternate ? sideFor(ph.key, cycle) : null;
        var label = LABELS[ph.key];

        phaseEl.textContent = side ? label + ' — ' + side + ' nostril' : label;
        subEl.textContent = side
          ? 'Close the other nostril'
          : (ph.key === 'in' || ph.key === 'out' ? 'Through the nose' : 'Stay easy - do not strain');

        YG.Audio.play('phase');
        YG.Native.vibrate(ph.key === 'hold1' || ph.key === 'hold2' ? 12 : 22);
        if (announce) YG.Native.speak(phaseEl.textContent);
      }

      function finish() {
        stop();
        if (opts.onDone) opts.onDone();
      }

      function frame(now) {
        if (!running) return;
        raf = requestAnimationFrame(frame);

        var dt = (now - lastTick) / 1000;
        lastTick = now;
        // A backgrounded WebView can hand back a huge dt on resume. Clamping it
        // means the pacer picks up where it left off instead of fast-forwarding
        // through four phases at once.
        if (dt > 0.5) dt = 0.5;
        elapsedTotal += dt;

        var ph = phases[phaseIndex];
        var inPhase = (now - phaseStart) / 1000;
        if (inPhase >= ph.secs) {
          enterPhase(phaseIndex + 1, now);
          return;
        }

        var t = inPhase / ph.secs;
        fill.style.transform = 'scale(' + scaleAt(ph.key, t).toFixed(4) + ')';

        var remaining = Math.ceil(ph.secs - inPhase);
        if (remaining !== lastShownCount) {
          lastShownCount = remaining;
          count.textContent = String(remaining);
        }
      }

      function start() {
        if (running) return;
        running = true;
        var now = performance.now();
        lastTick = now;
        if (phaseIndex < 0) {
          cycle = 0;
          elapsedTotal = 0;
          enterPhase(0, now);
        } else {
          // Resume mid-phase rather than restarting it - a pause three seconds
          // into a seven-count hold should not hand back a fresh seven.
          phaseStart = now - pausedPhaseElapsed;
        }
        raf = requestAnimationFrame(frame);
      }

      function pause() {
        if (!running) return;
        running = false;
        pausedPhaseElapsed = performance.now() - phaseStart;
        cancelAnimationFrame(raf);
      }

      function stop() {
        running = false;
        cancelAnimationFrame(raf);
        phaseIndex = -1;
      }

      return {
        start: start,
        pause: pause,
        resume: start,
        stop: stop,
        isRunning: function () { return running; },
        elapsed: function () { return elapsedTotal; }
      };
    }
  };
})(window.YG = window.YG || {});
