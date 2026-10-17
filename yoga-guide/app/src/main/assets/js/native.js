/*
 * The bridge to the Android layer, with a web fallback for every call.
 *
 * The fallbacks are not decoration: being able to open assets/index.html in a
 * desktop browser is what makes this app testable without a device, and the
 * whole UI works there apart from the reminder.
 */
(function (YG) {
  'use strict';

  var A = window.Android || null;
  var hasNative = !!(A && typeof A.speak === 'function');

  /* Web Speech is the fallback only. See the note in NativeBridge.java for why
     the platform engine is preferred when it is there. */
  var synth = window.speechSynthesis || null;
  var wakeLock = null;

  function safe(fn) {
    try { return fn(); } catch (e) { return undefined; }
  }

  var Native = {
    hasNative: hasNative,

    speak: function (text) {
      if (!text) return;
      if (!YG.Store.profile().voice) return;
      if (hasNative) { safe(function () { A.speak(text); }); return; }
      if (!synth) return;
      safe(function () {
        synth.cancel();
        var u = new SpeechSynthesisUtterance(text);
        u.rate = 0.94;
        u.pitch = 1;
        synth.speak(u);
      });
    },

    stopSpeaking: function () {
      if (hasNative) { safe(function () { A.stopSpeaking(); }); return; }
      if (synth) safe(function () { synth.cancel(); });
    },

    vibrate: function (ms) {
      if (!YG.Store.profile().haptics) return;
      if (hasNative && typeof A.vibrate === 'function') {
        safe(function () { A.vibrate(ms | 0); });
        return;
      }
      if (navigator.vibrate) safe(function () { navigator.vibrate(ms | 0); });
    },

    /**
     * Held only while a session is running. The native flag is the reliable
     * one; the Screen Wake Lock API is the browser fallback and is dropped
     * automatically when the page is hidden, which is exactly what we want.
     */
    keepAwake: function (on) {
      if (hasNative && typeof A.setKeepAwake === 'function') {
        safe(function () { A.setKeepAwake(!!on); });
        return;
      }
      if (!navigator.wakeLock) return;
      if (on) {
        safe(function () {
          navigator.wakeLock.request('screen').then(function (l) { wakeLock = l; },
                                                    function () { /* denied */ });
        });
      } else if (wakeLock) {
        safe(function () { wakeLock.release(); });
        wakeLock = null;
      }
    },

    immersive: function (on) {
      if (hasNative && typeof A.setImmersive === 'function') {
        safe(function () { A.setImmersive(!!on); });
      }
    },

    notificationsAllowed: function () {
      if (hasNative && typeof A.notificationsAllowed === 'function') {
        return !!safe(function () { return A.notificationsAllowed(); });
      }
      return false;
    },

    requestNotifications: function () {
      if (hasNative && typeof A.requestNotifications === 'function') {
        safe(function () { A.requestNotifications(); });
      }
    },

    scheduleReminder: function (hour, minute) {
      if (hasNative && typeof A.scheduleReminder === 'function') {
        safe(function () { A.scheduleReminder(hour | 0, minute | 0); });
      }
    },

    cancelReminder: function () {
      if (hasNative && typeof A.cancelReminder === 'function') {
        safe(function () { A.cancelReminder(); });
      }
    }
  };

  YG.Native = Native;
})(window.YG = window.YG || {});
