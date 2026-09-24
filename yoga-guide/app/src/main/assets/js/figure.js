/*
 * Pose illustration renderer.
 *
 * Every pose in this app is drawn as line art from a handful of joint
 * coordinates rather than shipped as an image. Two reasons that matters:
 * the whole illustration set costs a few hundred KB of text instead of tens of
 * megabytes of PNG, and 70 poses drawn by one renderer are automatically
 * consistent with each other in a way that 70 separately-sourced images never
 * are. It also keeps the repository text-only, which is what makes it
 * manageable from a phone.
 *
 * Coordinate space is a fixed 120 x 100 viewBox with the floor at y=92, so
 * figures stay in proportion across poses. See js/figures.js for the data.
 */
(function (YG) {
  'use strict';

  var VIEW_W = 120;
  var VIEW_H = 100;

  function num(n) {
    // Three decimals is well past what a 120-unit viewBox can resolve, but it
    // keeps generated paths from carrying 15 digits of float noise.
    return Math.round(n * 1000) / 1000;
  }

  /** Straight polyline through the points. */
  function polyPath(pts) {
    var d = 'M' + num(pts[0][0]) + ',' + num(pts[0][1]);
    for (var i = 1; i < pts.length; i++) {
      d += 'L' + num(pts[i][0]) + ',' + num(pts[i][1]);
    }
    return d;
  }

  /**
   * Catmull-Rom through the points, emitted as cubic beziers.
   *
   * Used for spines and other genuinely curved lines. Limbs stay as polylines:
   * smoothing an elbow rounds it away, and a rounded elbow reads as a wrong
   * pose rather than as a stylistic choice.
   */
  function smoothPath(pts, tension) {
    if (pts.length < 3) return polyPath(pts);
    var t = tension == null ? 0.5 : tension;
    var d = 'M' + num(pts[0][0]) + ',' + num(pts[0][1]);
    for (var i = 0; i < pts.length - 1; i++) {
      var p0 = pts[i > 0 ? i - 1 : 0];
      var p1 = pts[i];
      var p2 = pts[i + 1];
      var p3 = pts[i + 2 < pts.length ? i + 2 : pts.length - 1];
      var c1x = p1[0] + ((p2[0] - p0[0]) / 6) * t;
      var c1y = p1[1] + ((p2[1] - p0[1]) / 6) * t;
      var c2x = p2[0] - ((p3[0] - p1[0]) / 6) * t;
      var c2y = p2[1] - ((p3[1] - p1[1]) / 6) * t;
      d += 'C' + num(c1x) + ',' + num(c1y) + ' ' + num(c2x) + ',' + num(c2y) +
           ' ' + num(p2[0]) + ',' + num(p2[1]);
    }
    return d;
  }

  function chainPath(chain) {
    var pts = chain.p || chain;
    if (pts.length < 2) return '';
    return chain.s ? smoothPath(pts, chain.s === true ? 0.5 : chain.s) : polyPath(pts);
  }

  function esc(s) {
    return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  function propSVG(pr) {
    switch (pr.t) {
      case 'rect':
        return '<rect class="fig-prop" x="' + num(pr.x) + '" y="' + num(pr.y) +
               '" width="' + num(pr.w) + '" height="' + num(pr.h) +
               '" rx="' + num(pr.r == null ? 1.5 : pr.r) + '"/>';
      case 'line':
        return '<path class="fig-prop" d="' + chainPath(pr.p) + '"/>';
      case 'dash':
        return '<path class="fig-prop fig-prop-dash" d="' + chainPath(pr.p) + '"/>';
      case 'circle':
        return '<circle class="fig-prop" cx="' + num(pr.c[0]) + '" cy="' + num(pr.c[1]) +
               '" r="' + num(pr.c[2]) + '"/>';
      case 'dot':
        return '<circle class="fig-dot" cx="' + num(pr.c[0]) + '" cy="' + num(pr.c[1]) +
               '" r="' + num(pr.c[2]) + '"/>';
      case 'flame':
        // Candle flame for Trataka - the one place a filled shape reads better
        // than an outline, because an outlined flame looks like a leaf.
        return '<path class="fig-flame" d="' + chainPath(pr.p) + '"/>';
      default:
        return '';
    }
  }

  /**
   * Renders a figure definition to an SVG string.
   *
   * Returns a string rather than nodes because every caller immediately assigns
   * it into innerHTML, and building 70 of these as DOM would be slower for no
   * benefit. Nothing here interpolates user input.
   */
  YG.figureSVG = function (fig, opts) {
    opts = opts || {};
    if (!fig) {
      return '<svg class="fig" viewBox="0 0 ' + VIEW_W + ' ' + VIEW_H +
             '" aria-hidden="true"></svg>';
    }

    var out = '';

    // Painting order: props and the far-side limbs sit behind the body so the
    // near arm reads as being in front of the torso.
    if (fig.pr) {
      for (var i = 0; i < fig.pr.length; i++) out += propSVG(fig.pr[i]);
    }
    if (fig.g != null) {
      out += '<path class="fig-ground" d="M6,' + num(fig.g) + 'L' + (VIEW_W - 6) + ',' +
             num(fig.g) + '"/>';
    }
    if (fig.f) {
      for (var j = 0; j < fig.f.length; j++) {
        out += '<path class="fig-far" d="' + chainPath(fig.f[j]) + '"/>';
      }
    }
    if (fig.b) {
      for (var k = 0; k < fig.b.length; k++) {
        out += '<path class="fig-body" d="' + chainPath(fig.b[k]) + '"/>';
      }
    }
    if (fig.h) {
      out += '<circle class="fig-head" cx="' + num(fig.h[0]) + '" cy="' + num(fig.h[1]) +
             '" r="' + num(fig.h[2]) + '"/>';
    }

    var label = opts.label ? ' role="img" aria-label="' + esc(opts.label) + '"'
                           : ' aria-hidden="true"';
    var cls = 'fig' + (opts.className ? ' ' + opts.className : '');
    // Supine poses occupy a thin band at the bottom of the shared 120x100 space.
    // Authoring them in that space keeps the floor line consistent with every
    // standing pose; `vb` then crops the empty upper two thirds so the drawing
    // does not float as a sliver in the middle of a tall card.
    var vb = fig.vb ? fig.vb.join(' ') : '0 0 ' + VIEW_W + ' ' + VIEW_H;
    return '<svg class="' + cls + '" viewBox="' + vb +
           '" preserveAspectRatio="xMidYMid meet"' + label + '>' + out + '</svg>';
  };

  /** Looks a figure up by name, falling back to a seated silhouette. */
  YG.figureFor = function (name, opts) {
    var fig = YG.FIGURES[name] || YG.FIGURES.sukhasana;
    return YG.figureSVG(fig, opts);
  };
})(window.YG = window.YG || {});
