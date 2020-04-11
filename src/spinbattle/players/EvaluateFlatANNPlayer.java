package spinbattle.players;

import agents.dummy.DoNothingAgent;
import agents.dummy.RandomAgent;
import agents.evo.EvoAgent;
import evodef.DefaultMutator;
import evodef.EvoAlg;
import ga.SimpleRMHC;
import ggi.agents.PolicyEvoAgent;
import ggi.agents.SimpleEvoAgent;
import ggi.core.AbstractGameState;
import ggi.core.SimplePlayerInterface;
import spinbattle.actuator.SourceTargetActuator;
import spinbattle.actuator.SourceTargetJointActuator;
import spinbattle.core.Planet;
import spinbattle.core.SpinGameState;
import spinbattle.core.Transporter;
import spinbattle.params.SpinBattleParams;
import utilities.StatSummary;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.Random;
import java.util.function.BiFunction;

public class EvaluateFlatANNPlayer {
    public static void main(String[] args) throws Exception {
        int planets = Integer.parseInt(args[0]);
        String netType = args[1];
        String opponentType = args[2];
        String checkpoint = args[3];
        String outfile = args[4];
        int nGames = Integer.parseInt(args[5]);

        // csv scores output
        File csvOutput = new File(outfile);
        BufferedWriter csvWriter = new BufferedWriter(new FileWriter(csvOutput));
        csvWriter.write("game, score\n");

        // reset game
        SpinGameState gameState = restartStaticGame(planets);
        StatSummary scoreSummary = new StatSummary();

        // Agent setup
        SimplePlayerInterface annPlayer;
        if (netType.equals("flat")) {
            annPlayer = new FlatANNPlayer(checkpoint, planets, new FlatConverter()); // Change to be argument passed in
        } else {
            annPlayer = new IterANNPlayer(checkpoint, planets, new IterConverter());
        }

        SimplePlayerInterface opponentAgent;
        System.out.println(opponentType);
        if (opponentType.equals("evo")) {
            opponentAgent = getPolicyEvoAgent(new RandomAgent());
            System.out.println("using evo agent");
        } else {
            opponentAgent = new RandomAgent();
        }

        Random random = new Random();
        int playerFirst = random.nextInt(2);

        int[] actions = new int[2];
        for (int i = 0; i<nGames; i++) {
            playerFirst = random.nextInt(2);
            gameState.playerFirst = playerFirst;
            while (!gameState.isTerminal()) {
                if (playerFirst == 0) {
                    actions[0] = annPlayer.getAction(gameState, 0);
                    actions[1] = opponentAgent.getAction(gameState, 1);
                } else {
                    actions[1] = annPlayer.getAction(gameState, 1);
                    actions[0] = opponentAgent.getAction(gameState, 0);
                }
                gameState = (SpinGameState) gameState.next(actions);

            }
            annPlayer.reset();
            opponentAgent.reset();
            scoreSummary.add(gameState.currentScore);
            System.out.println(gameState.currentScore);
            csvWriter.write(i + ", " + gameState.getScore() + "\n");

            gameState = restartStaticGame(planets);
        }
        csvWriter.flush();
        //System.out.println(scoreSummary);


    }

    public static SpinGameState restartStaticGame(int planets) {
        // long[] eval_seeds = {-6330548296303013003L,};
        // Game Setup
        //SpinBattleParams.random = new Random(42);
        SpinBattleParams params = new SpinBattleParams();
        params.width = (int) (params.width*1.5);
        params.height = (int) (params.height*1.5);
        params.maxTicks = 500;
        params.nPlanets = planets;
        params.nToAllocate = 6;
        params.transitSpeed = 30;
        params.useVectorField = false;
        params.useProximityMap = false;
//        params.minGrowth = 0.5;
        params.maxGrowth = 0.25;
        params.symmetricMaps = true;
        params.includeTransitShipsInScore = true;
        SpinGameState gameState = new SpinGameState().setParams(params).setPlanets();
        gameState.actuators[0] = new SourceTargetJointActuator().setPlayerId(0);
        gameState.actuators[1] = new SourceTargetJointActuator().setPlayerId(1);
        return gameState;
    }

    static SimplePlayerInterface getPolicyEvoAgent(SimplePlayerInterface policy) {
        PolicyEvoAgent evoAgent = new PolicyEvoAgent();
        evoAgent.setUseShiftBuffer(true);
        evoAgent.setNEvals(20);
        evoAgent.setSequenceLength(100);
        evoAgent.setPolicy(policy);
        return evoAgent;
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
}

;

