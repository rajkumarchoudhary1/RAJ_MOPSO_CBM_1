package raj.cbm.pso;

import raj.cbm.core.ExperimentParams;
import raj.cbm.core.Plan;
import raj.cbm.eval.SLAEvaluator;

import java.util.Random;

/** Minimal scaffold for standard PSO. */
public class StandardPSOOptimizer {
    public static class Result {
        public final SLAEvaluator.Metrics metrics; public final Plan plan;
        public Result(SLAEvaluator.Metrics m, Plan p) { metrics = m; plan = p; }
    }

    public Result run(ExperimentParams ep) {
        Random rnd = new Random(ep.seed + 1);
        Plan plan = new Plan();
        plan.setVmCap(0, 0.75);
        plan.setVmCap(1, 0.65);
        plan.setVmCap(2, 0.55);
        plan.setVmCap(3, 0.40);
        // Slight remap: put C on VM1, B on VM2
        plan.setClassVm(1, 2); // B -> VM2
        plan.setClassVm(2, 1); // C -> VM1
        SLAEvaluator.Metrics m = SLAEvaluator.evaluate(ep, plan);
        return new Result(m, plan);
    }
}
