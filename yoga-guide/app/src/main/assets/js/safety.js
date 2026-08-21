/*
 * The contraindication filter.
 *
 * This is the part of the app that justifies its existence. A generic yoga app
 * shows everyone the same sequence and puts the warnings in a paragraph nobody
 * reads; here the user's health flags rewrite the sequence before they ever see
 * it, and the app says what it changed and why.
 *
 * Two rules the rest of the code depends on:
 *
 *   1. Substitution walks the alt chain. A pose blocked for one reason may have
 *      an alt blocked for another, so we keep walking until something is safe
 *      or the chain ends - at which point the step is dropped rather than
 *      quietly kept.
 *   2. Every removal or substitution is reported. A session that silently got
 *      shorter teaches the user nothing about their own body; one that says
 *      "Cobra swapped for Cat-Cow - you noted pregnancy" teaches them something
 *      they can use in any class they ever attend.
 */
(function (YG) {
  'use strict';

  var MAX_ALT_HOPS = 4;

  /* Beginners do not get advanced postures; everyone else does. Kept as a table
     rather than an inequality so the policy is visible and easy to change. */
  var MAX_LEVEL = { 1: 2, 2: 3, 3: 3 };

  var LENGTH_SCALE = { short: 0.6, medium: 1, long: 1.45 };

  function flagLabel(id) {
    for (var i = 0; i < YG.FLAGS.length; i++) {
      if (YG.FLAGS[i].id === id) return YG.FLAGS[i].label;
    }
    return id;
  }

  var Safety = {

    flagLabel: flagLabel,

    /** Which of the user's flags rule this pose out. */
    blockingFlags: function (pose, flags) {
      var out = [];
      var avoid = pose.avoid || [];
      for (var i = 0; i < avoid.length; i++) {
        if (flags[avoid[i]]) out.push(avoid[i]);
      }
      return out;
    },

    isBlocked: function (pose, flags) {
      return Safety.blockingFlags(pose, flags).length > 0;
    },

    tooAdvanced: function (pose, level) {
      return pose.level > (MAX_LEVEL[level] || 3);
    },

    /**
     * Resolves one pose against a profile.
     *
     * Returns {pose, from, reason} where `from` is the originally requested
     * pose when a substitution happened, or null when nothing is safe.
     */
    resolve: function (poseId, profile) {
      var flags = profile.flags || {};
      var level = profile.level || 1;
      var original = YG.POSE_BY_ID[poseId];
      if (!original) return null;

      var pose = original;
      var reason = null;

      for (var hop = 0; hop <= MAX_ALT_HOPS; hop++) {
        var blocking = Safety.blockingFlags(pose, flags);
        var advanced = Safety.tooAdvanced(pose, level);

        if (!blocking.length && !advanced) {
          return {
            pose: pose,
            from: pose === original ? null : original,
            reason: pose === original ? null : reason
          };
        }

        // Record why we are leaving this pose before hopping. Health reasons
        // outrank difficulty in the explanation, because that is the one the
        // user most needs to understand.
        reason = blocking.length
          ? blocking.map(flagLabel).join(', ')
          : 'too advanced for your current level';

        if (!pose.alt) break;
        var next = YG.POSE_BY_ID[pose.alt];
        if (!next || next === pose) break;
        pose = next;
      }

      return null;
    },

    /**
     * Builds the version of a session this user should actually do.
     *
     * @returns {steps, adjustments, totalSec}
     *   steps       [{pose, sec, from, reason}]
     *   adjustments [{type:'swap'|'drop', original, replacement, reason}]
     */
    buildSession: function (session, profile) {
      var scale = LENGTH_SCALE[profile.length] || 1;
      var steps = [];
      var adjustments = [];

      for (var i = 0; i < session.steps.length; i++) {
        var raw = session.steps[i];
        var res = Safety.resolve(raw.p, profile);
        var original = YG.POSE_BY_ID[raw.p];

        if (!res) {
          adjustments.push({
            type: 'drop',
            original: original,
            replacement: null,
            reason: original ? (Safety.blockingFlags(original, profile.flags || {})
                                      .map(flagLabel).join(', ') || 'not suitable at your level')
                             : 'unknown pose'
          });
          continue;
        }

        var sec = Math.round(raw.sec * scale);
        // Floors, not a single global minimum: eight seconds of Savasana is
        // not a short rest, it is a broken one.
        var floor = res.pose.type === 'relaxation' ? 60 : 15;
        if (sec < floor) sec = Math.min(floor, raw.sec);

        var prev = steps.length ? steps[steps.length - 1] : null;
        if (prev && prev.pose === res.pose) {
          // Many poses share a substitute - in the constipation routine, Cobra,
          // Bow and the wind-relieving pose all land on Cat-Cow for a pregnant
          // user. Pushed as separate steps that becomes "Cat-Cow, Cat-Cow,
          // Cat-Cow", which reads as a broken app rather than a careful one.
          // Fold the time into the step already running instead: "hold this one
          // a bit longer" is what a teacher would actually say. Capped, so a
          // long run of swaps cannot produce a five-minute hold.
          //
          // The cap is relative to the step already there, not to the pose's
          // default hold: sessions author their own durations (Cat-Cow runs 90s
          // in the prenatal sequences against a 60s default), and a cap derived
          // from the default would *shorten* a step it was supposed to extend.
          var cap = Math.max(prev.sec, res.pose.hold) * 2;
          prev.sec = Math.min(prev.sec + sec, cap);
          if (res.from) {
            adjustments.push({
              type: 'swap',
              original: res.from,
              replacement: res.pose,
              reason: res.reason,
              merged: true
            });
          }
          continue;
        }

        if (res.from) {
          adjustments.push({
            type: 'swap',
            original: res.from,
            replacement: res.pose,
            reason: res.reason
          });
        }

        steps.push({ pose: res.pose, sec: sec, from: res.from, reason: res.reason });
      }

      // Summed at the end rather than accumulated, because merging mutates the
      // duration of a step that was already counted.
      var totalSec = 0;
      for (var k = 0; k < steps.length; k++) {
        totalSec += steps[k].pose.sides ? steps[k].sec * 2 : steps[k].sec;
      }

      return { steps: steps, adjustments: adjustments, totalSec: totalSec };
    },

    /**
     * Extra warnings for a condition, drawn from the flags the user actually
     * has rather than from the condition's full safety list. Shown above a
     * session so the caveats that apply to this person are the prominent ones.
     */
    personalNotes: function (profile) {
      var notes = [];
      var flags = profile.flags || {};
      if (flags.pregnancy) {
        notes.push('Prone poses, deep twists, inversions and breath retention are ' +
                   'left out of every session while your profile says you are pregnant.');
        if (profile.trimester >= 2) {
          notes.push('From about 16 weeks, rest on your left side rather than flat on your back.');
        }
      }
      if (flags.hypertension || flags.heart) {
        notes.push('Inversions, head-below-heart poses and forceful breathing are left out.');
      }
      if (flags.glaucoma) {
        notes.push('Anything that raises pressure in the head or eyes is left out.');
      }
      if (flags.postnatal) {
        notes.push('Abdominal work is left out until you have been cleared and any ' +
                   'separation has been checked.');
      }
      if (flags.disc || flags.osteoporosis) {
        notes.push('Deep forward folds and strong twists are left out.');
      }
      if (flags.menstruation) {
        notes.push('Inversions are left out, as you asked.');
      }
      return notes;
    },

    /**
     * Annotates the pose library for browsing: everything stays visible, but
     * anything the user should not do is marked. Hiding poses outright would
     * make the library less useful as a reference - the point is to know what a
     * pose is and why it is not for you today.
     */
    annotate: function (pose, profile) {
      var blocking = Safety.blockingFlags(pose, profile.flags || {});
      return {
        pose: pose,
        blocked: blocking.length > 0,
        blockingLabels: blocking.map(flagLabel),
        advanced: Safety.tooAdvanced(pose, profile.level || 1)
      };
    },

    /** Poses that help a condition and are safe for this user, best first. */
    safePosesFor: function (conditionId, profile) {
      var out = [];
      for (var i = 0; i < YG.POSES.length; i++) {
        var p = YG.POSES[i];
        if ((p.helps || []).indexOf(conditionId) === -1) continue;
        if (Safety.isBlocked(p, profile.flags || {})) continue;
        if (Safety.tooAdvanced(p, profile.level || 1)) continue;
        out.push(p);
      }
      return out;
    }
  };

  YG.Safety = Safety;
})(window.YG = window.YG || {});
