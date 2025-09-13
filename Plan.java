package raj.cbm.core;

/**
 * Minimal plan with safe defaults:
 * - Class A,B,C,D map to VM 0,1,2,3
 * - Per-VM CPU caps default to 1.0 (100%)
 * You can change via setters or your optimizers.
 */
public class Plan {
    private final double[] vmCap = new double[]{1.0, 1.0, 1.0, 1.0}; // cap in [0..1]
    private final int[] classVm = new int[]{0, 1, 2, 3};             // A→0, B→1, C→2, D→3

    public double getVmCap(int i) {
        if (i < 0 || i >= vmCap.length) return 1.0;
        double v = vmCap[i];
        if (Double.isNaN(v) || v <= 0.0) return 1.0; // safe fallback
        if (v > 1.0) return 1.0;
        return v;
    }
    public void setVmCap(int i, double cap) {
        if (i < 0 || i >= vmCap.length) return;
        if (Double.isNaN(cap)) cap = 1.0;
        if (cap < 0.0) cap = 0.0;
        if (cap > 1.0) cap = 1.0;
        vmCap[i] = cap;
    }

    public int getClassVm(int cls) {
        if (cls < 0 || cls >= classVm.length) return Math.max(0, Math.min(3, cls));
        int vm = classVm[cls];
        if (vm < 0) return 0;
        if (vm > 3) return 3;
        return vm;
    }
    public void setClassVm(int cls, int vm) {
        if (cls < 0 || cls >= classVm.length) return;
        if (vm < 0) vm = 0;
        if (vm > 3) vm = 3;
        classVm[cls] = vm;
    }
}
