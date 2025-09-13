package raj.cbm.ga;

import raj.cbm.core.ExperimentParams;
import raj.cbm.core.Plan;
import raj.cbm.eval.SLAEvaluator;

/** Minimal scaffold for NSGA-II. */
public class NSGAIIOptimizer {
    public static class Result {
        public final SLAEvaluator.Metrics metrics; public final Plan plan;
        public Result(SLAEvaluator.Metrics m, Plan p) { metrics = m; plan = p; }
    }

    public Result run(ExperimentParams ep) {
        Plan plan = new Plan();
        plan.setVmCap(0, 0.80);
        plan.setVmCap(1, 0.60);
        plan.setVmCap(2, 0.50);
        plan.setVmCap(3, 0.30);
        SLAEvaluator.Metrics m = SLAEvaluator.evaluate(ep, plan);
        return new Result(m, plan);
    }
}
