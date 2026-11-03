namespace Blastgrid.Sim
{
    /// <summary>
    /// xorshift32. Small, fast, and plenty for laying out a grid.
    ///
    /// Only the level generator draws from this. Particles and fire keep
    /// UnityEngine.Random, because a daily run is about everyone getting the
    /// same *grid*, not the same sparks - and seeding those too would mean
    /// threading a generator through half the renderer for no gain.
    ///
    /// Ported from blastgrid.html's makeRng(), and deliberately bit-for-bit
    /// identical to it so a date produces the same sector in both.
    /// </summary>
    public sealed class Rng
    {
        private uint _s;

        public Rng(uint seed)
        {
            _s = seed != 0u ? seed : 0x9e3779b9u;
        }

        /// <summary>Next value in [0,1).</summary>
        public double NextDouble()
        {
            _s ^= _s << 13;
            _s ^= _s >> 17;
            _s ^= _s << 5;
            return _s / 4294967296.0;
        }

        /// <summary>Next integer in [0,maxExclusive).</summary>
        public int Next(int maxExclusive)
        {
            if (maxExclusive <= 0) return 0;
            return (int)(NextDouble() * maxExclusive);
        }

        /// <summary>
        /// FNV-1a over the date key, so "2026-11-03" always yields the same seed.
        /// Matches seedOf() in the web build.
        /// </summary>
        public static uint SeedOf(string s)
        {
            uint h = 2166136261u;
            for (int i = 0; i < s.Length; i++)
            {
                h ^= s[i];
                h *= 16777619u;
            }
            return h;
        }

        /// <summary>
        /// Local date, not UTC: the daily should roll over at the player's
        /// midnight, which is what the web build does.
        /// </summary>
        public static string DayKey(System.DateTime when)
        {
            return when.Year.ToString("D4") + "-" + when.Month.ToString("D2") + "-" + when.Day.ToString("D2");
        }
    }
}
