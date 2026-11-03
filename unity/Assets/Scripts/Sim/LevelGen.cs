using System.Collections.Generic;

namespace Blastgrid.Sim
{
    public struct DroneSpawn
    {
        public int X, Y;
        public DroneKind Kind;
    }

    /// <summary>One generated sector: the grid and everything placed on it.</summary>
    public sealed class Level
    {
        public int Cols, Rows;
        public Tile[] Grid;
        public byte[] ColumnHp;      // 255 = load-bearing edge, never falls
        public byte[] BrickColor;    // index into the renderer's palette
        public int ExitX, ExitY;
        public bool ExitOpen;
        public List<DroneSpawn> Drones = new List<DroneSpawn>();

        public int Index(int x, int y) { return y * Cols + x; }
        public Tile At(int x, int y) { return Grid[Index(x, y)]; }
    }

    /// <summary>
    /// Port of buildLevel() from the web build. Kept deliberately close to the
    /// original, including the order of random draws: a daily run must lay down
    /// the same sector in both, so reordering a single rnd() call would silently
    /// desynchronise them.
    /// </summary>
    public static class LevelGen
    {
        public static Level Build(int sector, int rows, int brickPaletteSize, bool daily, uint dailySeed)
        {
            var lv = new Level { Cols = SimConfig.Cols, Rows = rows };
            int cols = lv.Cols;
            int n = cols * rows;
            lv.Grid = new Tile[n];
            lv.ColumnHp = new byte[n];
            lv.BrickColor = new byte[n];

            // Load-bearing frame plus the fixed pillar lattice on even/even.
            for (int y = 0; y < rows; y++)
            {
                for (int x = 0; x < cols; x++)
                {
                    bool edge = x == 0 || y == 0 || x == cols - 1 || y == rows - 1;
                    bool pillar = (x % 2 == 0) && (y % 2 == 0);
                    bool solid = edge || pillar;
                    int i = y * cols + x;
                    lv.Grid[i] = solid ? Tile.Solid : Tile.Empty;
                    if (solid) lv.ColumnHp[i] = edge ? (byte)255 : (byte)3;
                }
            }

            // A daily has to lay out the same grid for everyone, so the generator -
            // and only the generator - runs off a seed from the date and the sector.
            Rng rng = null;
            if (daily) rng = new Rng(unchecked(dailySeed + (uint)sector * SimConfig.SectorSeedMix));
            System.Func<double> rnd = daily
                ? (System.Func<double>)(() => rng.NextDouble())
                : () => UnityRandomBridge.Value();

            double density = SimConfig.Clamp(
                SimConfig.DensityBase + sector * SimConfig.DensityPerSector,
                SimConfig.DensityBase, SimConfig.DensityMax);

            var bricks = new List<int>();   // packed x*1000+y, kept in draw order
            for (int y = 1; y < rows - 1; y++)
            {
                for (int x = 1; x < cols - 1; x++)
                {
                    int i = y * cols + x;
                    if (lv.Grid[i] != Tile.Empty || SimConfig.IsSafeCell(x, y)) continue;
                    double r = rnd();
                    if (r < density)
                    {
                        lv.Grid[i] = Tile.Brick;
                        lv.BrickColor[i] = (byte)(int)(rnd() * brickPaletteSize);
                        bricks.Add(x * 1000 + y);
                    }
                    else if (r < density + SimConfig.PalletBand)
                    {
                        lv.Grid[i] = Tile.Flam;
                    }
                }
            }

            // Sector 1 only: pallets near the spawn so the opening blast spreads.
            // Pallets rather than more bricks on purpose - this fills floor that
            // was empty anyway. The spawn corner itself stays walkable.
            if (sector == 1)
            {
                for (int y = 1; y <= SimConfig.OpeningPocketSpan; y++)
                {
                    for (int x = 1; x <= SimConfig.OpeningPocketSpan; x++)
                    {
                        if (SimConfig.IsSafeCell(x, y) || x + y < SimConfig.OpeningPocketMinSum) continue;
                        int j = y * cols + x;
                        if (lv.Grid[j] == Tile.Empty && rnd() < SimConfig.OpeningPalletChance)
                            lv.Grid[j] = Tile.Flam;
                    }
                }
            }

            // The exit hides under one of the bricks furthest from the spawn.
            if (bricks.Count > 0)
            {
                // JS Array.sort is stable, List.Sort is not: where two bricks sit
                // the same distance out, an unstable sort could pick a different
                // one and the daily would quietly lay down a different exit than
                // the web build. Tie-breaking on insertion order makes it stable.
                var order = new List<int>(bricks.Count);
                for (int k = 0; k < bricks.Count; k++) order.Add(k);
                order.Sort((ia, ib) =>
                {
                    int a = bricks[ia], b = bricks[ib];
                    int sa = (a / 1000) + (a % 1000);
                    int sb = (b / 1000) + (b % 1000);
                    if (sa != sb) return sb - sa;          // descending, as in the web build
                    return ia - ib;                       // stable: first drawn stays first
                });
                var far = new List<int>(bricks.Count);
                for (int k = 0; k < order.Count; k++) far.Add(bricks[order[k]]);
                int span = far.Count < SimConfig.ExitCandidates ? far.Count : SimConfig.ExitCandidates;
                int pick = far[(int)(rnd() * span)];
                lv.ExitX = pick / 1000;
                lv.ExitY = pick % 1000;
                lv.ExitOpen = false;
            }
            else
            {
                lv.ExitX = cols - 2;
                lv.ExitY = rows - 2;
                lv.ExitOpen = true;                        // nothing to dig through
            }

            // Patrol. Never in the player's corner.
            var spots = new List<int>();
            for (int y = 1; y < rows - 1; y++)
                for (int x = 1; x < cols - 1; x++)
                    if (lv.Grid[y * cols + x] == Tile.Empty && x + y > SimConfig.DroneMinSpawnSum)
                        spots.Add(x * 1000 + y);

            int want = SimConfig.DronesBase + sector / SimConfig.DronesPerSectors;
            if (want > SimConfig.DronesMax) want = SimConfig.DronesMax;

            for (int i = 0; i < want && spots.Count > 0; i++)
            {
                int at = (int)(rnd() * spots.Count);
                int cell = spots[at];
                spots.RemoveAt(at);                        // splice(), same semantics

                var kind = DroneKind.Drift;
                if (sector >= SimConfig.GhostFromSector && rnd() < 0.15) kind = DroneKind.Ghost;
                else if (sector >= SimConfig.SeekFromSector && rnd() < 0.15 + sector * 0.01) kind = DroneKind.Seek;

                lv.Drones.Add(new DroneSpawn { X = cell / 1000, Y = cell % 1000, Kind = kind });
            }

            return lv;
        }
    }

    /// <summary>
    /// The one seam where the sim would otherwise need UnityEngine. Tests swap in
    /// a deterministic source; the game points it at UnityEngine.Random.
    /// </summary>
    public static class UnityRandomBridge
    {
        private static System.Func<double> _source = NewSystemRandom();

        public static double Value() { return _source(); }

        public static void Use(System.Func<double> source) { _source = source; }

        public static void Reset() { _source = NewSystemRandom(); }

        private static System.Func<double> NewSystemRandom()
        {
            var r = new System.Random();
            return () => r.NextDouble();
        }
    }
}
