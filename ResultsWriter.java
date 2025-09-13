package raj.cbm.util;

import raj.cbm.eval.SLAEvaluator;

import java.io.File;
import java.util.Locale;

/** Writes CSV rows with a single consistent header. */
public final class ResultsWriter {
    private ResultsWriter(){}

    public static final String HEADER =
        "algo,seed,energy_kWh,p95A,p95B,p95C,p95D,missA%,missB%,missC%,missD%,throughput,reliability," +
        "makespan_s,avgResp_ms,vm0util,vm1util,vm2util,vm3util,host0util,host1util";

    public static void ensureHeader(File csv) {
        try {
            if (!csv.exists() || csv.length() == 0) {
                CsvUtils.appendLine(csv, HEADER);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void write(String algo, int seed, SLAEvaluator.Metrics m, File csv) {
        try {
            String row = String.format(Locale.US,
                    "%s,%d,%.6f,%.2f,%.2f,%.2f,%.2f,%.4f,%.4f,%.4f,%.4f,%.4f,%.3f,%.3f,%.2f," +
                            "%.3f,%.3f,%.3f,%.3f,%.3f,%.3f",
                    algo, seed,
                    m.energyKWh,
                    m.p95ms[0], m.p95ms[1], m.p95ms[2], m.p95ms[3],
                    m.missPct[0], m.missPct[1], m.missPct[2], m.missPct[3],
                    m.throughput, m.reliability,
                    m.makespanSec, m.avgResponseMs,
                    m.vmUtil[0], m.vmUtil[1], m.vmUtil[2], m.vmUtil[3],
                    m.hostUtil.length > 0 ? m.hostUtil[0] : 0.0,
                    m.hostUtil.length > 1 ? m.hostUtil[1] : 0.0
            );
            CsvUtils.appendLine(csv, row);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
