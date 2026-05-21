package org.example;

import org.openjdk.jcstress.annotations.*;
import org.openjdk.jcstress.infra.results.II_Result;

@JCStressTest
@Outcome(id = "1, 1", expect = Expect.ACCEPTABLE, desc = "Both inserts visible — correct")
@Outcome(id = "0, 1", expect = Expect.FORBIDDEN, desc = "key=1 lost")
@Outcome(id = "1, 0", expect = Expect.FORBIDDEN, desc = "key=2 lost")
@Outcome(id = "0, 0", expect = Expect.FORBIDDEN, desc = "Both inserts lost")
@State
public class NoLostUpdateTest {

    private final CustomHashMap<Integer, Integer> map = new CustomHashMap<>();

    @Actor
    public void insertKey1() {
        map.put(1, 1);
    }

    @Actor
    public void insertKey2() {
        map.put(2, 2);
    }

    @Arbiter
    public void checkBothPresent(II_Result r) {
        r.r1 = map.get(1) != null ? 1 : 0;
        r.r2 = map.get(2) != null ? 1 : 0;
    }
}
