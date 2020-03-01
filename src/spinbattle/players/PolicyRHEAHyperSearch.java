package spinbattle.players;

import agents.dummy.RandomAgent;
import caveswing.design.EvoAgentSearchSpaceCaveSwing;
import evodef.AnnotatedFitnessSpace;
import ggi.agents.PolicyEvoSearchSpace;
import ggi.core.SimplePlayerInterface;
import hyperopt.HyperParamTuneRunner;
import ntbea.NTupleBanditEA;
import ntbea.NTupleSystem;
import utilities.ElapsedTimer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class PolicyRHEAHyperSearch {
    public static void main(String[] args) throws IOException {
        String netType = args[0];
        String checkpoint = args[1];
        String outfile = args[2];

        File csvOutput = new File(outfile);
        BufferedWriter csvWriter = new BufferedWriter(new FileWriter(csvOutput));

        SimplePlayerInterface annPlayer;
        if (netType.equals("flat")) {
            annPlayer = new FlatANNPlayer(checkpoint, 6, new FlatConverter()); // Change to be argument passed in
        } else {
            annPlayer = new IterANNPlayer(checkpoint, 12, new IterConverter());
        }

        PolicyEvoSearchSpace searchSpace = new PolicyEvoSearchSpace(annPlayer);

        int nEvals = 2000;
        System.out.println("Optimization budget: " + nEvals);
        NTupleBanditEA ntbea = new NTupleBanditEA().setKExplore(5000);
        ntbea.logBestYet = true;
        ntbea.nSamples = 1;
        NTupleSystem model = new NTupleSystem();
        // set up a non-standard tuple pattern
        model.use1Tuple = true;
        model.use2Tuple = true;
        model.useNTuple = true;

        ntbea.setModel(model);

        int nChecks = 1;
        int nTrials = 1;

        ElapsedTimer timer = new ElapsedTimer();

        HyperParamTuneRunner runner = new HyperParamTuneRunner();
        runner.verbose = false;
//            runner.setLineChart(lineChart);
        runner.nChecks = nChecks;
        runner.nTrials = nTrials;
        runner.nEvals = nEvals;

        // this allows plotting of the independently assessed fitness of
        // the algorithm's best guesses during each run
        // set to zero for fastest performance, set to 5 or 10 to learn
        // more about the convergence of the algorithm
        runner.plotChecks = 0;
        // uncomment to run the skilful one
        // caveSwingSpace = new CaveSwingGameSkillSpace();
        System.out.println("Testing: " + ntbea);
        runner.runTrials(ntbea, searchSpace);
        System.out.println("Finished testing: " + ntbea);
        System.out.println("Time for all experiments: " + timer);

        System.out.println(searchSpace.csvReport(runner.solution));
        csvWriter.write(searchSpace.csvReport(runner.solution));
        csvWriter.flush();
        csvWriter.close();

        // new Plotter().setModel(model).setAnnotatedFitnessSpace(caveSwingSpace).plot1Tuples();
    }
}
