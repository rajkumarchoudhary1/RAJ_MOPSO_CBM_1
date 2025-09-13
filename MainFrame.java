package raj.cbm.gui;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.data.category.DefaultCategoryDataset;
import raj.cbm.aco.ACOOptimizer;
import raj.cbm.core.ExperimentParams;
import raj.cbm.eval.SLAEvaluator;
import raj.cbm.ga.NSGAIIOptimizer;
import raj.cbm.mopso.MopsoOptimizer;
import raj.cbm.pso.StandardPSOOptimizer;
import raj.cbm.spea.SPEA2Optimizer;
import raj.cbm.util.ResultsWriter;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * Simple Swing GUI to:
 *  - set parameters,
 *  - pick algorithm(s),
 *  - run and visualize results (Energy, p95, Miss%, Throughput, Util, Makespan/AvgResp),
 *  - write results.csv (in ./out/results.csv unless you change it).
 *
 * This is intentionally compact and well-commented so you can extend it.
 */
public class MainFrame extends JFrame {

    private final JComboBox<String> algoCombo;
    private final JTextField seedField;
    private final JTextField hostCountField, hostPesField, hostMipsField;
    private final JTextField vmMipsField, vmPesField;
    private final JTextField aCountField, bCountField, cCountField, dCountField;
    private final JTextField schedIntervalField;
    private final JLabel csvPathLabel;

    private final DefaultCategoryDataset dsEnergy = new DefaultCategoryDataset();
    private final DefaultCategoryDataset dsP95    = new DefaultCategoryDataset();
    private final DefaultCategoryDataset dsMiss   = new DefaultCategoryDataset();
    private final DefaultCategoryDataset dsThpt   = new DefaultCategoryDataset();
    private final DefaultCategoryDataset dsUtil   = new DefaultCategoryDataset();
    private final DefaultCategoryDataset dsTime   = new DefaultCategoryDataset();

    private final File csvFile = new File("out/results.csv").getAbsoluteFile();

    public MainFrame() {
        super("CBM-RTPS + Optimizer (CloudSim 3.0.3 Demo)");

        // ---- Controls (top) ----
        JPanel controls = new JPanel(new GridBagLayout());
        controls.setBorder(new TitledBorder("Parameters"));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4,4,4,4);
        gc.fill = GridBagConstraints.HORIZONTAL;

        algoCombo = new JComboBox<>(new String[]{
                "All", "MOPSO", "NSGA-II", "StandardPSO", "SPEA-II", "ACO"
        });

        seedField = new JTextField("42", 6);
        hostCountField = new JTextField("2", 6);
        hostPesField   = new JTextField("8", 6);
        hostMipsField  = new JTextField("2000", 6);

        vmMipsField    = new JTextField("2000", 6);
        vmPesField     = new JTextField("1", 6);

        aCountField    = new JTextField("200", 6);
        bCountField    = new JTextField("200", 6);
        cCountField    = new JTextField("200", 6);
        dCountField    = new JTextField("40", 6);

        schedIntervalField = new JTextField("1.0", 6);

        int col = 0, row = 0;
        addRow(controls, gc, row++, "Algorithm", algoCombo);
        addRow(controls, gc, row++, "Seed", seedField);
        addRow(controls, gc, row++, "Hosts / PEs / MIPS", rowFields(hostCountField, hostPesField, hostMipsField));
        addRow(controls, gc, row++, "VM MIPS / PEs", rowFields(vmMipsField, vmPesField));
        addRow(controls, gc, row++, "Class counts A/B/C/D", rowFields(aCountField, bCountField, cCountField, dCountField));
        addRow(controls, gc, row++, "Scheduling interval (s)", schedIntervalField);

        JButton runBtn = new JButton("Run");
        runBtn.addActionListener(e -> onRun());
        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 2;
        controls.add(runBtn, gc);

        csvPathLabel = new JLabel("CSV: " + csvFile.getPath());
        gc.gridy = row; gc.gridx = 2; gc.gridwidth = 2;
        controls.add(csvPathLabel, gc);

        // ---- Charts (center) ----
        JTabbedPane tabs = new JTabbedPane();
        tabs.add("Energy (kWh)", chartPanel("Energy (kWh)", "Algorithm", "kWh", dsEnergy));
        tabs.add("p95 (ms)",     chartPanel("p95 Latency (ms)", "Algorithm", "ms", dsP95));
        tabs.add("SLO-miss (%)", chartPanel("SLO-miss (%)", "Algorithm", "%", dsMiss));
        tabs.add("Throughput",   chartPanel("Throughput (cloudlets/s)", "Algorithm", "cl/s", dsThpt));
        tabs.add("Utilization",  chartPanel("CPU Utilization (avg)", "Algorithm", "util", dsUtil));
        tabs.add("Time",         chartPanel("Makespan / Avg Response", "Algorithm", "sec|ms", dsTime));

        // ---- Layout ----
        setLayout(new BorderLayout());
        add(controls, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(1100, 700);
        setLocationRelativeTo(null);
    }

    private void addRow(JPanel p, GridBagConstraints gc, int row, String label, Component comp) {
        gc.gridwidth = 1; gc.gridx = 0; gc.gridy = row; p.add(new JLabel(label), gc);
        gc.gridx = 1; gc.gridwidth = 3; p.add(comp, gc);
    }

    private JPanel rowFields(JTextField... fields) {
        JPanel panel = new JPanel(new GridLayout(1, fields.length, 6, 0));
        for (JTextField f : fields) panel.add(f);
        return panel;
    }

    private ChartPanel chartPanel(String title, String x, String y, DefaultCategoryDataset ds) {
        JFreeChart chart = ChartFactory.createBarChart(title, x, y, ds);
        return new ChartPanel(chart);
    }

    /** Read all fields, run one or all algorithms, update charts + CSV. */
    private void onRun() {
        // read params
        ExperimentParams ep = ExperimentParams.defaults();
        try {
            ep.seed = Integer.parseInt(seedField.getText().trim());
            ep.hostCount = Integer.parseInt(hostCountField.getText().trim());
            ep.hostPes = Integer.parseInt(hostPesField.getText().trim());
            ep.hostMips = Integer.parseInt(hostMipsField.getText().trim());
            ep.vmMips = Integer.parseInt(vmMipsField.getText().trim());
            ep.vmPes  = Integer.parseInt(vmPesField.getText().trim());
            ep.classACount = Integer.parseInt(aCountField.getText().trim());
            ep.classBCount = Integer.parseInt(bCountField.getText().trim());
            ep.classCCount = Integer.parseInt(cCountField.getText().trim());
            ep.classDCount = Integer.parseInt(dCountField.getText().trim());
            ep.schedulingInterval = Double.parseDouble(schedIntervalField.getText().trim());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Invalid input: " + ex, "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // ensure CSV header
        ResultsWriter.ensureHeader(csvFile);

        // clear charts
        dsEnergy.clear(); dsP95.clear(); dsMiss.clear(); dsThpt.clear(); dsUtil.clear(); dsTime.clear();

        String choice = (String) algoCombo.getSelectedItem();
        List<String> algos = new ArrayList<String>();
        if ("All".equals(choice)) {
            algos.addAll(Arrays.asList("MOPSO", "NSGA-II", "StandardPSO", "SPEA-II", "ACO"));
        } else {
            algos.add(choice);
        }

        for (String algo : algos) {
            SLAEvaluator.Metrics m = runAlgo(algo, ep);
            if (m == null) continue;

            // charts
            dsEnergy.addValue(m.energyKWh, "kWh", algo);
            dsP95.addValue(m.p95ms[0], "p95A", algo);
            dsP95.addValue(m.p95ms[1], "p95B", algo);
            dsP95.addValue(m.p95ms[2], "p95C", algo);
            dsP95.addValue(m.p95ms[3], "p95D", algo);

            dsMiss.addValue(m.missPct[0], "missA%", algo);
            dsMiss.addValue(m.missPct[1], "missB%", algo);
            dsMiss.addValue(m.missPct[2], "missC%", algo);
            dsMiss.addValue(m.missPct[3], "missD%", algo);

            dsThpt.addValue(m.throughput, "throughput", algo);

            dsUtil.addValue(m.vmUtil[0], "vm0", algo);
            dsUtil.addValue(m.vmUtil[1], "vm1", algo);
            dsUtil.addValue(m.vmUtil[2], "vm2", algo);
            dsUtil.addValue(m.vmUtil[3], "vm3", algo);
            if (m.hostUtil.length > 0) dsUtil.addValue(m.hostUtil[0], "host0", algo);
            if (m.hostUtil.length > 1) dsUtil.addValue(m.hostUtil[1], "host1", algo);

            dsTime.addValue(m.makespanSec, "makespan_s", algo);
            dsTime.addValue(m.avgResponseMs, "avgResp_ms", algo);

            // CSV
            ResultsWriter.write(algo, ep.seed, m, csvFile);
        }
    }

    private SLAEvaluator.Metrics runAlgo(String algo, ExperimentParams ep) {
        try {
            if ("MOPSO".equals(algo)) {
                MopsoOptimizer.Result r = new MopsoOptimizer().run(ep);
                return r.metrics;
            } else if ("NSGA-II".equals(algo)) {
                NSGAIIOptimizer.Result r = new NSGAIIOptimizer().run(ep);
                return r.metrics;
            } else if ("StandardPSO".equals(algo)) {
                StandardPSOOptimizer.Result r = new StandardPSOOptimizer().run(ep);
                return r.metrics;
            } else if ("SPEA-II".equals(algo)) {
                SPEA2Optimizer.Result r = new SPEA2Optimizer().run(ep);
                return r.metrics;
            } else if ("ACO".equals(algo)) {
                ACOOptimizer.Result r = new ACOOptimizer().run(ep);
                return r.metrics;
            }
        } catch (Throwable t) {
            JOptionPane.showMessageDialog(this, algo + " failed: " + t, "Run error", JOptionPane.ERROR_MESSAGE);
        }
        return null;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }
}
