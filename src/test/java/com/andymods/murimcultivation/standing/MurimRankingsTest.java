package com.andymods.murimcultivation.standing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MurimRankingsTest {

    private static List<String> names(List<MurimRankings.Entry> entries) {
        return entries.stream().map(MurimRankings.Entry::name).toList();
    }

    @Test
    void realmOutranksEverythingBeneathIt() {
        List<MurimRankings.Entry> ranked = MurimRankings.top(List.of(
                new MurimRankings.Entry("low_peak", 2, 3, 999.0D, 100),
                new MurimRankings.Entry("high_early", 3, 0, 0.0D, 0)), 10);
        assertEquals(List.of("high_early", "low_peak"), names(ranked));
    }

    @Test
    void thenSubstageThenProgressThenHonour() {
        List<MurimRankings.Entry> ranked = MurimRankings.top(List.of(
                new MurimRankings.Entry("a", 3, 1, 10.0D, 0),
                new MurimRankings.Entry("b", 3, 2, 0.0D, 0),
                new MurimRankings.Entry("c", 3, 1, 10.0D, 50),
                new MurimRankings.Entry("d", 3, 1, 20.0D, 0)), 10);
        assertEquals(List.of("b", "d", "c", "a"), names(ranked));
    }

    @Test
    void aDeadHeatIsBrokenByNameSoTheOrderIsStable() {
        List<MurimRankings.Entry> ranked = MurimRankings.top(List.of(
                new MurimRankings.Entry("zed", 1, 0, 0.0D, 0),
                new MurimRankings.Entry("amy", 1, 0, 0.0D, 0)), 10);
        assertEquals(List.of("amy", "zed"), names(ranked));
    }

    @Test
    void theListIsCut() {
        List<MurimRankings.Entry> many = List.of(
                new MurimRankings.Entry("a", 1, 0, 0.0D, 0),
                new MurimRankings.Entry("b", 2, 0, 0.0D, 0),
                new MurimRankings.Entry("c", 3, 0, 0.0D, 0));
        assertEquals(List.of("c", "b"), names(MurimRankings.top(many, 2)));
        assertEquals(List.of(), MurimRankings.top(many, 0));
        assertEquals(List.of(), MurimRankings.top(many, -1));
    }
}
