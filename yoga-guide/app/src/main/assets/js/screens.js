/*
 * Screen renderers and the actions they fire.
 *
 * Each renderer is a pure function from route params to an HTML string;
 * anything interactive carries a data-go (navigate) or data-do (act) attribute
 * and is picked up by the delegated handlers in ui.js. Nothing here holds a DOM
 * reference across a render.
 */
(function (YG) {
  'use strict';

  var S = YG.UI._screens;
  var A = YG.UI._actions;
  var esc = YG.UI._esc;
  var fmtMins = YG.UI._fmtMins;
  var levelName = YG.UI._levelName;
  var typeName = YG.UI._typeName;
  var I = YG.UI._icons;

  function Store() { return YG.Store; }
  function profile() { return YG.Store.profile(); }

  /* ------------------------------------------------------------ fragments */

  function topbar(title, sub, opts) {
    opts = opts || {};
    return '<div class="topbar">' +
      (opts.back ? '<button class="iconbtn" data-go="back" aria-label="Back">' + I.BACK + '</button>' : '') +
      '<div class="grow"><h1>' + esc(title) + '</h1>' +
        (sub ? '<div class="sub">' + esc(sub) + '</div>' : '') + '</div>' +
      (opts.settings ? '<button class="iconbtn" data-go="settings" aria-label="Settings">' + I.GEAR + '</button>' : '') +
    '</div>';
  }

  function figBox(figName, label, cls) {
    return '<div class="figbox ' + (cls || '') + '">' + YG.figureFor(figName, { label: label }) + '</div>';
  }

  function sessionLength(session, p) {
    return YG.Safety.buildSession(session, p).totalSec;
  }

  function poseRow(pose, p) {
    var ann = YG.Safety.annotate(pose, p);
    return '<button class="rowitem" data-go="pose" data-id="' + esc(pose.id) + '">' +
      figBox(pose.fig, pose.name, 'sm') +
      '<span class="grow">' +
        '<span class="name">' + esc(pose.name) + '</span>' +
        '<span class="meta"> · ' + esc(typeName(pose.type)) + '</span>' +
        '<div class="meta">' + esc(pose.sanskrit || levelName(pose.level)) + '</div>' +
        (ann.blocked
          ? '<div class="meta" style="color:var(--danger)">Not for you — ' +
            esc(ann.blockingLabels.join(', ')) + '</div>'
          : '') +
      '</span>' +
      '<span class="chev">' + I.CHEV + '</span>' +
    '</button>';
  }

  /* ============================== DISCLAIMER ============================= */

  S.disclaimer = function () {
    return '<div style="max-width:34em;margin:0 auto">' +
      '<div style="width:96px;margin:14px auto 6px">' +
        YG.figureFor('sukhasana', { label: 'Seated figure' }) + '</div>' +
      '<h1 class="center">Yoga Guide</h1>' +
      '<p class="muted center small" style="margin-bottom:18px">Please read this before you start.</p>' +
      '<div class="banner">' +
        '<strong>This app is educational, not medical advice.</strong>' +
        '<ul>' +
          '<li>It does not diagnose, treat or cure any condition, and it is not a substitute ' +
              'for your doctor, physiotherapist or midwife.</li>' +
          '<li>Talk to a professional before starting if you are pregnant, recovering from ' +
              'surgery or injury, or managing a diagnosed condition.</li>' +
          '<li>Stop immediately if you feel pain, dizziness, breathlessness or anything ' +
              'that is simply not right.</li>' +
          '<li>Never adjust prescribed medication because a practice seems to be helping.</li>' +
        '</ul>' +
      '</div>' +
      '<p class="small muted">The health details you enter next stay on this phone. The app has ' +
        'no internet permission at all, so nothing you record here can be sent anywhere.</p>' +
      '<button class="btn primary block" style="margin-top:14px" data-do="accept-disclaimer">' +
        'I understand — continue</button>' +
    '</div>';
  };

  A['accept-disclaimer'] = function () {
    Store().acceptDisclaimer();
    YG.UI.go(profile().onboarded ? 'today' : 'onboarding');
  };

  /* ============================== ONBOARDING ============================= */

  var ob = { step: 0 };

  function obSteps() {
    var steps = ['goals', 'level', 'health'];
    if (profile().flags.pregnancy) steps.push('trimester');
    steps.push('length', 'reminder');
    return steps;
  }

  S.onboarding = function () {
    var steps = obSteps();
    if (ob.step >= steps.length) ob.step = steps.length - 1;
    var key = steps[ob.step];
    var body = OB_BODY[key]();

    return '<div class="ob-progress">' +
      steps.map(function (_, i) { return '<i class="' + (i <= ob.step ? 'on' : '') + '"></i>'; }).join('') +
    '</div>' + body +
    '<div class="ob-actions">' +
      (ob.step > 0 ? '<button class="btn ghost" data-do="ob-back">Back</button>' : '') +
      '<button class="btn primary" data-do="ob-next">' +
        (ob.step === steps.length - 1 ? 'Finish' : 'Continue') + '</button>' +
    '</div>';
  };

  var OB_BODY = {
    goals: function () {
      var chosen = profile().conditions;
      return '<h1>What would you like help with?</h1>' +
        '<p class="muted small">Pick as many as apply. You can change this any time, and every ' +
        'condition stays browsable either way.</p>' +
        '<div class="rows" style="margin-top:14px">' +
          YG.CONDITIONS.map(function (c) {
            var on = chosen.indexOf(c.id) >= 0;
            return '<button class="checkrow" data-do="ob-goal" data-id="' + esc(c.id) + '" ' +
              'aria-pressed="' + on + '">' +
              '<span class="box">' + I.CHECK + '</span>' +
              '<span class="grow"><span class="name">' + esc(c.name) + '</span>' +
              '<div class="meta">' + esc(c.tagline) + '</div></span></button>';
          }).join('') +
        '</div>';
    },

    level: function () {
      var lv = profile().level;
      return '<h1>How much yoga have you done?</h1>' +
        '<p class="muted small">This decides whether advanced postures appear in your sessions ' +
        'at all.</p>' +
        '<div class="rows" style="margin-top:14px">' +
          [[1, 'New to yoga', 'Only beginner and intermediate poses. Advanced ones are replaced.'],
           [2, 'Some experience', 'Everything, including the advanced postures.'],
           [3, 'Experienced', 'Everything, at full length.']].map(function (r) {
            return '<button class="checkrow" data-do="ob-level" data-id="' + r[0] + '" ' +
              'aria-pressed="' + (lv === r[0]) + '">' +
              '<span class="box">' + I.CHECK + '</span>' +
              '<span class="grow"><span class="name">' + esc(r[1]) + '</span>' +
              '<div class="meta">' + esc(r[2]) + '</div></span></button>';
          }).join('') +
        '</div>';
    },

    health: function () {
      var flags = profile().flags;
      return '<h1>Anything we should work around?</h1>' +
        '<p class="muted small">This is the part that matters most. Every pose in the app is ' +
        'tagged with what it is unsafe for, and anything you tick here is removed from your ' +
        'sessions or swapped for something gentler.</p>' +
        '<div class="rows" style="margin-top:14px">' +
          YG.FLAGS.map(function (f) {
            return '<button class="checkrow" data-do="ob-flag" data-id="' + esc(f.id) + '" ' +
              'aria-pressed="' + !!flags[f.id] + '">' +
              '<span class="box">' + I.CHECK + '</span>' +
              '<span class="grow"><span class="name">' + esc(f.label) + '</span></span></button>';
          }).join('') +
        '</div>' +
        '<p class="tiny muted" style="margin-top:12px">Nothing here leaves your phone.</p>';
    },

    trimester: function () {
      var t = profile().trimester;
      return '<h1>How far along are you?</h1>' +
        '<p class="muted small">What is safe changes considerably between trimesters, so your ' +
        'prenatal sessions are chosen by this.</p>' +
        '<div class="rows" style="margin-top:14px">' +
          [[1, 'First trimester', 'Weeks 1–13'],
           [2, 'Second trimester', 'Weeks 14–27'],
           [3, 'Third trimester', 'Week 28 onwards']].map(function (r) {
            return '<button class="checkrow" data-do="ob-trimester" data-id="' + r[0] + '" ' +
              'aria-pressed="' + (t === r[0]) + '">' +
              '<span class="box">' + I.CHECK + '</span>' +
              '<span class="grow"><span class="name">' + esc(r[1]) + '</span>' +
              '<div class="meta">' + esc(r[2]) + '</div></span></button>';
          }).join('') +
        '</div>' +
        '<div class="banner" style="margin-top:14px">Please check with your doctor or midwife ' +
        'before starting, and stop if anything feels wrong.</div>';
    },

    length: function () {
      var l = profile().length;
      return '<h1>How long do you want to practise?</h1>' +
        '<p class="muted small">Every session scales to fit. You can override this per session ' +
        'later.</p>' +
        '<div class="rows" style="margin-top:14px">' +
          [['short', 'Short', 'About 60% of the full length — good for a daily habit'],
           ['medium', 'Standard', 'The sessions as written'],
           ['long', 'Long', 'Longer holds, deeper practice']].map(function (r) {
            return '<button class="checkrow" data-do="ob-length" data-id="' + r[0] + '" ' +
              'aria-pressed="' + (l === r[0]) + '">' +
              '<span class="box">' + I.CHECK + '</span>' +
              '<span class="grow"><span class="name">' + esc(r[1]) + '</span>' +
              '<div class="meta">' + esc(r[2]) + '</div></span></button>';
          }).join('') +
        '</div>';
    },

    reminder: function () {
      var r = profile().reminder;
      var hh = ('0' + r.hour).slice(-2) + ':' + ('0' + r.minute).slice(-2);
      return '<h1>A daily nudge?</h1>' +
        '<p class="muted small">One notification a day, at a time you choose. Consistency ' +
        'matters far more than session length.</p>' +
        '<div class="card" style="margin-top:14px">' +
          '<div class="row">' +
            '<span class="grow"><strong>Daily reminder</strong>' +
            '<div class="small muted">Off unless you want it</div></span>' +
            '<label class="toggle"><input type="checkbox" data-input="ob-reminder-on"' +
              (r.on ? ' checked' : '') + '><span></span></label>' +
          '</div>' +
          '<div class="row" style="margin-top:12px">' +
            '<span class="grow">Time</span>' +
            '<input type="time" value="' + hh + '" data-input="ob-reminder-time">' +
          '</div>' +
        '</div>';
    }
  };

  A['ob-goal'] = function (btn) {
    var p = profile();
    var id = btn.dataset.id;
    var i = p.conditions.indexOf(id);
    if (i >= 0) p.conditions.splice(i, 1); else p.conditions.push(id);

    // Choosing a condition asserts the health fact behind it. Picking Pregnancy
    // and then not being asked whether you are pregnant is the kind of gap that
    // makes a safety filter useless.
    var cond = YG.CONDITION_BY_ID[id];
    (cond.autoFlags || []).forEach(function (f) {
      if (i >= 0) { if (!wantedByOtherCondition(f, p)) delete p.flags[f]; }
      else p.flags[f] = true;
    });
    Store().saveProfile();
    YG.UI.refresh();
  };

  function wantedByOtherCondition(flag, p) {
    for (var i = 0; i < p.conditions.length; i++) {
      var c = YG.CONDITION_BY_ID[p.conditions[i]];
      if (c && (c.autoFlags || []).indexOf(flag) >= 0) return true;
    }
    return false;
  }

  A['ob-level'] = function (b) { Store().saveProfile({ level: parseInt(b.dataset.id, 10) }); YG.UI.refresh(); };
  A['ob-trimester'] = function (b) { Store().saveProfile({ trimester: parseInt(b.dataset.id, 10) }); YG.UI.refresh(); };
  A['ob-length'] = function (b) { Store().saveProfile({ length: b.dataset.id }); YG.UI.refresh(); };
  A['ob-flag'] = function (b) { Store().setFlag(b.dataset.id, b.getAttribute('aria-pressed') !== 'true'); YG.UI.refresh(); };

  A['ob-reminder-on'] = function (n) {
    var p = profile();
    p.reminder.on = n.checked;
    Store().saveProfile();
    applyReminder();
  };
  A['ob-reminder-time'] = function (n) {
    var parts = (n.value || '07:00').split(':');
    var p = profile();
    p.reminder.hour = parseInt(parts[0], 10) || 0;
    p.reminder.minute = parseInt(parts[1], 10) || 0;
    Store().saveProfile();
    applyReminder();
  };

  function applyReminder() {
    var r = profile().reminder;
    if (!r.on) { YG.Native.cancelReminder(); return; }
    if (!YG.Native.notificationsAllowed()) YG.Native.requestNotifications();
    YG.Native.scheduleReminder(r.hour, r.minute);
  }
  YG.applyReminder = applyReminder;

  A['ob-back'] = function () { if (ob.step > 0) ob.step--; YG.UI.refresh(); };
  A['ob-next'] = function () {
    var steps = obSteps();
    if (ob.step < steps.length - 1) { ob.step++; YG.UI.refresh(); return; }
    Store().saveProfile({ onboarded: true });
    applyReminder();
    YG.UI.go('today');
  };

  /* ================================ TODAY =============================== */

  S.today = function () {
    var p = profile();
    var hour = new Date().getHours();
    var greeting = hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening';
    var streak = Store().streak();
    var out = topbar(greeting, streak > 0 ? streak + ' day streak — keep it going' :
                     'A few minutes is enough to start', { settings: true });

    var chosen = p.conditions.map(function (id) { return YG.CONDITION_BY_ID[id]; })
                             .filter(Boolean);

    if (!chosen.length) {
      out += '<div class="card"><h2>Pick what to work on</h2>' +
        '<p class="muted small">Choose one or more conditions and your sessions will be built ' +
        'and filtered around them.</p>' +
        '<button class="btn primary block" style="margin-top:12px" data-go="conditions">' +
        'Browse conditions</button></div>';
    } else {
      var primary = chosen[0];
      var session = pickSession(primary, p);
      var built = YG.Safety.buildSession(session, p);
      out += '<button class="card tap" data-go="session" data-id="' + esc(primary.id) +
             '" data-sub="' + esc(session.id) + '">' +
        '<div class="row">' +
          figBox(primary.hero, primary.name, 'sm') +
          '<span class="grow">' +
            '<div class="tiny muted" style="text-transform:uppercase;letter-spacing:.06em;' +
              'font-weight:700">Today\'s practice</div>' +
            '<h2>' + esc(session.name) + '</h2>' +
            '<div class="small muted">' + esc(primary.name) + ' · ' +
              esc(fmtMins(built.totalSec)) + ' · ' + built.steps.length + ' poses</div>' +
          '</span>' +
          '<span class="chev">' + I.CHEV + '</span>' +
        '</div>' +
      '</button>';

      if (built.adjustments.length) {
        out += '<div class="banner info small">' +
          built.adjustments.length + ' pose' + (built.adjustments.length > 1 ? 's have' : ' has') +
          ' been adjusted for your health profile. Open the session to see what and why.' +
        '</div>';
      }

      if (chosen.length > 1) {
        out += '<div class="section-title">Your other goals</div><div class="rows">' +
          chosen.slice(1).map(function (c) {
            var s = pickSession(c, p);
            var b = YG.Safety.buildSession(s, p);
            return '<button class="rowitem" data-go="condition" data-id="' + esc(c.id) + '">' +
              figBox(c.hero, c.name, 'sm') +
              '<span class="grow"><span class="name">' + esc(c.name) + '</span>' +
              '<div class="meta">' + esc(s.name) + ' · ' + esc(fmtMins(b.totalSec)) + '</div></span>' +
              '<span class="chev">' + I.CHEV + '</span></button>';
          }).join('') + '</div>';
      }
    }

    out += '<div class="section-title">Quick practice</div>' +
      '<div class="rows">' +
        quickRow('anulom_vilom', 'Alternate Nostril Breathing', 'Three minutes, sitting anywhere') +
        quickRow('deep_breathing', 'Deep Yogic Breath', 'The simplest way to settle') +
        quickRow('bhramari', 'Humming Bee Breath', 'For a busy head') +
        quickRow('yoga_nidra', 'Yoga Nidra', 'Deep rest, lying down') +
      '</div>';

    var notes = YG.Safety.personalNotes(p);
    if (notes.length) {
      out += '<div class="section-title">Applied to every session</div>' +
        '<div class="banner"><ul>' +
          notes.map(function (n) { return '<li>' + esc(n) + '</li>'; }).join('') +
        '</ul></div>';
    }

    return out;
  };

  function quickRow(poseId, name, sub) {
    var pose = YG.POSE_BY_ID[poseId];
    var ann = YG.Safety.annotate(pose, profile());
    if (ann.blocked) return '';
    return '<button class="rowitem" data-do="quick" data-id="' + esc(poseId) + '">' +
      figBox(pose.fig, name, 'sm') +
      '<span class="grow"><span class="name">' + esc(name) + '</span>' +
      '<div class="meta">' + esc(sub) + ' · ' + Math.round(pose.hold / 60) + ' min</div></span>' +
      '<span class="chev">' + I.CHEV + '</span></button>';
  }

  A.quick = function (b) { YG.UI.startBreathOnly(b.dataset.id); };

  /**
   * The session shown as "today's practice". Pregnancy is picked by trimester;
   * everything else follows the user's declared level, falling back to the
   * gentlest session rather than to nothing.
   */
  function pickSession(cond, p) {
    if (cond.id === 'pregnancy') {
      var want = 'preg_t' + (p.trimester || 2);
      for (var i = 0; i < cond.sessions.length; i++) {
        if (cond.sessions[i].id === want) return cond.sessions[i];
      }
    }
    var best = null;
    for (var j = 0; j < cond.sessions.length; j++) {
      var s = cond.sessions[j];
      if (s.level <= (p.level || 1) && (!best || s.level > best.level)) best = s;
    }
    return best || cond.sessions[0];
  }

  /* ============================== CONDITIONS ============================= */

  S.conditions = function () {
    var p = profile();
    var chosen = p.conditions;
    var sorted = YG.CONDITIONS.slice().sort(function (a, b) {
      return (chosen.indexOf(b.id) >= 0) - (chosen.indexOf(a.id) >= 0);
    });
    return topbar('Conditions', YG.CONDITIONS.length + ' areas, ' + YG.POSES.length + ' poses',
                  { settings: true }) +
      '<div class="rows">' +
        sorted.map(function (c) {
          var on = chosen.indexOf(c.id) >= 0;
          return '<button class="rowitem" data-go="condition" data-id="' + esc(c.id) + '">' +
            figBox(c.hero, c.name, 'sm') +
            '<span class="grow"><span class="name">' + esc(c.name) + '</span>' +
              (on ? '<span class="meta"> · yours</span>' : '') +
              '<div class="meta">' + esc(c.tagline) + '</div></span>' +
            '<span class="chev">' + I.CHEV + '</span></button>';
        }).join('') +
      '</div>';
  };

  S.condition = function (params) {
    var p = profile();
    var c = YG.CONDITION_BY_ID[params.id];
    if (!c) return topbar('Not found', '', { back: true });
    var chosen = p.conditions.indexOf(c.id) >= 0;

    var out = topbar(c.name, c.tagline, { back: true }) +
      figBox(c.hero, c.name) +
      '<div class="card" style="margin-top:12px"><p class="small">' + esc(c.about) + '</p></div>';

    out += '<div class="section-title">Why it helps</div><div class="card"><ul class="bullets small">' +
      c.why.map(function (w) { return '<li>' + esc(w) + '</li>'; }).join('') + '</ul></div>';

    out += '<div class="banner"><strong>Before you begin</strong><ul>' +
      c.safety.map(function (s) { return '<li>' + esc(s) + '</li>'; }).join('') + '</ul></div>';

    out += '<div class="section-title">Sessions</div><div class="rows">' +
      c.sessions.map(function (s) {
        var b = YG.Safety.buildSession(s, p);
        var disabled = !b.steps.length;
        return '<button class="rowitem"' + (disabled ? ' disabled style="opacity:.5"' : '') +
          ' data-go="session" data-id="' + esc(c.id) + '" data-sub="' + esc(s.id) + '">' +
          '<span class="grow"><span class="name">' + esc(s.name) + '</span>' +
          '<div class="meta">' + (disabled ? 'Not suitable with your health profile'
            : esc(fmtMins(b.totalSec)) + ' · ' + b.steps.length + ' poses · ' +
              esc(levelName(s.level))) + '</div></span>' +
          '<span class="chev">' + I.CHEV + '</span></button>';
      }).join('') + '</div>';

    var safe = YG.Safety.safePosesFor(c.id, p);
    if (safe.length) {
      out += '<div class="section-title">Poses that help — safe for you (' + safe.length + ')</div>' +
        '<div class="rows">' + safe.map(function (x) { return poseRow(x, p); }).join('') + '</div>';
    }

    out += '<button class="btn block ' + (chosen ? 'ghost' : 'primary') + '" ' +
      'style="margin-top:14px" data-do="toggle-goal" data-id="' + esc(c.id) + '">' +
      (chosen ? 'Remove from my goals' : 'Add to my goals') + '</button>';

    return out;
  };

  A['toggle-goal'] = function (b) {
    A['ob-goal'](b);
  };

  /* =============================== SESSION ============================== */

  S.session = function (params) {
    var p = profile();
    var c = YG.CONDITION_BY_ID[params.id];
    if (!c) return topbar('Not found', '', { back: true });
    var s = null;
    for (var i = 0; i < c.sessions.length; i++) if (c.sessions[i].id === params.sub) s = c.sessions[i];
    if (!s) return topbar('Not found', '', { back: true });

    var built = YG.Safety.buildSession(s, p);

    var out = topbar(s.name, c.name + ' · ' + fmtMins(built.totalSec), { back: true });

    if (s.note) out += '<div class="banner info small">' + esc(s.note) + '</div>';

    if (!built.steps.length) {
      out += '<div class="banner"><strong>Nothing in this session is safe for your profile.</strong>' +
        ' Try a gentler session for this condition, or review Settings.</div>';
      return out;
    }

    if (built.adjustments.length) {
      out += '<div class="section-title">Adjusted for you</div><div class="card"><ul class="bullets small">' +
        built.adjustments.map(function (a) {
          return a.type === 'swap'
            ? '<li><strong>' + esc(a.original.name) + '</strong> → ' + esc(a.replacement.name) +
              '<div class="muted tiny">' + esc(a.reason) + '</div></li>'
            : '<li><strong>' + esc(a.original ? a.original.name : 'A pose') + '</strong> removed' +
              '<div class="muted tiny">' + esc(a.reason) + '</div></li>';
        }).join('') + '</ul></div>';
    }

    out += '<div class="section-title">' + built.steps.length + ' poses</div><div class="rows">' +
      built.steps.map(function (st, idx) {
        return '<button class="rowitem" data-go="pose" data-id="' + esc(st.pose.id) + '">' +
          figBox(st.pose.fig, st.pose.name, 'sm') +
          '<span class="grow"><span class="name">' + (idx + 1) + '. ' + esc(st.pose.name) + '</span>' +
          '<div class="meta">' + Math.round(st.sec) + 's' +
            (st.pose.sides ? ' each side' : '') +
            (st.from ? ' · swapped in for ' + esc(st.from.name) : '') + '</div></span>' +
          '<span class="chev">' + I.CHEV + '</span></button>';
      }).join('') + '</div>';

    out += '<button class="btn primary block" style="margin-top:16px" data-do="start" ' +
      'data-id="' + esc(c.id) + '" data-sub="' + esc(s.id) + '">Start session</button>' +
      '<p class="tiny muted center" style="margin-top:10px">Stop at any point if something hurts. ' +
      'Discomfort is fine; pain is a signal.</p>';

    return out;
  };

  A.start = function (b) { YG.UI.startSession(b.dataset.id, b.dataset.sub); };

  /* ============================ POSE LIBRARY ============================ */

  var libFilter = 'all';

  S.poses = function () {
    var p = profile();
    var types = [['all', 'All'], ['asana', 'Postures'], ['pranayama', 'Breathing'],
                 ['relaxation', 'Relaxation'], ['mobility', 'Mobility'], ['kriya', 'Cleansing']];

    return topbar('Poses', YG.POSES.length + ' in the library', { settings: true }) +
      '<input type="search" placeholder="Search by name or Sanskrit" data-input="lib-search" ' +
        'style="width:100%;background:var(--surface-2);border:1px solid var(--line);' +
        'border-radius:12px;padding:11px 14px;min-height:46px">' +
      '<div class="chips" style="margin:12px 0">' +
        types.map(function (t) {
          return '<button class="chip" data-do="lib-filter" data-id="' + t[0] + '" ' +
            'aria-pressed="' + (libFilter === t[0]) + '">' + esc(t[1]) + '</button>';
        }).join('') +
      '</div>' +
      '<div class="rows" data-lib>' +
        YG.POSES.filter(function (x) { return libFilter === 'all' || x.type === libFilter; })
          .map(function (x) { return poseRow(x, p); }).join('') +
      '</div>' +
      '<p class="tiny muted center" style="margin-top:14px">Poses your profile rules out stay ' +
      'listed and marked, so you can still read why.</p>';
  };

  A['lib-filter'] = function (b) { libFilter = b.dataset.id; YG.UI.refresh(); };

  A['lib-search'] = function (n) {
    // Filtering the existing rows instead of re-rendering: a re-render would
    // blur the search field on every keystroke.
    var q = (n.value || '').trim().toLowerCase();
    var rows = document.querySelectorAll('[data-lib] .rowitem');
    Array.prototype.forEach.call(rows, function (row) {
      row.style.display = !q || row.textContent.toLowerCase().indexOf(q) >= 0 ? '' : 'none';
    });
  };

  S.pose = function (params) {
    var p = profile();
    var pose = YG.POSE_BY_ID[params.id];
    if (!pose) return topbar('Not found', '', { back: true });
    var ann = YG.Safety.annotate(pose, p);

    var out = topbar(pose.name, pose.sanskrit || typeName(pose.type), { back: true }) +
      figBox(pose.fig, pose.name);

    if (pose.fig2) {
      out += '<div class="tiny muted center" style="margin-top:6px">' +
        'This one moves between two shapes — the player alternates them.</div>' +
        figBox(pose.fig2, pose.name + ' (second phase)');
    }

    out += '<div class="chips" style="margin:12px 0">' +
      '<span class="chip level">' + esc(typeName(pose.type)) + '</span>' +
      '<span class="chip level">' + esc(levelName(pose.level)) + '</span>' +
      '<span class="chip level">' + pose.hold + 's' + (pose.sides ? ' each side' : '') + '</span>' +
    '</div>';

    if (ann.blocked) {
      out += '<div class="banner"><strong>Not recommended for you.</strong> Your profile lists: ' +
        esc(ann.blockingLabels.join(', ')) + '.' +
        (pose.alt ? ' The app substitutes ' + esc(YG.POSE_BY_ID[pose.alt].name) +
                    ' in your sessions.' : ' It is left out of your sessions.') + '</div>';
    } else if (ann.advanced) {
      out += '<div class="banner">Marked advanced. It is left out of your sessions while your ' +
        'level is set to ' + esc(levelName(p.level)) + '.</div>';
    }

    if (pose.breath) {
      out += '<div class="card flat" style="background:var(--accent-soft);border-color:transparent">' +
        '<strong class="small">Breath</strong><p class="small" style="margin-top:3px">' +
        esc(pose.breath) + '</p></div>';
    }

    out += '<div class="section-title">How to do it</div><div class="card"><ol class="steps small">' +
      pose.steps.map(function (s) { return '<li>' + esc(s) + '</li>'; }).join('') + '</ol></div>';

    out += '<div class="section-title">Benefits</div><div class="card"><ul class="bullets small">' +
      pose.benefits.map(function (s) { return '<li>' + esc(s) + '</li>'; }).join('') + '</ul></div>';

    if ((pose.cautions || []).length) {
      out += '<div class="section-title">Cautions</div><div class="banner"><ul>' +
        pose.cautions.map(function (s) { return '<li>' + esc(s) + '</li>'; }).join('') + '</ul></div>';
    }

    if ((pose.mods || []).length) {
      out += '<div class="section-title">Modifications</div><div class="card"><ul class="bullets small">' +
        pose.mods.map(function (s) { return '<li>' + esc(s) + '</li>'; }).join('') + '</ul></div>';
    }

    if ((pose.mistakes || []).length) {
      out += '<div class="section-title">Common mistakes</div><div class="card"><ul class="bullets small">' +
        pose.mistakes.map(function (s) { return '<li>' + esc(s) + '</li>'; }).join('') + '</ul></div>';
    }

    if ((pose.helps || []).length) {
      out += '<div class="section-title">Helps with</div><div class="chips">' +
        pose.helps.map(function (id) {
          var c = YG.CONDITION_BY_ID[id];
          return c ? '<button class="chip" data-go="condition" data-id="' + esc(id) + '">' +
                     esc(c.name) + '</button>' : '';
        }).join('') + '</div>';
    }

    if (!ann.blocked && !ann.advanced && (pose.type === 'pranayama' || pose.type === 'relaxation')) {
      out += '<button class="btn primary block" style="margin-top:18px" data-do="quick" ' +
        'data-id="' + esc(pose.id) + '">Practise this now</button>';
    }

    return out;
  };

  /* ============================== PROGRESS ============================== */

  S.progress = function () {
    var p = profile();
    var days = Store().recentDays(28);
    var maxMin = days.reduce(function (m, d) { return Math.max(m, d.minutes); }, 0);
    var out = topbar('Progress', Store().sessionCount() + ' sessions logged', { settings: true });

    out += '<div class="stats">' +
      '<div class="stat"><div class="n">' + Store().streak() + '</div><div class="l">day streak</div></div>' +
      '<div class="stat"><div class="n">' + Store().totalMinutes() + '</div><div class="l">minutes</div></div>' +
      '<div class="stat"><div class="n">' + Store().sessionCount() + '</div><div class="l">sessions</div></div>' +
    '</div>';

    out += '<div class="section-title">Last four weeks</div><div class="card">' +
      '<div class="heat-days">' +
        ['S', 'M', 'T', 'W', 'T', 'F', 'S'].map(function (d) { return '<span>' + d + '</span>'; }).join('') +
      '</div><div class="heat">' +
        padToWeek(days).map(function (d) {
          if (!d) return '<i style="visibility:hidden"></i>';
          var lvl = d.minutes === 0 ? '' : d.minutes < 10 ? 'l1' : d.minutes < 25 ? 'l2' : 'l3';
          return '<i class="' + lvl + '" title="' + d.minutes + ' min"></i>';
        }).join('') +
      '</div>' +
      '<div class="tiny muted" style="margin-top:8px">' +
        (maxMin ? 'Darkest squares are your longest days.' : 'Practise once and this fills in.') +
      '</div></div>';

    var primary = p.conditions.length ? YG.CONDITION_BY_ID[p.conditions[0]] : null;
    if (primary) {
      var trend = Store().symptomTrend(primary.id, 14);
      out += '<div class="section-title">' + esc(primary.name) + ' — symptom ratings</div><div class="card">';
      if (trend.length < 2) {
        out += '<p class="small muted">Rate your symptoms before and after a session and the ' +
               'change shows up here. Two sessions are enough to start a line.</p>';
      } else {
        var avgBefore = avg(trend.map(function (t) { return t.before; }));
        var avgAfter = avg(trend.map(function (t) { return t.after; }));
        out += '<div class="row" style="margin-bottom:10px">' +
          '<span class="grow small">Average before <strong>' + avgBefore.toFixed(1) + '</strong></span>' +
          '<span class="small">after <strong>' + avgAfter.toFixed(1) + '</strong></span></div>' +
          '<div class="spark">' +
            trend.map(function (t) {
              return '<i style="height:' + (t.after / 5 * 100) + '%"></i>';
            }).join('') +
          '</div>' +
          '<div class="tiny muted" style="margin-top:8px">Post-session rating, oldest to newest. ' +
          'Lower is better.</div>';
      }
      out += '</div>';
    }

    var h = Store().history().slice(-12).reverse();
    if (h.length) {
      out += '<div class="section-title">Recent sessions</div><div class="rows">' +
        h.map(function (e) {
          var c = YG.CONDITION_BY_ID[e.c];
          var d = new Date(e.ts);
          return '<div class="rowitem"><span class="grow">' +
            '<span class="name">' + esc(c ? c.name : 'Quick practice') + '</span>' +
            '<div class="meta">' + d.toLocaleDateString() + ' · ' + Math.round(e.sec / 60) +
            ' min' + (e.done ? '' : ' · ended early') + '</div></span></div>';
        }).join('') + '</div>';
    } else {
      out += '<div class="card center"><p class="muted small">No sessions yet. Your first one ' +
             'starts the streak.</p><button class="btn primary" data-go="conditions">' +
             'Find a session</button></div>';
    }

    return out;
  };

  function avg(a) { return a.reduce(function (x, y) { return x + y; }, 0) / a.length; }

  /** Leading blanks so the heatmap columns line up with weekday headings. */
  function padToWeek(days) {
    if (!days.length) return days;
    var pad = days[0].date.getDay();
    var out = [];
    for (var i = 0; i < pad; i++) out.push(null);
    return out.concat(days);
  }

  /* ============================== SETTINGS ============================== */

  S.settings = function () {
    var p = profile();
    var r = p.reminder;
    var hh = ('0' + r.hour).slice(-2) + ':' + ('0' + r.minute).slice(-2);
    var flagCount = Object.keys(p.flags).length;

    var out = topbar('Settings', '', { back: true });

    out += '<div class="section-title">Practice</div><div class="rows">' +
      '<button class="rowitem" data-do="edit-goals"><span class="grow">' +
        '<span class="name">Your goals</span><div class="meta">' +
        (p.conditions.length ? esc(p.conditions.map(function (id) {
          return YG.CONDITION_BY_ID[id].name; }).join(', ')) : 'None chosen') +
        '</div></span><span class="chev">' + I.CHEV + '</span></button>' +
      '<button class="rowitem" data-do="edit-level"><span class="grow">' +
        '<span class="name">Experience level</span><div class="meta">' +
        esc(levelName(p.level)) + '</div></span><span class="chev">' + I.CHEV + '</span></button>' +
      '<button class="rowitem" data-do="edit-length"><span class="grow">' +
        '<span class="name">Session length</span><div class="meta">' +
        esc({ short: 'Short', medium: 'Standard', long: 'Long' }[p.length]) +
        '</div></span><span class="chev">' + I.CHEV + '</span></button>' +
    '</div>';

    out += '<div class="section-title">Health profile</div><div class="rows">' +
      '<button class="rowitem" data-do="edit-health"><span class="grow">' +
        '<span class="name">Conditions to work around</span><div class="meta">' +
        (flagCount ? flagCount + ' noted' : 'None noted') +
        '</div></span><span class="chev">' + I.CHEV + '</span></button>' +
      (p.flags.pregnancy ? '<button class="rowitem" data-do="edit-trimester"><span class="grow">' +
        '<span class="name">Trimester</span><div class="meta">' +
        ['', 'First', 'Second', 'Third'][p.trimester] + '</div></span>' +
        '<span class="chev">' + I.CHEV + '</span></button>' : '') +
    '</div>' +
    '<p class="tiny muted" style="margin-top:8px">Keep this current. It is the only thing ' +
    'standing between you and a pose that is wrong for you today.</p>';

    out += '<div class="section-title">During a session</div><div class="rows">' +
      toggleRow('Spoken guidance', 'Pose names and cues read aloud', 'voice', p.voice) +
      toggleRow('Chimes', 'A soft bell at each transition', 'chime', p.chime) +
      toggleRow('Vibration', 'A tap at each breath phase', 'haptics', p.haptics) +
    '</div>';

    out += '<div class="section-title">Reminder</div><div class="card">' +
      '<div class="row"><span class="grow"><strong>Daily reminder</strong>' +
      '<div class="small muted">' + (YG.Native.hasNative ? 'One notification a day'
        : 'Only works in the installed app') + '</div></span>' +
      '<label class="toggle"><input type="checkbox" data-input="ob-reminder-on"' +
      (r.on ? ' checked' : '') + '><span></span></label></div>' +
      '<div class="row" style="margin-top:12px"><span class="grow">Time</span>' +
      '<input type="time" value="' + hh + '" data-input="ob-reminder-time"></div>' +
    '</div>';

    out += '<div class="section-title">About</div><div class="card">' +
      '<p class="small muted">Yoga Guide keeps everything on this device. It has no internet ' +
      'permission, no account and no analytics — your health profile and practice history ' +
      'cannot leave this phone.</p>' +
      '<p class="small muted">' + YG.POSES.length + ' poses, ' + YG.CONDITIONS.length +
      ' conditions. Illustrations are drawn by the app itself.</p></div>';

    out += '<div class="banner"><strong>Educational, not medical advice.</strong> This app does ' +
      'not diagnose, treat or cure anything. Talk to a professional before starting, and stop ' +
      'if anything hurts.</div>';

    out += '<button class="btn danger block" style="margin-top:6px" data-do="reset">' +
      'Erase all my data</button>';

    return out;
  };

  function toggleRow(name, sub, key, on) {
    return '<div class="rowitem"><span class="grow"><span class="name">' + esc(name) + '</span>' +
      '<div class="meta">' + esc(sub) + '</div></span>' +
      '<label class="toggle"><input type="checkbox" data-input="pref" data-id="' + key + '"' +
      (on ? ' checked' : '') + '><span></span></label></div>';
  }

  A.pref = function (n) {
    var patch = {};
    patch[n.dataset.id] = n.checked;
    Store().saveProfile(patch);
    if (n.dataset.id === 'voice' && !n.checked) YG.Native.stopSpeaking();
  };

  /* Settings re-uses the onboarding step bodies inside a sheet, so there is one
     definition of each editor rather than two that can drift apart. */
  function editSheet(key, title) {
    YG.UI.sheet('<h2>' + esc(title) + '</h2><div data-ob-body></div>' +
      '<button class="btn primary block" style="margin-top:14px" data-x="done">Done</button>',
      function (root, close) {
        function paint() { root.querySelector('[data-ob-body]').innerHTML = OB_BODY[key](); }
        paint();
        root.addEventListener('click', function (ev) {
          var b = ev.target.closest('[data-do]');
          if (b && A[b.dataset.do]) {
            // The onboarding actions call UI.refresh, which repaints the screen
            // behind the sheet; the sheet has to repaint itself.
            A[b.dataset.do](b, ev);
            paint();
            return;
          }
          if (ev.target.closest('[data-x="done"]')) { close(); YG.UI.refresh(); }
        });
        root.addEventListener('change', function (ev) {
          var n = ev.target.closest('[data-input]');
          if (n && A[n.dataset.input]) A[n.dataset.input](n, ev);
        });
      });
  }

  A['edit-goals'] = function () { editSheet('goals', 'Your goals'); };
  A['edit-level'] = function () { editSheet('level', 'Experience level'); };
  A['edit-length'] = function () { editSheet('length', 'Session length'); };
  A['edit-health'] = function () { editSheet('health', 'Conditions to work around'); };
  A['edit-trimester'] = function () { editSheet('trimester', 'Trimester'); };

  A.reset = function () {
    YG.UI.confirmSheet({
      title: 'Erase everything?',
      body: 'Your health profile, goals and every logged session will be deleted from this ' +
            'phone. This cannot be undone.',
      confirm: 'Erase',
      cancel: 'Keep my data',
      onConfirm: function () {
        YG.Native.cancelReminder();
        Store().resetAll();
        ob.step = 0;
        YG.UI.go('disclaimer');
      }
    });
  };
})(window.YG = window.YG || {});
