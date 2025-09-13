package raj.cbm.core;

/**
 * Experiment parameters controlled by the GUI.
 * Keep it small and stable; add more knobs gradually.
 */
public class ExperimentParams {

    // ---- Hosts (Power) ----
    public int   hostCount      = 2;     // number of hosts
    public int   hostPes        = 8;     // PEs per host
    public int   hostMips       = 2000;  // MIPS per PE
    public int   hostRamMb      = 16384; // RAM per host
    public long  hostBw         = 10_000;// BW per host
    public long  hostStorageMb  = 1_000_000; // storage per host
    public double hostIdleW     = 100.0; // idle Watts
    public double hostMaxW      = 250.0; // max Watts

    // ---- VMs ----
    public int   vmPes          = 1;
    public int   vmMips         = 2000;
    public int   vmRamMb        = 2048;
    public long  vmBw           = 1000;
    public long  vmSizeMb       = 10000;

    // ---- Workload (4 classes: A,B,C,D) ----
    public int   classACount    = 200;
    public int   classBCount    = 200;
    public int   classCCount    = 200;
    public int   classDCount    = 40;

    public long  classALen      = 50_000; // cloudlet length
    public long  classBLen      = 50_000;
    public long  classCLen      = 50_000;
    public long  classDLen      = 50_000;

    // ---- Evaluation ----
    public double p95MissThresholdMs = 50.0; // SLO threshold used for "miss%" metric
    public double schedulingInterval = 1.0;  // seconds (PowerDatacenter scheduling step)

    // seed is used by the optimizers to vary caps/mappings
    public int seed = 42;

    public static ExperimentParams defaults() { return new ExperimentParams(); }
}
