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
        SimplePlayerInterface randomPlayer = new agents.dummy.RandomAgent();
        SimplePlayerInterface doNothingPlayer = new DoNothingAgent();

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

    public static SpinGameState restartStaticGame() {
        // Game Setup
        long seed = -6330548296303013003L;
        System.out.println("Setting seed to: " + seed);
        SpinBattleParams.random = new Random(seed);
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

class IterConverter implements BiFunction<AbstractGameState, Integer, double[]> {
    @Override
    public double[] apply(AbstractGameState abstractGameState, Integer playerId) {
        return stateToInput((SpinGameState) abstractGameState, playerId);
    }

    private double[] stateToInput(SpinGameState gameState, int playerId) {
        double  playerShips = 0,
                playerGrowth = 0,
                opponentShips = 0,
                opponentGrowth = 0,
                neutralShips = 0;
        HashMap<Integer, Double> transitCounts = new HashMap<Integer, Double>();

        for (Planet planet : gameState.planets) {
            if (planet.ownedBy == playerId) {
                playerShips += planet.shipCount;
                playerGrowth += planet.growthRate;
            } else if (planet.ownedBy < 2) {
                opponentShips += planet.shipCount;
                opponentGrowth += planet.growthRate;
            } else {
                neutralShips += planet.shipCount;
            }
            Transporter transporter = planet.getTransporter();
            if (transporter != null) {
                double payload = transporter.payload;
                if (transporter.ownedBy != playerId) {
                    payload *= -1;
                }
                if (!transitCounts.containsKey(planet.index)) {
                    transitCounts.put(planet.index, 0.0);
                }
                transitCounts.put(planet.index, transitCounts.get(planet.index) + payload);
            }
        }

        double[] featureVector = new double[18 * (gameState.planets.size() * gameState.planets.size() - gameState.planets.size())];
        int currentIdx = 0;

        Planet src;
        Planet dest;
        for (int i = 0; i < gameState.planets.size(); i++) {
            for (int j = 0; j < gameState.planets.size(); j++) {
                if (i != j) {
                    src = gameState.planets.get(i);
                    dest = gameState.planets.get(j);

                    //global features
                    featureVector[currentIdx++] = playerShips;
                    featureVector[currentIdx++] = playerGrowth;
                    featureVector[currentIdx++] = opponentShips;
                    featureVector[currentIdx++] = opponentGrowth;
                    featureVector[currentIdx++] = neutralShips;

                    // planet specific features
                    featureVector[currentIdx++] = src.shipCount;
                    featureVector[currentIdx++] = src.growthRate;
                    featureVector[currentIdx++] = src.ownedBy == 0 ? 1.0 : 0.0;
                    featureVector[currentIdx++] = src.ownedBy == 1 ? 1.0 : 0.0;
                    featureVector[currentIdx++] = src.ownedBy == 2 ? 1.0 : 0.0;
                    if (transitCounts.containsKey(src.index)) {
                        featureVector[currentIdx++] = transitCounts.get(src.index);
                    } else {
                        featureVector[currentIdx++] = 0;
                    }

                    featureVector[currentIdx++] = dest.shipCount;
                    featureVector[currentIdx++] = dest.growthRate;
                    featureVector[currentIdx++] = dest.ownedBy == 0 ? 1.0 : 0.0;
                    featureVector[currentIdx++] = dest.ownedBy == 1 ? 1.0 : 0.0;
                    featureVector[currentIdx++] = dest.ownedBy == 2 ? 1.0 : 0.0;
                    if (transitCounts.containsKey(dest.index)) {
                        featureVector[currentIdx++] = transitCounts.get(dest.index);
                    } else {
                        featureVector[currentIdx++] = 0;
                    }

                    // planet pair features
                    featureVector[currentIdx++] = src.position.dist(dest.position);
                }
            }
        }
        return featureVector;
    }
}
