/*
 * The guided session player.
 *
 * Full-screen, one pose at a time, with a countdown ring, spoken cues and a
 * chime at each transition. Poses that carry a `pace` (the pranayama ones)
 * hand their whole step over to the breath pacer instead of running a plain
 * countdown - the practice *is* the timing there.
 *
 * A pose marked `sides: true` runs its timer twice with a switch cue in
 * between, so the authored duration always means "per side".
 */
(function (YG) {
  'use strict';

  var RING_R = 52;
  var RING_C = 2 * Math.PI * RING_R;

  /* Alternating the two shapes of Cat-Cow at roughly breath pace. Slow enough
     to follow, and only used by poses that declare a second figure. */
  var FIG_SWAP_MS = 3800;

  function icon(path) {
    return '<svg viewBox="0 0 24 24" aria-hidden="true">' + path + '</svg>';
  }
  var I_PAUSE = icon('<path d="M8 5h3v14H8zM13 5h3v14h-3z"/>');
  var I_PLAY = icon('<path d="M8 5l11 7-11 7z"/>');
  var I_PREV = icon('<path d="M7 6h2v12H7zM19 6v12l-9-6z"/>');
  var I_NEXT = icon('<path d="M15 6h2v12h-2zM5 6l9 6-9 6z"/>');
  var I_CLOSE = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" ' +
                'stroke-width="2" stroke-linecap="round"><path d="M6 6l12 12M18 6L6 18"/></svg>';

  function fmt(sec) {
    sec = Math.max(0, Math.round(sec));
    var m = Math.floor(sec / 60);
    var s = sec % 60;
    return m + ':' + (s < 10 ? '0' : '') + s;
  }

  var Player = {

    /**
     * @param built    output of YG.Safety.buildSession
     * @param meta     {sessionName, conditionId, sessionId}
     * @param onExit   called with {completed, seconds}
     */
    open: function (built, meta, onExit) {
      if (!built.steps.length) return;

      var root = document.createElement('div');
      root.className = 'player';
      root.innerHTML =
        '<div class="player-head">' +
          '<button class="iconbtn" data-act="close" aria-label="End session">' + I_CLOSE + '</button>' +
          '<div class="grow">' +
            '<div class="small" style="font-weight:650" data-el="stepcount"></div>' +
            '<div class="tiny muted" data-el="remaining"></div>' +
          '</div>' +
        '</div>' +
        '<div class="player-progress"><i data-el="bar" style="width:0%"></i></div>' +
        '<div class="player-stage">' +
          '<div class="figwrap" data-el="fig"></div>' +
          '<div class="player-side" data-el="side"></div>' +
          '<div class="player-name" data-el="name"></div>' +
          '<div class="player-sanskrit" data-el="sanskrit"></div>' +
          '<div class="player-cue" data-el="cue"></div>' +
        '</div>' +
        '<div class="player-controls">' +
          '<button class="ctrl" data-act="prev" aria-label="Previous pose">' + I_PREV + '</button>' +
          '<button class="ctrl main" data-act="toggle" aria-label="Pause">' + I_PAUSE + '</button>' +
          '<button class="ctrl" data-act="next" aria-label="Next pose">' + I_NEXT + '</button>' +
        '</div>';

      document.body.appendChild(root);

      var el = {};
      Array.prototype.forEach.call(root.querySelectorAll('[data-el]'), function (n) {
        el[n.getAttribute('data-el')] = n;
      });

      /* ------------------------------------------------------------ state */

      var steps = built.steps;
      var index = 0;
      var side = 0;              // 0 = first side, 1 = second, for `sides` poses
      var remaining = 0;
      var paused = false;
      var practised = 0;         // seconds actually spent, for the log
      var ticker = 0;
      var figTimer = 0;
      var figAlt = false;
      var breath = null;
      var finished = false;

      var totalSec = built.totalSec;

      YG.Native.keepAwake(true);
      YG.Native.immersive(true);
      YG.Audio.unlock();

      /* ------------------------------------------------------------- view */

      function ringHTML() {
        return '<div class="ring">' +
          '<svg viewBox="0 0 120 120">' +
            '<circle class="track" cx="60" cy="60" r="' + RING_R + '"/>' +
            '<circle class="bar" cx="60" cy="60" r="' + RING_R + '" ' +
              'stroke-dasharray="' + RING_C.toFixed(1) + '" stroke-dashoffset="0"/>' +
          '</svg>' +
          '<div class="label" data-el="ringlabel">0</div>' +
        '</div>';
      }

      function renderStep() {
        var step = steps[index];
        var pose = step.pose;

        el.stepcount.textContent = 'Pose ' + (index + 1) + ' of ' + steps.length;
        el.name.textContent = pose.name;
        el.sanskrit.textContent = pose.sanskrit || '';
        el.sanskrit.style.display = pose.sanskrit ? '' : 'none';
        el.cue.textContent = pose.breath || '';
        el.side.textContent = pose.sides ? (side === 0 ? 'First side' : 'Second side') : '';

        drawFigure(false);
        scheduleFigSwap(pose);

        // A substituted pose says so, once, where the user is looking anyway.
        if (step.from) {
          el.cue.textContent = 'Swapped in for ' + step.from.name + ' — ' + step.reason +
                               '. ' + (pose.breath || '');
        }
      }

      function drawFigure(useAlt) {
        var pose = steps[index].pose;
        var name = (useAlt && pose.fig2) ? pose.fig2 : pose.fig;
        el.fig.innerHTML = YG.figureFor(name, { label: pose.name });
      }

      function scheduleFigSwap(pose) {
        clearInterval(figTimer);
        figAlt = false;
        if (!pose.fig2) return;
        figTimer = setInterval(function () {
          if (paused) return;
          figAlt = !figAlt;
          drawFigure(figAlt);
        }, FIG_SWAP_MS);
      }

      function updateProgress() {
        var done = 0;
        for (var i = 0; i < index; i++) {
          done += steps[i].pose.sides ? steps[i].sec * 2 : steps[i].sec;
        }
        if (steps[index].pose.sides && side === 1) done += steps[index].sec;
        done += Math.max(0, steps[index].sec - remaining);

        el.bar.style.width = Math.min(100, (done / totalSec) * 100).toFixed(1) + '%';
        el.remaining.textContent = fmt(Math.max(0, totalSec - done)) + ' left';
      }

      function setRing(sec, total) {
        var label = root.querySelector('[data-el="ringlabel"]');
        var bar = root.querySelector('.ring .bar');
        if (!label || !bar) return;
        label.textContent = String(Math.ceil(sec));
        var frac = total > 0 ? Math.max(0, Math.min(1, sec / total)) : 0;
        bar.setAttribute('stroke-dashoffset', (RING_C * (1 - frac)).toFixed(1));
      }

      /* --------------------------------------------------------- stepping */

      function beginStep() {
        clearInterval(ticker);
        if (breath) { breath.stop(); breath = null; }

        var step = steps[index];
        var pose = step.pose;
        renderStep();

        YG.Audio.play('step');
        YG.Native.vibrate(35);
        announce(pose, step);

        if (pose.pace) {
          startBreathStep(pose, step);
        } else {
          startTimedStep(step);
        }
        updateProgress();
      }

      function announce(pose, step) {
        var text = pose.name;
        if (pose.sides) text += ', ' + (side === 0 ? 'first side' : 'second side');
        if (step.from) text += '. Adjusted for you.';
        if (pose.steps && pose.steps.length) text += '. ' + pose.steps[0];
        YG.Native.speak(text);
      }

      function startBreathStep(pose, step) {
        // The pacer replaces the ring entirely: two competing countdowns on one
        // screen is worse than either alone.
        el.fig.innerHTML = '';
        clearInterval(figTimer);
        var host = document.createElement('div');
        el.fig.appendChild(host);

        breath = YG.Breath.create(host, pose.pace, {
          duration: step.sec,
          announce: false,
          onDone: nextStep
        });
        breath.start();

        remaining = step.sec;
        ticker = setInterval(function () {
          if (paused) return;
          remaining--;
          practised++;
          updateProgress();
        }, 1000);
      }

      function startTimedStep(step) {
        remaining = step.sec;
        var host = document.createElement('div');
        host.innerHTML = ringHTML();
        el.fig.parentNode.insertBefore(host.firstChild, el.side);
        setRing(remaining, step.sec);

        ticker = setInterval(function () {
          if (paused) return;
          remaining--;
          practised++;
          setRing(remaining, step.sec);
          updateProgress();
          // Spoken at three seconds, not one: a cue that lands as the timer
          // hits zero arrives too late to act on.
          if (remaining === 3 && step.sec > 12) YG.Native.speak('Release');
          if (remaining <= 0) nextStep();
        }, 1000);
      }

      function clearRing() {
        var ring = root.querySelector('.ring');
        if (ring && ring.parentNode) ring.parentNode.removeChild(ring);
      }

      function nextStep() {
        clearInterval(ticker);
        if (breath) { breath.stop(); breath = null; }
        clearRing();

        var pose = steps[index].pose;
        if (pose.sides && side === 0) {
          side = 1;
          YG.Native.speak('Change sides');
          beginStep();
          return;
        }

        side = 0;
        index++;
        if (index >= steps.length) { complete(); return; }
        beginStep();
      }

      function prevStep() {
        clearInterval(ticker);
        if (breath) { breath.stop(); breath = null; }
        clearRing();

        if (steps[index].pose.sides && side === 1) {
          side = 0;
        } else if (index > 0) {
          index--;
          side = 0;
        }
        beginStep();
      }

      function togglePause() {
        paused = !paused;
        var btn = root.querySelector('[data-act="toggle"]');
        btn.innerHTML = paused ? I_PLAY : I_PAUSE;
        btn.setAttribute('aria-label', paused ? 'Resume' : 'Pause');
        if (breath) { if (paused) breath.pause(); else breath.resume(); }
        if (paused) YG.Native.stopSpeaking();
        YG.Native.keepAwake(!paused);
      }

      /* ----------------------------------------------------------- ending */

      function complete() {
        if (finished) return;
        finished = true;
        YG.Audio.play('end');
        YG.Native.speak('Practice complete. Take a moment before you get up.');
        teardown();
        if (onExit) onExit({ completed: true, seconds: practised });
      }

      function quit() {
        if (finished) return;
        finished = true;
        teardown();
        // Under a minute is a mis-tap or a look around, not a practice worth
        // logging - counting it would inflate the streak into meaninglessness.
        if (onExit) onExit({ completed: false, seconds: practised >= 60 ? practised : 0 });
      }

      function teardown() {
        clearInterval(ticker);
        clearInterval(figTimer);
        if (breath) { breath.stop(); breath = null; }
        YG.Native.stopSpeaking();
        YG.Native.keepAwake(false);
        YG.Native.immersive(false);
        if (root.parentNode) root.parentNode.removeChild(root);
        YG.player = null;
      }

      /* ------------------------------------------------------------ input */

      root.addEventListener('click', function (ev) {
        var btn = ev.target.closest('[data-act]');
        if (!btn) return;
        switch (btn.getAttribute('data-act')) {
          case 'close': confirmQuit(); break;
          case 'toggle': togglePause(); break;
          case 'next': nextStep(); break;
          case 'prev': prevStep(); break;
        }
      });

      function confirmQuit() {
        if (!paused) togglePause();
        YG.UI.confirmSheet({
          title: 'End this session?',
          body: practised >= 60
            ? 'Your ' + Math.round(practised / 60) + ' minutes so far will still be logged.'
            : 'Nothing will be logged - you have only just started.',
          confirm: 'End session',
          cancel: 'Keep going',
          onConfirm: quit,
          onCancel: function () { if (paused) togglePause(); }
        });
      }

      YG.player = {
        quit: confirmQuit,
        isOpen: function () { return !finished; }
      };

      beginStep();
    }
  };

  YG.Player = Player;
})(window.YG = window.YG || {});
