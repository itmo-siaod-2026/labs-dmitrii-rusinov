package org.example;

import org.openjdk.jcstress.annotations.*;
import org.openjdk.jcstress.infra.results.II_Result;

@JCStressTest
@Outcome(id = "0, 1", expect = Expect.ACCEPTABLE, desc = "actor2 ran before put completed; final state is correct")
@Outcome(id = "1, 1", expect = Expect.ACCEPTABLE, desc = "actor2 observed the put; final state is correct")
@Outcome(id = "0, 0", expect = Expect.FORBIDDEN, desc = "Put was lost: not visible after both actors finished")
@Outcome(id = "1, 0", expect = Expect.FORBIDDEN, desc = "Impossible: value seen mid-execution but lost afterward")
@State
public class PutGetVisibilityTest {

    private final CustomHashMap<Integer, Integer> map = new CustomHashMap<>();

    @Actor
    public void writer() {
        map.put(1, 1);
    }

    @Actor
    public void reader(II_Result r) {
        r.r1 = map.get(1) != null ? 1 : 0;
    }

    @Arbiter
    public void finalState(II_Result r) {
        r.r2 = map.get(1) != null ? 1 : 0;
    }
}
