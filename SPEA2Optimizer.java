package raj.cbm.spea;

import raj.cbm.core.ExperimentParams;
import raj.cbm.core.Plan;
import raj.cbm.eval.SLAEvaluator;

/** Minimal scaffold for SPEA-II. */
public class SPEA2Optimizer {
    public static class Result {
        public final SLAEvaluator.Metrics metrics; public final Plan plan;
        public Result(SLAEvaluator.Metrics m, Plan p) { metrics = m; plan = p; }
    }

    public Result run(ExperimentParams ep) {
        Plan plan = new Plan();
        plan.setVmCap(0, 0.70);
        plan.setVmCap(1, 0.70);
        plan.setVmCap(2, 0.40);
        plan.setVmCap(3, 0.40);
        // Swap D and C placement
        plan.setClassVm(2, 3); // C -> VM3
        plan.setClassVm(3, 2); // D -> VM2
        SLAEvaluator.Metrics m = SLAEvaluator.evaluate(ep, plan);
        return new Result(m, plan);
    }
}
