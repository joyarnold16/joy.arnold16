/*
 * Persistence.
 *
 * Everything lives in localStorage on the device and nothing is ever sent
 * anywhere - the app has no INTERNET permission, so that is enforced by the
 * manifest rather than by good intentions. The health profile in particular is
 * the sort of data that should never leave a phone.
 *
 * Keys carry a version suffix so a future schema change can migrate rather than
 * silently misread old data.
 */
(function (YG) {
  'use strict';

  var K_PROFILE = 'yg.profile.v1';
  var K_HISTORY = 'yg.history.v1';
  var DISCLAIMER_VERSION = 1;

  function defaults() {
    return {
      onboarded: false,
      disclaimer: 0,
      level: 1,
      flags: {},
      trimester: 2,
      conditions: [],
      length: 'medium',
      reminder: { on: false, hour: 7, minute: 0 },
      voice: true,
      chime: true,
      haptics: true
    };
  }

  function read(key, fallback) {
    try {
      var raw = localStorage.getItem(key);
      if (!raw) return fallback;
      var parsed = JSON.parse(raw);
      return parsed == null ? fallback : parsed;
    } catch (e) {
      // Corrupt or unparseable storage should reset that key, not brick the app
      // behind an exception on every launch.
      return fallback;
    }
  }

  function write(key, value) {
    try {
      localStorage.setItem(key, JSON.stringify(value));
      return true;
    } catch (e) {
      return false;
    }
  }

  var profile = null;
  var history = null;

  var Store = {
    DISCLAIMER_VERSION: DISCLAIMER_VERSION,

    profile: function () {
      if (!profile) {
        var d = defaults();
        var saved = read(K_PROFILE, {});
        for (var k in saved) if (Object.prototype.hasOwnProperty.call(saved, k)) d[k] = saved[k];
        // Nested objects need their own merge or a profile saved before a new
        // sub-field existed would come back missing it.
        d.flags = saved.flags || {};
        d.reminder = Object.assign({ on: false, hour: 7, minute: 0 }, saved.reminder || {});
        profile = d;
      }
      return profile;
    },

    saveProfile: function (patch) {
      var p = Store.profile();
      if (patch) for (var k in patch) if (Object.prototype.hasOwnProperty.call(patch, k)) p[k] = patch[k];
      write(K_PROFILE, p);
      return p;
    },

    setFlag: function (id, on) {
      var p = Store.profile();
      if (on) p.flags[id] = true; else delete p.flags[id];
      write(K_PROFILE, p);
      return p;
    },

    hasFlag: function (id) {
      return !!Store.profile().flags[id];
    },

    activeFlags: function () {
      return Object.keys(Store.profile().flags);
    },

    disclaimerAccepted: function () {
      return Store.profile().disclaimer >= DISCLAIMER_VERSION;
    },

    acceptDisclaimer: function () {
      return Store.saveProfile({ disclaimer: DISCLAIMER_VERSION });
    },

    /* ------------------------------------------------------------ history */

    history: function () {
      if (!history) {
        var h = read(K_HISTORY, []);
        history = Array.isArray(h) ? h : [];
      }
      return history;
    },

    /**
     * @param entry {sessionId, conditionId, seconds, completed, before, after}
     */
    logSession: function (entry) {
      var h = Store.history();
      h.push({
        ts: Date.now(),
        s: entry.sessionId || '',
        c: entry.conditionId || '',
        sec: Math.round(entry.seconds || 0),
        done: !!entry.completed,
        b: entry.before == null ? null : entry.before,
        a: entry.after == null ? null : entry.after
      });
      // 400 entries is over a year of daily practice. Trimming keeps the
      // localStorage quota comfortable without the user ever noticing.
      if (h.length > 400) h.splice(0, h.length - 400);
      write(K_HISTORY, h);
      return h;
    },

    clearHistory: function () {
      history = [];
      write(K_HISTORY, history);
    },

    resetAll: function () {
      profile = null;
      history = null;
      try {
        localStorage.removeItem(K_PROFILE);
        localStorage.removeItem(K_HISTORY);
      } catch (e) { /* nothing useful to do */ }
    },

    /* ------------------------------------------------------------- derived */

    dayKey: function (ts) {
      var d = new Date(ts);
      return d.getFullYear() * 10000 + (d.getMonth() + 1) * 100 + d.getDate();
    },

    /** Distinct days practised, most recent first. */
    practiceDays: function () {
      var seen = {};
      var out = [];
      var h = Store.history();
      for (var i = h.length - 1; i >= 0; i--) {
        var k = Store.dayKey(h[i].ts);
        if (!seen[k]) { seen[k] = true; out.push(k); }
      }
      return out;
    },

    /**
     * Consecutive days up to today. Practising yesterday but not yet today
     * still counts - the streak only breaks once a whole day has been missed,
     * otherwise every streak would read zero until the evening.
     */
    streak: function () {
      var days = Store.practiceDays();
      if (!days.length) return 0;
      var today = new Date();
      today.setHours(0, 0, 0, 0);
      var cursor = today.getTime();
      var todayKey = Store.dayKey(cursor);
      var yesterdayKey = Store.dayKey(cursor - 86400000);

      if (days[0] !== todayKey && days[0] !== yesterdayKey) return 0;
      if (days[0] === yesterdayKey) cursor -= 86400000;

      var n = 0;
      for (var i = 0; i < days.length; i++) {
        if (days[i] === Store.dayKey(cursor)) { n++; cursor -= 86400000; } else break;
      }
      return n;
    },

    totalMinutes: function () {
      var h = Store.history();
      var s = 0;
      for (var i = 0; i < h.length; i++) s += h[i].sec;
      return Math.round(s / 60);
    },

    sessionCount: function () {
      return Store.history().length;
    },

    /** Last `n` days as {key, count, minutes}, oldest first. For the heatmap. */
    recentDays: function (n) {
      var byDay = {};
      var h = Store.history();
      for (var i = 0; i < h.length; i++) {
        var k = Store.dayKey(h[i].ts);
        if (!byDay[k]) byDay[k] = { count: 0, sec: 0 };
        byDay[k].count++;
        byDay[k].sec += h[i].sec;
      }
      var out = [];
      var d = new Date();
      d.setHours(0, 0, 0, 0);
      for (var j = n - 1; j >= 0; j--) {
        var day = new Date(d.getTime() - j * 86400000);
        var key = Store.dayKey(day.getTime());
        var e = byDay[key];
        out.push({
          key: key,
          date: day,
          count: e ? e.count : 0,
          minutes: e ? Math.round(e.sec / 60) : 0
        });
      }
      return out;
    },

    /** Symptom ratings for a condition, oldest first, only where both ends exist. */
    symptomTrend: function (conditionId, limit) {
      var h = Store.history();
      var out = [];
      for (var i = 0; i < h.length; i++) {
        if (h[i].c !== conditionId) continue;
        if (h[i].b == null || h[i].a == null) continue;
        out.push({ ts: h[i].ts, before: h[i].b, after: h[i].a });
      }
      if (limit && out.length > limit) out = out.slice(out.length - limit);
      return out;
    }
  };

  YG.Store = Store;
})(window.YG = window.YG || {});
