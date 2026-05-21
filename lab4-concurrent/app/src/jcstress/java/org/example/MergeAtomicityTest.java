package org.example;

import org.openjdk.jcstress.annotations.*;
import org.openjdk.jcstress.infra.results.II_Result;

@JCStressTest
@Outcome(id = "1, 2", expect = Expect.ACCEPTABLE, desc = "actor1 first: inserted 1, actor2 accumulated to 2")
@Outcome(id = "2, 1", expect = Expect.ACCEPTABLE, desc = "actor2 first: inserted 1, actor1 accumulated to 2")
@Outcome(id = "1, 1", expect = Expect.FORBIDDEN, desc = "Lost update: both actors think they inserted fresh")
@Outcome(id = "2, 2", expect = Expect.FORBIDDEN, desc = "Impossible double accumulation without prior insert")
@State
public class MergeAtomicityTest {

    private final CustomHashMap<Integer, Integer> map = new CustomHashMap<>();

    @Actor
    public void actor1(II_Result r) {
        r.r1 = map.merge(1, 1, Integer::sum);
    }

    @Actor
    public void actor2(II_Result r) {
        r.r2 = map.merge(1, 1, Integer::sum);
    }
}
