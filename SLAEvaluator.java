package raj.cbm.eval;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerDatacenter;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.power.models.PowerModelLinear;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import raj.cbm.core.ExperimentParams;
import raj.cbm.core.Plan;

import java.util.*;

/**
 * Evaluates a Plan by running a CloudSim 3.0.3 scenario using the Power module.
 * - Uses PowerDatacenter + PowerHost + PowerModelLinear for energy.
 * - Applies per-VM CPU caps via a capped time-shared scheduler.
 * - Class→VM mapping (A,B,C,D -> VM 0..3) comes from Plan.
 */
public final class SLAEvaluator {

    /** Collected metrics for a run. */
    public static class Metrics {
        public double energyKWh;
        public final double[] p95ms = new double[4];
        public final double[] missPct = new double[4];
        public double throughput;
        public double reliability;
        public double makespanSec;
        public double avgResponseMs;
        public final double[] vmUtil = new double[4]; // avg CPU util [0..1] per VM
        public double[] hostUtil = new double[0];     // avg CPU util per Host
    }

    private SLAEvaluator(){}

    public static Metrics evaluate(final ExperimentParams ep, final Plan plan) {
        // 1) Init CloudSim
        CloudSim.init(1, Calendar.getInstance(), false);

        PowerDatacenter dc = createPowerDatacenter("edge", ep);
        DatacenterBroker broker = createBroker();
        int brokerId = broker.getId();

        // 2) VMs: four VMs, each with a capped scheduler (fraction of MIPS)
        List<Vm> vmList = new ArrayList<Vm>(4);
        
        
        // inside evaluate(...):
for (int i = 0; i < 4; i++) {
    double cap = 1.0; // default full speed
    if (plan != null) {
        try {
            double c = plan.getVmCap(i);
            if (!(c > 0.0)) c = 1.0;  // fallback if 0/neg/NaN
            if (c > 1.0) c = 1.0;
            cap = c;
        } catch (Throwable ignore) { /* keep 1.0 */ }
    }
    CloudletScheduler scheduler = new CloudletSchedulerCapped(cap);
    Vm vm = new Vm(
        i, brokerId,
        ep.vmMips > 0 ? ep.vmMips : 2000,
        ep.vmPes  > 0 ? ep.vmPes  : 1,
        ep.vmRamMb> 0 ? ep.vmRamMb: 2048,
        ep.vmBw    > 0 ? ep.vmBw   : 1000,
        ep.vmSizeMb> 0 ? ep.vmSizeMb:10000,
        "Xen",
        scheduler
    );
    vmList.add(vm);
}




        broker.submitVmList(vmList);

        // 3) Cloudlets for A/B/C/D, then pin to VM using Plan mapping
        List<Cloudlet> cloudlets = new ArrayList<Cloudlet>();
        int aCount = ep.classACount > 0 ? ep.classACount : 200;
        int bCount = ep.classBCount > 0 ? ep.classBCount : 200;
        int cCount = ep.classCCount > 0 ? ep.classCCount : 200;
        int dCount = ep.classDCount > 0 ? ep.classDCount : 40;

        long aLen = ep.classALen > 0 ? ep.classALen : 50_000;
        long bLen = ep.classBLen > 0 ? ep.classBLen : 50_000;
        long cLen = ep.classCLen > 0 ? ep.classCLen : 50_000;
        long dLen = ep.classDLen > 0 ? ep.classDLen : 50_000;

        cloudlets.addAll(makeClass(brokerId, 0,    aLen, aCount)); // A
        cloudlets.addAll(makeClass(brokerId, 1000, bLen, bCount)); // B
        cloudlets.addAll(makeClass(brokerId, 2000, cLen, cCount)); // C
        cloudlets.addAll(makeClass(brokerId, 3000, dLen, dCount)); // D

        for (Cloudlet c : cloudlets) {
            int cls = classIndexById(c.getCloudletId());
            int vmId = cls;
            if (plan != null) try { vmId = within03(plan.getClassVm(cls)); } catch (Throwable ignore) {}
            c.setVmId(vmId);
        }
        broker.submitCloudletList(cloudlets);

        // 4) Run simulation
        CloudSim.startSimulation();
        @SuppressWarnings("unchecked")
        List<Cloudlet> finished = broker.getCloudletReceivedList();
        CloudSim.stopSimulation();

        // 5) Gather metrics
        Metrics m = new Metrics();
        double thrMs = ep.p95MissThresholdMs > 0 ? ep.p95MissThresholdMs : 50.0;

        List<Double> latA = new ArrayList<Double>(),
                     latB = new ArrayList<Double>(),
                     latC = new ArrayList<Double>(),
                     latD = new ArrayList<Double>();

        double makespan = 0.0;
        double respSumMs = 0.0;

        // VM CPU seconds bookkeeping to compute average utilizations
        double[] vmCpuSeconds = new double[4];

        for (Cloudlet c : finished) {
            double start  = c.getExecStartTime();
            double finish = c.getFinishTime();
            if (finish > makespan) makespan = finish;

            double latencySec = (start >= 0.0) ? Math.max(0.0, finish - start) : Math.max(0.0, finish);
            double latencyMs  = latencySec * 1000.0;
            respSumMs += latencyMs;

            int cls = classIndexById(c.getCloudletId());
            if      (cls == 0) latA.add(latencyMs);
            else if (cls == 1) latB.add(latencyMs);
            else if (cls == 2) latC.add(latencyMs);
            else               latD.add(latencyMs);

            int vmId = c.getVmId();
            if (vmId >= 0 && vmId < 4) vmCpuSeconds[vmId] += c.getActualCPUTime();
        }

        m.p95ms[0] = percentile(latA, 95);
        m.p95ms[1] = percentile(latB, 95);
        m.p95ms[2] = percentile(latC, 95);
        m.p95ms[3] = percentile(latD, 95);

        m.missPct[0] = missPct(latA, thrMs);
        m.missPct[1] = missPct(latB, thrMs);
        m.missPct[2] = missPct(latC, thrMs);
        m.missPct[3] = missPct(latD, thrMs);

        m.makespanSec = makespan;
        m.avgResponseMs = finished.isEmpty() ? 0.0 : (respSumMs / finished.size());
        m.throughput = (makespan > 0.0) ? (finished.size() / makespan) : 0.0;
        m.reliability = cloudlets.isEmpty() ? 1.0 : ((double) finished.size() / (double) cloudlets.size());

        // VM utilization (avg over makespan)
        for (int i = 0; i < 4; i++) {
            double denom = (ep.vmPes > 0 ? ep.vmPes : 1) * makespan;
            m.vmUtil[i] = denom > 0 ? vmCpuSeconds[i] / denom : 0.0;
            if (m.vmUtil[i] < 0) m.vmUtil[i] = 0.0;
            if (m.vmUtil[i] > 1) m.vmUtil[i] = 1.0;
        }

        // Host utilization using Host list (no generics cast issues)
        List<Host> hosts = dc.getHostList();
        m.hostUtil = new double[hosts.size()];
        for (int h = 0; h < hosts.size(); h++) {
            Host host = hosts.get(h);
            double vmSecondsOnHost = 0.0;
            for (Vm vm : vmList) {
                if (vm.getHost() == host) {
                    int id = vm.getId();
                    if (id >= 0 && id < 4) vmSecondsOnHost += vmCpuSeconds[id];
                }
            }
            double denom = (ep.hostPes > 0 ? ep.hostPes : 8) * makespan;
            m.hostUtil[h] = denom > 0 ? vmSecondsOnHost / denom : 0.0;
            if (m.hostUtil[h] < 0) m.hostUtil[h] = 0.0;
            if (m.hostUtil[h] > 1) m.hostUtil[h] = 1.0;
        }

        // Energy from PowerDatacenter: getPower() returns Watt*seconds
        double wattSeconds = dc.getPower();
        m.energyKWh = wattSeconds / 3_600_000.0; // Ws -> kWh

        return m;
    }

    /** Simple capped scheduler: scales MIPS shares by cap in [0..1]. */
    private static final class CloudletSchedulerCapped extends CloudletSchedulerTimeShared {
        private final double cap;
        CloudletSchedulerCapped(double cap) { this.cap = clamp01(cap); }
        @Override
        public double updateVmProcessing(double currentTime, List<Double> mipsShare) {
            if (mipsShare != null && !mipsShare.isEmpty()) {
                List<Double> scaled = new ArrayList<Double>(mipsShare.size());
                for (Double m : mipsShare) scaled.add((m == null ? 0.0 : m) * cap);
                return super.updateVmProcessing(currentTime, scaled);
            }
            return super.updateVmProcessing(currentTime, mipsShare);
        }
    }

    private static PowerDatacenter createPowerDatacenter(String name, ExperimentParams ep) {
        List<PowerHost> hostList = new ArrayList<PowerHost>();

        int hostCount = ep.hostCount > 0 ? ep.hostCount : 2;
        int hostPes   = ep.hostPes   > 0 ? ep.hostPes   : 8;
        int hostMips  = ep.hostMips  > 0 ? ep.hostMips  : 2000;

        for (int h = 0; h < hostCount; h++) {
            List<Pe> peList = new ArrayList<Pe>(hostPes);
            for (int i = 0; i < hostPes; i++) {
                peList.add(new Pe(i, new PeProvisionerSimple(hostMips)));
            }
            PowerHost host = new PowerHost(
                    h,
                    new RamProvisionerSimple(ep.hostRamMb > 0 ? ep.hostRamMb : 16384),
                    new BwProvisionerSimple(ep.hostBw    > 0 ? ep.hostBw    : 10_000),
                    ep.hostStorageMb > 0 ? ep.hostStorageMb : 1_000_000,
                    peList,
                    new VmSchedulerTimeShared(peList),
                    new PowerModelLinear(ep.hostIdleW > 0 ? ep.hostIdleW : 100.0,
                                         ep.hostMaxW  > 0 ? ep.hostMaxW  : 250.0)
            );
            hostList.add(host);
        }

        // In CloudSim 3.0.3 use DatacenterCharacteristics (not PowerDatacenterCharacteristics)
        DatacenterCharacteristics ch = new DatacenterCharacteristics(
                "x86", "Linux", "Xen", new ArrayList<Host>(hostList), 0.0,
                0.01, 0.005, 0.001, 0.001);
        
        

       double interval = ep.schedulingInterval > 0 ? ep.schedulingInterval : 0.1; // was 1.0; make it 0.1
    try {
        return new PowerDatacenter(
            name,
            ch,
            new VmAllocationPolicySimple(new ArrayList<Host>(hostList)),
            new LinkedList<Storage>(),
            interval
        );
    } catch (Exception e) {
        throw new RuntimeException(e);
    }
}
    

    private static DatacenterBroker createBroker() {
        try { return new DatacenterBroker("broker"); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    private static List<Cloudlet> makeClass(int userId, int idBase, long length, int count) {
        List<Cloudlet> list = new ArrayList<Cloudlet>(count);
        UtilizationModel um = new UtilizationModelFull();
        long fileSize = 300, outputSize = 300;
        for (int i = 0; i < count; i++) {
            Cloudlet c = new Cloudlet(idBase + (i + 1), length, 1, fileSize, outputSize, um, um, um);
            c.setUserId(userId);
            list.add(c);
        }
        return list;
    }

    private static int classIndexById(int cloudletId) {
        if (cloudletId >= 3000) return 3; // D
        if (cloudletId >= 2000) return 2; // C
        if (cloudletId >= 1000) return 1; // B
        return 0;                         // A
    }

    private static double percentile(List<Double> data, double p) {
        if (data == null || data.isEmpty()) return 0.0;
        List<Double> v = new ArrayList<Double>(data);
        Collections.sort(v);
        if (v.size() == 1) return v.get(0);
        double rank = (p / 100.0) * (v.size() - 1);
        int lo = (int) Math.floor(rank), hi = (int) Math.ceil(rank);
        if (lo == hi) return v.get(lo);
        double w = rank - lo;
        return v.get(lo) * (1.0 - w) + v.get(hi) * w;
    }

    private static double missPct(List<Double> data, double thrMs) {
        if (data == null || data.isEmpty()) return 0.0;
        int miss = 0; for (double x : data) if (x > thrMs) miss++;
        return 100.0 * miss / data.size();
    }

    private static double clamp01(double x) { return x < 0 ? 0 : (x > 1 ? 1 : x); }
    private static int within03(int x) { if (x < 0) return 0; if (x > 3) return 3; return x; }
}
