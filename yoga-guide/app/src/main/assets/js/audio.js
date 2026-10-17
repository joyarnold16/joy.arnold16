/*
 * Transition chimes, synthesised rather than bundled.
 *
 * A struck-bell tone is two decaying sine partials, which is a handful of
 * oscillator nodes - so there is no reason to ship audio files, pay for their
 * size, or keep the repository binary for them. It also means the chime can be
 * tuned by editing numbers instead of re-recording anything.
 */
(function (YG) {
  'use strict';

  var ctx = null;

  function context() {
    if (ctx) return ctx;
    var C = window.AudioContext || window.webkitAudioContext;
    if (!C) return null;
    try { ctx = new C(); } catch (e) { ctx = null; }
    return ctx;
  }

  /** One decaying partial. */
  function partial(c, freq, gain, at, dur) {
    var osc = c.createOscillator();
    var amp = c.createGain();
    osc.type = 'sine';
    osc.frequency.setValueAtTime(freq, at);
    amp.gain.setValueAtTime(0, at);
    // A 12ms attack instead of an instant one: a hard start on a sine is an
    // audible click, and a click is the opposite of what this app is for.
    amp.gain.linearRampToValueAtTime(gain, at + 0.012);
    amp.gain.exponentialRampToValueAtTime(0.0001, at + dur);
    osc.connect(amp);
    amp.connect(c.destination);
    osc.start(at);
    osc.stop(at + dur + 0.05);
  }

  var TONES = {
    // Pose change: a clear mid bell.
    step:  { f: 660, dur: 1.5, gain: 0.16 },
    // Breath phase: quieter and shorter, because it repeats every few seconds.
    phase: { f: 880, dur: 0.5, gain: 0.07 },
    // Session start and end: lower and longer, so they read as bookends.
    start: { f: 528, dur: 1.9, gain: 0.16 },
    end:   { f: 396, dur: 2.6, gain: 0.18 }
  };

  YG.Audio = {
    /** Called from the first user gesture - autoplay policy needs that. */
    unlock: function () {
      var c = context();
      if (c && c.state === 'suspended') c.resume();
    },

    play: function (name) {
      if (!YG.Store.profile().chime) return;
      var c = context();
      var t = TONES[name];
      if (!c || !t) return;
      if (c.state === 'suspended') c.resume();
      var at = c.currentTime + 0.01;
      partial(c, t.f, t.gain, at, t.dur);
      // The second partial an octave and a fifth up is what makes it read as a
      // struck bell rather than a test tone.
      partial(c, t.f * 3, t.gain * 0.35, at, t.dur * 0.55);
    }
  };
})(window.YG = window.YG || {});
