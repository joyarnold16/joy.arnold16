/*
 * Bootstrap.
 *
 * Decides the first screen, mounts the UI, and exposes window.yg.onBack for
 * MainActivity to call on the hardware back button.
 */
(function (YG) {
  'use strict';

  function firstScreen() {
    var p = YG.Store.profile();
    if (!YG.Store.disclaimerAccepted()) return 'disclaimer';
    if (!p.onboarded) return 'onboarding';
    return 'today';
  }

  function start() {
    YG.UI.mount(document.getElementById('screen'), document.getElementById('tabs'));
    YG.UI.go(firstScreen());

    // Re-arm on every launch. WorkManager persists the request, but a reinstall
    // or a "clear data" wipes its database while localStorage may survive - so
    // trusting the stored preference over the stored schedule is the safe way
    // round.
    var r = YG.Store.profile().reminder;
    if (r && r.on && YG.applyReminder) YG.applyReminder();

    // The first tap anywhere unlocks the audio context. Autoplay policy blocks
    // it until then, and the first chime the user needs is one they did not tap
    // for - the transition out of the first pose.
    document.addEventListener('click', function once() {
      YG.Audio.unlock();
      document.removeEventListener('click', once);
    }, { once: true });
  }

  /**
   * Android's back button, in priority order: leave the player, close a sheet,
   * pop the screen stack. Returning false lets MainActivity apply its
   * press-twice-to-exit rule.
   */
  window.yg = {
    onBack: function () {
      if (YG.player && YG.player.isOpen()) { YG.player.quit(); return true; }
      if (YG.activeSheet) { YG.activeSheet.close(); return true; }
      return YG.UI.back();
    }
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', start);
  } else {
    start();
  }
})(window.YG = window.YG || {});
