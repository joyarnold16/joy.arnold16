/*
 * Screens and routing.
 *
 * A plain string-rendering UI with a manual back stack. No framework: the whole
 * app is four tabs and a handful of detail screens, and a router that Android's
 * hardware back button can drive (see window.yg.onBack, wired up in app.js) is
 * about thirty lines.
 */
(function (YG) {
  'use strict';

  var screenEl = null;
  var tabsEl = null;
  var stack = [];

  /* ------------------------------------------------------------- helpers */

  function esc(s) {
    return String(s == null ? '' : s)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  function fmtMins(sec) {
    var m = Math.round(sec / 60);
    return m < 1 ? 'under a minute' : m + ' min';
  }

  function levelName(n) {
    return ['', 'Beginner', 'Intermediate', 'Advanced'][n] || 'Beginner';
  }

  function typeName(t) {
    return {
      asana: 'Posture', pranayama: 'Breathing', relaxation: 'Relaxation',
      mobility: 'Mobility', kriya: 'Cleansing practice'
    }[t] || t;
  }

  function ico(d, extra) {
    return '<svg viewBox="0 0 24 24" aria-hidden="true"' + (extra || '') + '>' + d + '</svg>';
  }
  var CHEV = ico('<path d="M9 6l6 6-6 6"/>');
  var CHECK = ico('<path d="M4 12l5 5L20 6"/>');
  var BACK = ico('<path d="M15 6l-6 6 6 6"/>');
  var GEAR = ico('<circle cx="12" cy="12" r="3.2"/><path d="M12 2.6v2.2M12 19.2v2.2M4.2 ' +
                 '7.4l1.9 1.1M17.9 15.5l1.9 1.1M4.2 16.6l1.9-1.1M17.9 8.5l1.9-1.1"/>');

  var TAB_ICONS = {
    today: ico('<path d="M12 3.5c2.2 2.6 3.2 5 3.2 7.2a3.2 3.2 0 0 1-6.4 0c0-2.2 1-4.6 ' +
               '3.2-7.2z"/><path d="M5 20.5c1.8-2.4 4.2-3.6 7-3.6s5.2 1.2 7 3.6"/>'),
    conditions: ico('<path d="M4 6.5h16M4 12h16M4 17.5h16"/><circle cx="7.5" cy="6.5" r="0"/>'),
    poses: ico('<circle cx="12" cy="5.6" r="2.3"/><path d="M12 8.4v5.2M12 10.6L7.4 ' +
               '13.4M12 10.6l4.6 2.8M8.4 20.4c1-2.4 2.2-3.6 3.6-3.6s2.6 1.2 3.6 3.6"/>'),
    progress: ico('<path d="M4 19.5V13M9.3 19.5V8M14.7 19.5v-6M20 19.5V5"/>')
  };

  /* --------------------------------------------------------------- router */

  var SCREENS = {};
  var TABS = ['today', 'conditions', 'poses', 'progress'];

  function currentRoute() {
    return stack.length ? stack[stack.length - 1] : null;
  }

  function render() {
    var route = currentRoute();
    if (!route) return;
    var fn = SCREENS[route.name];
    if (!fn) return;
    screenEl.className = 'screen' + (TABS.indexOf(route.name) >= 0 ? ' has-tabs' : '');
    screenEl.innerHTML = fn(route.params || {});
    screenEl.scrollTop = route.scroll || 0;
    screenEl.classList.add('fade-in');
    setTimeout(function () { screenEl.classList.remove('fade-in'); }, 240);
    updateTabs();
  }

  function updateTabs() {
    var route = currentRoute();
    var show = route && TABS.indexOf(route.name) >= 0 && YG.Store.profile().onboarded;
    tabsEl.style.display = show ? '' : 'none';
    Array.prototype.forEach.call(tabsEl.querySelectorAll('button'), function (b) {
      b.setAttribute('aria-current', String(route && b.dataset.tab === route.name));
    });
  }

  var UI = {

    /** Replaces the stack - used for tabs and for leaving onboarding. */
    go: function (name, params) {
      stack = [{ name: name, params: params || {} }];
      render();
    },

    /** Pushes a detail screen on top of the current one. */
    push: function (name, params) {
      var route = currentRoute();
      if (route) route.scroll = screenEl.scrollTop;
      stack.push({ name: name, params: params || {} });
      render();
    },

    /** @returns true if it consumed the back press. */
    back: function () {
      if (stack.length <= 1) return false;
      stack.pop();
      render();
      return true;
    },

    refresh: render,

    /* ------------------------------------------------------------ sheets */

    sheet: function (html, onMount) {
      var back = document.createElement('div');
      back.className = 'sheet-back';
      back.innerHTML = '<div class="sheet"><div class="grab"></div>' + html + '</div>';
      document.body.appendChild(back);
      back.addEventListener('click', function (ev) {
        if (ev.target === back) close();
      });
      function close() {
        if (back.parentNode) back.parentNode.removeChild(back);
        if (YG.activeSheet === api) YG.activeSheet = null;
      }
      var api = { close: close, el: back };
      YG.activeSheet = api;
      if (onMount) onMount(back, close);
      return api;
    },

    confirmSheet: function (o) {
      var cancelled = true;
      var s = UI.sheet(
        '<h2>' + esc(o.title) + '</h2>' +
        '<p class="muted small">' + esc(o.body || '') + '</p>' +
        '<div class="row" style="margin-top:16px;gap:10px">' +
          '<button class="btn ghost grow" data-x="cancel">' + esc(o.cancel || 'Cancel') + '</button>' +
          '<button class="btn primary grow" data-x="ok">' + esc(o.confirm || 'OK') + '</button>' +
        '</div>',
        function (root, close) {
          root.querySelector('[data-x="ok"]').addEventListener('click', function () {
            cancelled = false;
            close();
            if (o.onConfirm) o.onConfirm();
          });
          root.querySelector('[data-x="cancel"]').addEventListener('click', close);
          root.addEventListener('click', function (ev) {
            if (ev.target === root && cancelled && o.onCancel) o.onCancel();
          });
        });
      return s;
    },

    /* ------------------------------------------------------------ session */

    startSession: function (conditionId, sessionId) {
      var cond = YG.CONDITION_BY_ID[conditionId];
      if (!cond) return;
      var session = null;
      for (var i = 0; i < cond.sessions.length; i++) {
        if (cond.sessions[i].id === sessionId) session = cond.sessions[i];
      }
      if (!session) return;

      var profile = YG.Store.profile();
      var built = YG.Safety.buildSession(session, profile);
      if (!built.steps.length) {
        UI.sheet('<h2>Nothing safe left in this session</h2><p class="muted small">' +
                 'Every pose in it is ruled out by your health profile. Try a gentler ' +
                 'session, or review your profile in Settings.</p>' +
                 '<button class="btn block ghost" style="margin-top:14px" data-x="c">Close</button>',
          function (root, close) {
            root.querySelector('[data-x="c"]').addEventListener('click', close);
          });
        return;
      }

      askRating('Before you start', 'How are your symptoms right now?', function (before) {
        YG.Player.open(built, { sessionName: session.name }, function (result) {
          if (!result.seconds) { UI.refresh(); return; }
          askRating('How was that?', 'And how do your symptoms feel now?', function (after) {
            YG.Store.logSession({
              sessionId: session.id,
              conditionId: cond.id,
              seconds: result.seconds,
              completed: result.completed,
              before: before,
              after: after
            });
            UI.go('today');
            if (result.completed) celebrate(cond, result.seconds, before, after);
          });
        });
      });
    },

    startBreathOnly: function (poseId) {
      var pose = YG.POSE_BY_ID[poseId];
      if (!pose) return;
      var built = {
        steps: [{ pose: pose, sec: pose.hold, from: null, reason: null }],
        adjustments: [],
        totalSec: pose.hold
      };
      YG.Player.open(built, { sessionName: pose.name }, function (result) {
        if (result.seconds) {
          YG.Store.logSession({
            sessionId: pose.id, conditionId: '', seconds: result.seconds,
            completed: result.completed, before: null, after: null
          });
        }
        UI.go('today');
      });
    },

    esc: esc,

    mount: function (screenNode, tabsNode) {
      screenEl = screenNode;
      tabsEl = tabsNode;

      tabsEl.innerHTML = TABS.map(function (t) {
        var label = { today: 'Today', conditions: 'Conditions', poses: 'Poses', progress: 'Progress' }[t];
        return '<button data-tab="' + t + '">' + TAB_ICONS[t] + '<span>' + label + '</span></button>';
      }).join('');

      tabsEl.addEventListener('click', function (ev) {
        var b = ev.target.closest('button[data-tab]');
        if (b) UI.go(b.dataset.tab);
      });

      screenEl.addEventListener('click', onScreenClick);
      // Live inputs (library search, toggles, time picker) are handled without
      // re-rendering the screen - a re-render would blur the field mid-keystroke.
      screenEl.addEventListener('input', onScreenInput);
      screenEl.addEventListener('change', onScreenInput);
    }
  };

  /* --------------------------------------------------- symptom check-in */

  function askRating(title, body, done) {
    var picked = null;
    UI.sheet(
      '<h2>' + esc(title) + '</h2>' +
      '<p class="muted small">' + esc(body) + '</p>' +
      '<div class="scale" style="margin-top:14px">' +
        [1, 2, 3, 4, 5].map(function (n) {
          return '<button data-r="' + n + '" aria-pressed="false">' + n + '</button>';
        }).join('') +
      '</div>' +
      '<div class="row tiny muted" style="margin-top:6px">' +
        '<span class="grow">1 — no trouble</span><span>5 — very bad</span></div>' +
      '<button class="btn block ghost" style="margin-top:16px" data-x="skip">Skip</button>',
      function (root, close) {
        root.addEventListener('click', function (ev) {
          var b = ev.target.closest('[data-r]');
          if (b) {
            picked = parseInt(b.dataset.r, 10);
            Array.prototype.forEach.call(root.querySelectorAll('[data-r]'), function (n) {
              n.setAttribute('aria-pressed', String(n === b));
            });
            // No confirm step: the tap is the answer, and a second tap to
            // continue is one tap too many when you are about to lie down.
            setTimeout(function () { close(); done(picked); }, 180);
            return;
          }
          if (ev.target.closest('[data-x="skip"]')) { close(); done(null); }
        });
      });
  }

  function celebrate(cond, seconds, before, after) {
    var streak = YG.Store.streak();
    var delta = (before != null && after != null) ? before - after : null;
    var msg = '';
    if (delta > 0) msg = 'You rated your symptoms ' + delta + ' point' + (delta > 1 ? 's' : '') +
                         ' better than before you started.';
    else if (delta === 0) msg = 'No change today. That is normal - the effect builds over weeks.';
    UI.sheet(
      '<h2>Practice complete</h2>' +
      '<p class="muted small">' + esc(Math.round(seconds / 60)) + ' minutes of ' +
      esc(cond.name) + '.' + (msg ? ' ' + esc(msg) : '') + '</p>' +
      '<div class="stats" style="margin:14px 0">' +
        '<div class="stat"><div class="n">' + streak + '</div><div class="l">day streak</div></div>' +
        '<div class="stat"><div class="n">' + YG.Store.sessionCount() + '</div><div class="l">sessions</div></div>' +
        '<div class="stat"><div class="n">' + YG.Store.totalMinutes() + '</div><div class="l">minutes</div></div>' +
      '</div>' +
      '<button class="btn block primary" data-x="c">Done</button>',
      function (root, close) {
        root.querySelector('[data-x="c"]').addEventListener('click', close);
      });
  }

  /* ------------------------------------------------------- event routing */

  function onScreenClick(ev) {
    var t = ev.target.closest('[data-go]');
    if (t) {
      var params = {};
      if (t.dataset.id) params.id = t.dataset.id;
      if (t.dataset.sub) params.sub = t.dataset.sub;
      if (t.dataset.go === 'back') { UI.back(); return; }
      UI.push(t.dataset.go, params);
      return;
    }
    var act = ev.target.closest('[data-do]');
    if (act && ACTIONS[act.dataset.do]) ACTIONS[act.dataset.do](act, ev);
  }

  function onScreenInput(ev) {
    var n = ev.target.closest('[data-input]');
    if (n && ACTIONS[n.dataset.input]) ACTIONS[n.dataset.input](n, ev);
  }

  var ACTIONS = {};

  YG.UI = UI;
  YG.UI._screens = SCREENS;
  YG.UI._actions = ACTIONS;
  YG.UI._esc = esc;
  YG.UI._fmtMins = fmtMins;
  YG.UI._levelName = levelName;
  YG.UI._typeName = typeName;
  YG.UI._icons = { CHEV: CHEV, CHECK: CHECK, BACK: BACK, GEAR: GEAR };
})(window.YG = window.YG || {});
