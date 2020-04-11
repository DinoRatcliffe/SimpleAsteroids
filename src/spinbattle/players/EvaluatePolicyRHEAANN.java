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
import spinbattle.actuator.SourceTargetJointActuator;
import spinbattle.core.SpinGameState;
import spinbattle.params.SpinBattleParams;
import utilities.StatSummary;

import java.io.*;
import java.util.Random;

public class EvaluatePolicyRHEAANN {
    public static void main(String[] args) throws Exception {
        int planets = Integer.parseInt(args[0]);
        String netType = args[1];
        String opponentType = args[2];
        String checkpoint = args[3];
        String outfile = args[4];
        int nGames = Integer.parseInt(args[5]);
        String paramsFile = args[6];

        double probMutation = 0.2;
        double initUsingPolicy = 0.8;
        double appendUsingPolicy = 0.8;
        double mutateUsingPolicy = 0.5;

        if (!paramsFile.equals("default")) {
            File csvParamsFile = new File(paramsFile);
            BufferedReader csvReader = new BufferedReader(new FileReader(csvParamsFile));
            String line = csvReader.readLine();
            line = csvReader.readLine();
            String[] values = line.split(",");
            probMutation = Double.parseDouble(values[0]);
            initUsingPolicy = Double.parseDouble(values[1]);
            appendUsingPolicy = Double.parseDouble(values[2]);
            mutateUsingPolicy = Double.parseDouble(values[3]);
        }

        // csv scores output
        File csvOutput = new File(outfile);
        BufferedWriter csvWriter = new BufferedWriter(new FileWriter(csvOutput));
        csvWriter.write("game, score\n");

        // reset game
        SpinGameState gameState = restartStaticGame(planets);
        StatSummary scoreSummary = new StatSummary();

        // Agent setup
        SimplePlayerInterface randomPlayer = new agents.dummy.RandomAgent();
        SimplePlayerInterface doNothingPlayer = new DoNothingAgent();

        SimplePlayerInterface annPlayer;
        PolicyEvoAgent player;

        if (netType.equals("flat")) {
            annPlayer = new FlatANNPlayer(checkpoint, planets, new FlatConverter()); // Change to be argument passed in
        } else {
            annPlayer = new IterANNPlayer(checkpoint, planets, new IterConverter());
        }

        player = (PolicyEvoAgent) getPolicyEvoAgent(annPlayer);
        player.setUseMutationTransducer(false);
        player.setProbMutation(probMutation);
        player.setInitUsingPolicy(initUsingPolicy);
        player.setAppendUsingPolicy(appendUsingPolicy);
        player.setMutateUsingPolicy(mutateUsingPolicy);

        SimplePlayerInterface opponentAgent;

        if (opponentType.equals("evo")) {
            opponentAgent = getPolicyEvoAgent(new RandomAgent());
        } else {
            opponentAgent = new RandomAgent();
        }

        Random random = new Random();
        int playerFirst;

        int[] actions = new int[2];
        for (int i = 0; i<nGames; i++) {
            playerFirst = random.nextInt(2);
            gameState.playerFirst = playerFirst;
            while (!gameState.isTerminal()) {
                if (playerFirst == 0) {
                    actions[0] = player.getAction(gameState, 0);
                    actions[1] = opponentAgent.getAction(gameState, 1);
                } else {
                    actions[1] = player.getAction(gameState, 1);
                    actions[0] = opponentAgent.getAction(gameState, 0);
                }
                gameState = (SpinGameState) gameState.next(actions);
            }
            player.reset();
            opponentAgent.reset();

            scoreSummary.add(gameState.getScore());
            System.out.println(gameState.getScore());
            csvWriter.write(i + ", " + gameState.getScore() + "\n");

            gameState = restartStaticGame(planets);
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
        evoAgent.setUseShiftBuffer(true);
        evoAgent.setNEvals(20);
        evoAgent.setSequenceLength(100);
        evoAgent.setPolicy(policy);
        return evoAgent;
    }

    public static SpinGameState restartStaticGame(int planets) {
        // Game Setup
        //long seed = 42;
        //System.out.println("Setting seed to: " + seed);
        //SpinBattleParams.random = new Random(seed);
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
}
