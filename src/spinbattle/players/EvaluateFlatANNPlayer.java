package spinbattle.players;

import agents.dummy.DoNothingAgent;
import agents.dummy.RandomAgent;
import agents.evo.EvoAgent;
import evodef.DefaultMutator;
import evodef.EvoAlg;
import ga.SimpleRMHC;
import ggi.agents.SimpleEvoAgent;
import ggi.core.AbstractGameState;
import ggi.core.SimplePlayerInterface;
import spinbattle.actuator.SourceTargetActuator;
import spinbattle.core.Planet;
import spinbattle.core.SpinGameState;
import spinbattle.core.Transporter;
import spinbattle.params.SpinBattleParams;
import utilities.StatSummary;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Random;
import java.util.function.BiFunction;

public class EvaluateFlatANNPlayer {
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
        SimplePlayerInterface annPlayer;
        if (netType.equals("flat")) {
            annPlayer = new FlatANNPlayer(checkpoint, 6, new Converter(45)); // Change to be argument passed in
        } else {
            annPlayer = new IterANNPlayer(checkpoint, 6, new IterConverter());
        }

        SimplePlayerInterface opponentAgent;
        System.out.println(opponentType);
        if (opponentType.equals("evo")) {
            opponentAgent = getEvoAgent();
            System.out.println("using evo agent");
        } else {
            opponentAgent = new RandomAgent();
        }

        int[] actions = new int[2];
        for (int i = 0; i<nGames; i++) {
            while (!gameState.isTerminal()) {
                actions[0] = annPlayer.getAction(gameState, 0);
                actions[1] = opponentAgent.getAction(gameState, 1);
                gameState = (SpinGameState) gameState.next(actions);

            }
            annPlayer.reset();
            opponentAgent.reset();
            scoreSummary.add(gameState.currentScore);
            System.out.println(gameState.currentScore);
            csvWriter.write(i + ", " + gameState.getScore() + "\n");
            gameState = restartStaticGame();
        }
        csvWriter.flush();
        System.out.println(scoreSummary);


    }

    public static SpinGameState restartStaticGame() {
        // Game Setup
        long[] eval_seeds = {-6330548296303013003L,};
        SpinBattleParams.random = new Random(eval_seeds[0]);

        SpinBattleParams params = new SpinBattleParams();
        params.maxTicks = 500;
        params.nPlanets = 6;
        params.transitSpeed = 30;
        params.useVectorField = false;
        params.useProximityMap = false;
        params.symmetricMaps = true;
        params.includeTransitShipsInScore = true;
        SpinGameState gameState = new SpinGameState().setParams(params).setPlanets();
        gameState.actuators[0] = new SourceTargetActuator().setPlayerId(0);
        gameState.actuators[1] = new SourceTargetActuator().setPlayerId(1);
        return gameState;
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

class Converter implements BiFunction<AbstractGameState, Integer, double[]> {
    int inputSize;
    public Converter(int inputSize) {
        this.inputSize = inputSize;
    }
    @Override
    public double[] apply(AbstractGameState abstractGameState, Integer playerId) {
        return stateToInput((SpinGameState) abstractGameState, playerId);
    }

    private double[] stateToInput(SpinGameState gameState, int playerId) {
        // TODO handle if playerId is 1 -> make it look to network as if it is player 0
        double[] observation = new double[inputSize];
        Planet currentPlanet;
        for (int i = 0; i < gameState.planets.size(); i++) {
            currentPlanet = gameState.planets.get(i);
            observation[i*3] = currentPlanet.ownedBy == 0 && playerId == 1 ? 1 : currentPlanet.ownedBy - playerId;
            observation[(i*3)+1] = currentPlanet.shipCount;
            observation[(i*3)+2] = currentPlanet.growthRate;

            //transit
            Transporter transporter = currentPlanet.getTransporter();
            if (transporter != null) {
                if (transporter.target != null) {
                    observation[20 + (i * 5) + transporter.target] = transporter.payload;
                } else {
                    observation[20 + (i * 5)] = transporter.payload;
                }
            }
        }
        Integer selectedPlanet = ((SourceTargetActuator) gameState.actuators[playerId]).planetSelected;
        if (selectedPlanet != null) {
            observation[(5*3) + selectedPlanet] = 1.0;
        }
        return observation;
    }
};

