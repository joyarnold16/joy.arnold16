using System.Collections.Generic;
using System.Text;
using Blastgrid.Sim;
using NUnit.Framework;

/// <summary>
/// Mirrors the checks the web build is held to, so the port can be shown to
/// behave the same rather than merely to compile. If one of these fails, the
/// C# and the JS have drifted apart and a daily run would lay down different
/// sectors on the two.
/// </summary>
public class LevelGenTests
{
    private const int Rows = 21;
    private const int Palette = 8;

    [TearDown]
    public void Restore() { UnityRandomBridge.Reset(); }

    private static string Signature(Level lv)
    {
        var sb = new StringBuilder();
        for (int i = 0; i < lv.Grid.Length; i++) sb.Append((int)lv.Grid[i]);
        sb.Append('#');
        for (int i = 0; i < lv.BrickColor.Length; i++) sb.Append(lv.BrickColor[i]).Append(',');
        sb.Append('#').Append(lv.ExitX).Append(',').Append(lv.ExitY).Append('#');
        foreach (var d in lv.Drones) sb.Append(d.X).Append(',').Append(d.Y).Append(',').Append(d.Kind).Append('|');
        return sb.ToString();
    }

    private static Level Daily(int sector, string date)
    {
        return LevelGen.Build(sector, Rows, Palette, true, Rng.SeedOf(date));
    }

    // ---- the generator itself ------------------------------------------------

    [Test]
    public void SameSeedGivesTheSameSequence()
    {
        var a = new Rng(Rng.SeedOf("2026-11-03"));
        var b = new Rng(Rng.SeedOf("2026-11-03"));
        for (int i = 0; i < 2000; i++) Assert.AreEqual(a.NextDouble(), b.NextDouble());
    }

    [Test]
    public void ValuesStayInRange()
    {
        var r = new Rng(12345u);
        for (int i = 0; i < 5000; i++)
        {
            double v = r.NextDouble();
            Assert.GreaterOrEqual(v, 0.0);
            Assert.Less(v, 1.0);
        }
    }

    [Test]
    public void DistributionIsRoughlyUniform()
    {
        var r = new Rng(Rng.SeedOf("2026-11-03"));
        var buckets = new int[10];
        for (int i = 0; i < 20000; i++) buckets[(int)(r.NextDouble() * 10)]++;
        foreach (int b in buckets) Assert.Greater(b, 1500, "a decile came out starved");
    }

    [Test]
    public void DateKeyIsZeroPaddedAndLocal()
    {
        Assert.AreEqual("2026-01-05", Rng.DayKey(new System.DateTime(2026, 1, 5)));
        Assert.AreEqual("2026-11-03", Rng.DayKey(new System.DateTime(2026, 11, 3, 23, 30, 0)));
        Assert.AreNotEqual(Rng.SeedOf("2026-11-03"), Rng.SeedOf("2026-11-04"));
    }

    // ---- the daily promise ---------------------------------------------------

    [Test]
    public void ADailyLaysDownTheSameSectorEveryTime()
    {
        string a = Signature(Daily(1, "2026-11-03"));
        Assert.AreEqual(a, Signature(Daily(1, "2026-11-03")));
        Assert.AreEqual(a, Signature(Daily(1, "2026-11-03")));
    }

    [Test]
    public void AnotherDateIsAnotherSector()
    {
        Assert.AreNotEqual(Signature(Daily(1, "2026-11-03")), Signature(Daily(1, "2026-11-04")));
    }

    [Test]
    public void EachSectorDiffersButStillReproduces()
    {
        string one = Signature(Daily(1, "2026-11-03"));
        string five = Signature(Daily(5, "2026-11-03"));
        Assert.AreNotEqual(one, five);
        Assert.AreEqual(five, Signature(Daily(5, "2026-11-03")));
    }

    [Test]
    public void AnOrdinaryRunStaysRandom()
    {
        var seen = new HashSet<string>();
        for (int i = 0; i < 3; i++)
            seen.Add(Signature(LevelGen.Build(1, Rows, Palette, false, 0u)));
        Assert.AreEqual(3, seen.Count, "three ordinary builds produced a repeat");
    }

    // ---- what the player walks into -----------------------------------------

    [Test]
    public void TheSpawnPocketIsNeverBoxedIn()
    {
        for (int i = 0; i < 300; i++)
        {
            var lv = LevelGen.Build(1, Rows, Palette, false, 0u);
            Assert.AreEqual(Tile.Empty, lv.At(1, 1), "spawn tile blocked");
            Assert.AreEqual(Tile.Empty, lv.At(2, 1), "spawn exit blocked");
            Assert.AreEqual(Tile.Empty, lv.At(1, 2), "spawn exit blocked");
        }
    }

    [Test]
    public void SectorOneOpensWithAPalletPocket()
    {
        int total = 0;
        const int runs = 60;
        for (int i = 0; i < runs; i++)
        {
            var lv = LevelGen.Build(1, Rows, Palette, false, 0u);
            for (int y = 1; y <= 5; y++)
                for (int x = 1; x <= 5; x++)
                    if (lv.At(x, y) == Tile.Flam) total++;
        }
        Assert.Greater(total / (double)runs, 4.0, "sector 1 opened without a pocket to blow up");
    }

    [Test]
    public void LaterSectorsDoNotGetThePocket()
    {
        int total = 0;
        const int runs = 60;
        for (int i = 0; i < runs; i++)
        {
            var lv = LevelGen.Build(2, Rows, Palette, false, 0u);
            for (int y = 1; y <= 5; y++)
                for (int x = 1; x <= 5; x++)
                    if (lv.At(x, y) == Tile.Flam) total++;
        }
        Assert.Less(total / (double)runs, 3.0, "the opening pocket leaked into sector 2");
    }

    [Test]
    public void ThePatrolGrowsWithDepthAndIsCapped()
    {
        Assert.AreEqual(3, LevelGen.Build(1, Rows, Palette, false, 0u).Drones.Count);
        Assert.AreEqual(4, LevelGen.Build(6, Rows, Palette, false, 0u).Drones.Count);
        Assert.AreEqual(10, LevelGen.Build(99, Rows, Palette, false, 0u).Drones.Count);
    }

    [Test]
    public void DronesNeverSpawnInThePlayersCorner()
    {
        for (int i = 0; i < 200; i++)
            foreach (var d in LevelGen.Build(1, Rows, Palette, false, 0u).Drones)
                Assert.Greater(d.X + d.Y, SimConfig.DroneMinSpawnSum, "a drone spawned on top of the player");
    }

    [Test]
    public void TheArenaIsFilledNotBare()
    {
        int destructible = 0, empty = 0;
        const int runs = 60;
        for (int i = 0; i < runs; i++)
        {
            var lv = LevelGen.Build(1, Rows, Palette, false, 0u);
            for (int y = 1; y < Rows - 1; y++)
                for (int x = 1; x < SimConfig.Cols - 1; x++)
                {
                    var t = lv.At(x, y);
                    if (t == Tile.Brick || t == Tile.Flam) destructible++;
                    else if (t == Tile.Empty) empty++;
                }
        }
        double filled = destructible / (double)(destructible + empty);
        Assert.Greater(filled, 0.30, "sector 1 came out as bare floor again");
        Assert.Less(filled, 0.50, "sector 1 came out wall to wall");
    }

    [Test]
    public void TheExitIsBuriedAndFarFromTheSpawn()
    {
        for (int i = 0; i < 100; i++)
        {
            var lv = LevelGen.Build(1, Rows, Palette, false, 0u);
            Assert.IsFalse(lv.ExitOpen, "the exit started already open");
            Assert.AreEqual(Tile.Brick, lv.At(lv.ExitX, lv.ExitY), "the exit is not under a brick");
            Assert.Greater(lv.ExitX + lv.ExitY, 8, "the exit landed next to the spawn");
        }
    }
}
