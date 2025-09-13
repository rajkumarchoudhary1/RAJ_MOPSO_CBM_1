package raj.cbm.mopso;

import raj.cbm.core.ExperimentParams;
import raj.cbm.core.Plan;
import raj.cbm.eval.SLAEvaluator;

import java.util.Random;

/** Minimal scaffold for MOPSO. */
public class MopsoOptimizer {

    public static class Result {
        public final SLAEvaluator.Metrics metrics; public final Plan plan;
        public Result(SLAEvaluator.Metrics m, Plan p) { metrics = m; plan = p; }
    }

    public Result run(ExperimentParams ep) {
        Random rnd = new Random(ep.seed);
        Plan plan = new Plan();
        // Example: reserve more CPU to class A & B VMs
        plan.setVmCap(0, 0.85);
        plan.setVmCap(1, 0.70);
        plan.setVmCap(2, 0.45);
        plan.setVmCap(3, 0.35);
        // Keep default mapping A->0,B->1,C->2,D->3
        SLAEvaluator.Metrics m = SLAEvaluator.evaluate(ep, plan);
        return new Result(m, plan);
    }
}
