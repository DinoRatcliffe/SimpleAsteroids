package spinbattle.players;

import agents.dummy.DoNothingAgent;
import agents.dummy.RandomAgent;
import agents.evo.EvoAgent;
import evodef.DefaultMutator;
import evodef.EvoAlg;
import ga.SimpleRMHC;
import ggi.agents.PolicyEvoAgent;
import ggi.agents.SimpleEvoAgent;
import ggi.core.SimplePlayerInterface;
import spinbattle.actuator.SourceTargetActuator;
import spinbattle.core.SpinGameState;
import spinbattle.params.SpinBattleParams;
import utilities.StatSummary;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.Random;

public class EvaluatePolicyRHEAANN {
    public static void main(String[] args) throws Exception {
        String netType = args[0];
        String opponentType = args[1];
        String checkpoint = args[2];
        String outfile = args[3];
        int nGames = Integer.parseInt(args[4]);

        // csv scores output
        File csvOutput = new File(outfile);
        BufferedWriter csvWriter = new BufferedWriter(new FileWriter(csvOutput));
        csvWriter.write("game, score\n");

        // reset game
        SpinGameState gameState = restartStaticGame();
        StatSummary scoreSummary = new StatSummary();

        // Agent setup
        SimplePlayerInterface randomPlayer = new agents.dummy.RandomAgent();
        SimplePlayerInterface doNothingPlayer = new DoNothingAgent();

        SimplePlayerInterface annPlayer;
        SimplePlayerInterface player;

        if (netType.equals("flat")) {
            annPlayer = new FlatANNPlayer(checkpoint, 5, new Converter(45)); // Change to be argument passed in
        } else {
            annPlayer = new IterANNPlayer(checkpoint, 5, new IterConverter());
        }

        player = getPolicyEvoAgent(annPlayer);

        SimplePlayerInterface opponentAgent;
        System.out.println(opponentType);
        if (opponentType.equals("evo")) {
            opponentAgent = getEvoAgent();
            System.out.println("using evo agent");
        } else {
            opponentAgent = new RandomAgent();
        }

        player = getEvoAgent();
        opponentAgent = getEvoAgent();

        int[] actions = new int[2];
        for (int i = 0; i<nGames; i++) {
            while (!gameState.isTerminal()) {
                actions[0] = player.getAction(gameState, 0);
                actions[1] = opponentAgent.getAction(gameState, 1);
                gameState = (SpinGameState) gameState.next(actions);
            }
            scoreSummary.add(gameState.getScore());
            System.out.println(gameState.getScore());
            csvWriter.write(i + ", " + gameState.getScore() + "\n");
            gameState = restartStaticGame();
        }
        csvWriter.flush();
        System.out.println(scoreSummary);
    }

    static boolean useSimpleEvoAgent = false;
    static SimplePlayerInterface getEvoAgent() {

        if (useSimpleEvoAgent) {
            return new SimpleEvoAgent().setOpponent(new DoNothingAgent());
        }
        //
        int nResamples = 1;

        DefaultMutator mutator = new DefaultMutator(null);
        // setting to true may give best performance
        // mutator.totalRandomChaosMutation = true;
        mutator.pointProb = 10;

        SimpleRMHC simpleRMHC = new SimpleRMHC();
        simpleRMHC.setSamplingRate(nResamples);
        simpleRMHC.setMutator(mutator);

        EvoAlg evoAlg = simpleRMHC;

        // evoAlg = new SlidingMeanEDA();
        // evoAlg = new SimpleGA();

        int nEvals = 20;
        int seqLength = 100;
        EvoAgent evoAgent = new EvoAgent().setEvoAlg(evoAlg, nEvals).setSequenceLength(seqLength);
        boolean useShiftBuffer = true;
        evoAgent.setUseShiftBuffer(useShiftBuffer);
        //evoAgent.setVisual();

        return evoAgent;
    }

    static SimplePlayerInterface getPolicyEvoAgent(SimplePlayerInterface policy) {
        PolicyEvoAgent evoAgent = new PolicyEvoAgent();
        evoAgent.setPolicy(policy);
        return evoAgent;
    }

    public static SpinGameState restartStaticGame() {
        // Game Setup
        long seed = -6330548296303013003L;
        System.out.println("Setting seed to: " + seed);
        SpinBattleParams.random = new Random(seed);
        SpinBattleParams params = new SpinBattleParams();
        params.maxTicks = 500;
        params.symmetricMaps = true;
        params.nPlanets = 5;
        params.transitSpeed = 30;
        params.useVectorField = false;
        params.useProximityMap = false;
        params.includeTransitShipsInScore = true;
        SpinGameState gameState = new SpinGameState().setParams(params).setPlanets();
        gameState.actuators[0] = new SourceTargetActuator().setPlayerId(0);
        gameState.actuators[1] = new SourceTargetActuator().setPlayerId(1);
        return gameState;
    }
}
