namespace Blastgrid.Sim
{
    public enum Tile : byte { Empty = 0, Solid = 1, Brick = 2, Flam = 3 }

    public enum DroneKind { Drift, Seek, Ghost }

    /// <summary>
    /// Every tuned number lives here rather than scattered through the sim, so
    /// balance changes are one file. Values carried over from the shipped web
    /// build, which is the version these were actually playtested against.
    /// </summary>
    public static class SimConfig
    {
        public const int Cols = 13;

        // The web build derives rows from the screen aspect. Unity will pass its
        // own, so this is only the fallback and the clamp the generator trusts.
        public const int DefaultRows = 21;
        public const int MinRows = 9;
        public const int MaxRows = 31;

        // 0.16 left sector 1 three-quarters bare floor, which read as deserted
        // rather than open. 0.28 is 37% filled at sector 1, 50% by sector 40.
        public const double DensityBase = 0.28;
        public const double DensityPerSector = 0.0045;
        public const double DensityMax = 0.58;

        // Timber pallets sit in a thin band just past the brick threshold.
        public const double PalletBand = 0.06;

        // Sector 1 only: a pocket of pallets near the spawn, so the very first
        // charge catches and spreads instead of popping one brick.
        public const double OpeningPalletChance = 0.55;
        public const int OpeningPocketSpan = 5;
        public const int OpeningPocketMinSum = 5;

        public const int DronesBase = 3;
        public const int DronesPerSectors = 6;
        public const int DronesMax = 10;
        public const int SeekFromSector = 8;
        public const int GhostFromSector = 15;

        // The exit hides under one of the ten bricks furthest from the spawn.
        public const int ExitCandidates = 10;

        // Drones never spawn in the player's corner.
        public const int DroneMinSpawnSum = 6;

        // Mixes the sector into the daily seed so each floor differs while the
        // whole run still reproduces from one date.
        public const uint SectorSeedMix = 2654435761u;

        public static readonly string[] SafeCells = { "1,1", "2,1", "1,2", "3,1", "1,3" };

        public static bool IsSafeCell(int x, int y)
        {
            return (x == 1 && y == 1) || (x == 2 && y == 1) || (x == 1 && y == 2)
                || (x == 3 && y == 1) || (x == 1 && y == 3);
        }

        public static double Clamp(double v, double lo, double hi)
        {
            return v < lo ? lo : (v > hi ? hi : v);
        }
    }
}
