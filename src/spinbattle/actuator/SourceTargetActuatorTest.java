package spinbattle.actuator;

import agents.dummy.DoNothingAgent;
import agents.dummy.RandomAgent;
import agents.evo.EvoAgent;
import evodef.DefaultMutator;
import evodef.EvoAlg;
import ga.SimpleRMHC;
import ggi.agents.PolicyEvoAgent;
import ggi.agents.SimpleEvoAgent;
import ggi.core.SimplePlayerInterface;
import logger.sample.DefaultLogger;
import spinbattle.core.FalseModelAdapter;
import spinbattle.core.SpinGameState;
import spinbattle.params.Constants;
import spinbattle.params.SpinBattleParams;
import spinbattle.players.IterANNPlayer;
import spinbattle.players.IterConverter;
import spinbattle.players.TunablePriorityLauncher;
import spinbattle.view.SpinBattleView;
import utilities.JEasyFrame;
import utilities.StatSummary;

import java.awt.*;
import java.util.Random;

public class SourceTargetActuatorTest {

    public static void main(String[] args) throws Exception {
        // to always get the same initial game
        long seed = new Random().nextLong();
        seed = -6330548296303013003L;
        System.out.println("Setting seed to: " + seed);
        SpinBattleParams.random = new Random(seed);
        // SpinBattleParams.random = new Random();

        SpinBattleParams params = new SpinBattleParams();
        // params.transitSpeed *= 2;
        //         params.gravitationalFieldConstant *= 1.0;

        // params.maxTicks = 300;
        params.width = 500;
        params.height = 800;
//        params.nPlanets = 10;
//        params.transitSpeed *= 1.0;
        params.nPlanets = 12;

        params.symmetricMaps = true;
        // params.height = 700;

        // SpinBattleParams altParams = params.copy();

        // params.gravitationalFieldConstant *= 1.0;
        // params.transitSpeed *= 2;
        // params.maxInitialShips *= 3;

        SpinGameState gameState = new SpinGameState().setParams(params).setPlanets();
        gameState = restartStaticGame();

        // BasicLogger basicLogger = new BasicLogger();
        DefaultLogger logger = new DefaultLogger();
        // gameState.setLogger(logger);

        // SpinGameState copy1 = ((SpinGameState) gameState.copy()).setParams(altParams);

        // System.out.println("Logger in copied state: " + copy1.logger);

        SimplePlayerInterface evoAgent = getPolicyEvoAgent(new RandomAgent());
        SimplePlayerInterface player2 = new RandomAgent();
        String checkpoint = "/mnt/storage-ssd/cma-planetwars/ntbea-experiments/single-player-joint-actuator-slow/0/models/1/saved_model";
        SimplePlayerInterface annPlayer = new IterANNPlayer(checkpoint, 6, new IterConverter());
        PolicyEvoAgent annPlayerEvo = getPolicyEvoAgent(annPlayer);
        annPlayerEvo.setUseMutationTransducer(false);
        annPlayerEvo.setMutateUsingPolicy(1.0);
        annPlayerEvo.setAppendUsingPolicy(1.0);
        annPlayerEvo.setInitUsingPolicy(1.0);
        annPlayerEvo.setProbMutation(0.2);

        SimplePlayerInterface randomPlayer = new agents.dummy.RandomAgent();
        // evoAgent = randomPlayer;

        // but now we also need to establish a player

        SpinBattleView view = new SpinBattleView().setParams(gameState.params).setGameState(gameState);
        // HeuristicLauncher launcher = new HeuristicLauncher();
        TunablePriorityLauncher launcher = new TunablePriorityLauncher();
        String title = "Spin Battle Game" ;
        JEasyFrame frame = new JEasyFrame(view, title + ": Waiting for Graphics");
        frame.setLocation(new Point(800, 0));
//        MouseSlingController mouseSlingController = new MouseSlingController();
//        mouseSlingController.setGameState(gameState).setPlayerId(Constants.playerOne);
//        CaveView.addMouseListener(mouseSlingController);
        int launchPeriod = 5; // params.releasePeriod;
        waitUntilReady(view);
        int[] actions = new int[2];

        int frameDelay = 20;

        //SpinBattleParams falseParams = params.copy(); // new SpinBattleParams();
        // params.gravitationalFieldConstant *= 0;
        // falseParams.transitSpeed = 0.00000;
        // falseParams.clampZeroScore = false;
        //FalseModelAdapter falsePlayer = new FalseModelAdapter().setPlayer(evoAgent).setParams(falseParams);

        // may want to stop before the end of the game for demo purposes
        StatSummary winRate = new StatSummary();
        int nTicks = 500;
        int playerFirst;
        Random random = new Random();
        for (int j = 0; j<1000; j++) {
            evoAgent.reset();
            annPlayer.reset();
            gameState = restartStaticGame();
            playerFirst = random.nextInt(2);
            gameState.playerFirst = playerFirst;
            for (int i = 0; i < nTicks && !gameState.isTerminal(); i++) {
                // SpinGameState copy = ((SpinGameState) gameState.copy()).setParams(altParams);
                if (gameState.playerFirst == 0) {
                    actions[0] = annPlayer.getAction(gameState.copy(), 0);
                    actions[1] = evoAgent.getAction(gameState.copy(), 1);
                } else {
                    actions[1] = annPlayer.getAction(gameState.copy(), 1);
                    actions[0] = evoAgent.getAction(gameState.copy(), 0);
                }
                gameState.next(actions);
                // mouseSlingController.update();
                // launcher.makeTransits(gameState, Constants.playerOne);
//            if (i % launchPeriod == 0)
//                launcher.makeTransits(gameState, Constants.playerTwo);
//                SpinGameState viewCopy = (SpinGameState) gameState.copy();
//                viewCopy.logger = gameState.logger;
//                view.setGameState(viewCopy);
//                view.repaint();
//                frame.setTitle(title + " : " + i); //  + " : " + CaveView.getTitle());
//                Thread.sleep(frameDelay);
            }
            System.out.println("made terminal: " + gameState.nTicks);
            System.out.println("score: " + gameState.getScore());
            winRate.add(gameState.getScore() > 0 ? 1 : 0);
        }
        System.out.println(winRate);
        String trajTitle = String.format("g = %.3f, spd = %.3f", params.gravitationalFieldConstant, params.transitSpeed);
        // logger.showTrajectories(params.width, params.height, trajTitle);
        // System.out.println("nTraj: " + logger.getTrajectoryLogger().trajectories.size());
    }

    static void waitUntilReady(SpinBattleView view) throws Exception {
        int i = 0;
        while (view.nPaints == 0) {
            // System.out.println(i++ + " : " + CaveView.nPaints);
            Thread.sleep(50);
        }
    }

    static boolean usePolicyEvoAgent = false;
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

    static PolicyEvoAgent getPolicyEvoAgent(SimplePlayerInterface policy) {
        PolicyEvoAgent evoAgent = new PolicyEvoAgent();
        evoAgent.setUseShiftBuffer(true);
        evoAgent.setNEvals(20);
        evoAgent.setSequenceLength(100);
        evoAgent.setPolicy(policy);
        return evoAgent;
    }

    public static SpinGameState restartStaticGame() {
        // Game Setup
        SpinBattleParams.random = new Random(31415);
        SpinBattleParams params = new SpinBattleParams();
        params.width = (int) (params.width*1.5);
        params.height = (int) (params.height*1.5);
        params.maxTicks = 500;
        params.nPlanets = 6;
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
