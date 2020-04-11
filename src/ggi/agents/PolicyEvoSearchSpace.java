package ggi.agents;

import agents.dummy.RandomAgent;
import evodef.AnnotatedFitnessSpace;
import evodef.EvolutionLogger;
import evodef.SearchSpace;
import evodef.SearchSpaceUtil;
import ggi.core.SimplePlayerInterface;
import ntuple.params.DoubleParam;
import ntuple.params.Param;
import spinbattle.actuator.SourceTargetActuator;
import spinbattle.actuator.SourceTargetJointActuator;
import spinbattle.core.SpinGameState;
import spinbattle.params.SpinBattleParams;
import spinbattle.players.FlatANNPlayer;
import spinbattle.players.FlatConverter;
import spinbattle.players.IterANNPlayer;
import spinbattle.players.IterConverter;
import utilities.ElapsedTimer;
import utilities.StatSummary;

import java.util.Random;

public class PolicyEvoSearchSpace implements AnnotatedFitnessSpace {

    public static void main(String[] args) {
        String netType = args[0];
        String checkpoint = args[1];

        SimplePlayerInterface annPlayer;
        if (netType.equals("flat")) {
            annPlayer = new FlatANNPlayer(checkpoint, 6, new FlatConverter()); // Change to be argument passed in
        } else {
            annPlayer = new IterANNPlayer(checkpoint, 6, new IterConverter());
        }

        PolicyEvoSearchSpace searchSpace = new PolicyEvoSearchSpace(annPlayer, 6);
        int[] point = SearchSpaceUtil.randomPoint(searchSpace);

        System.out.println(searchSpace.report(point));
        System.out.println();
        System.out.println("Size: " + SearchSpaceUtil.size(searchSpace));
        ElapsedTimer timer = new ElapsedTimer();
        System.out.println("Value: " + searchSpace.evaluate(point));
        System.out.println(timer);
    }

    double[] probMutation = {0.0, 0.2, 0.4, 0.6, 0.8, 1.0};
    double[] initUsingPolicy = {0.0, 0.2, 0.4, 0.6, 0.8, 1.0};
    double[] appendUsingPolicy = {0.0, 0.2, 0.4, 0.6, 0.8, 1.0};
    double[] mutateUsingPolicy = {0.0, 0.2, 0.4, 0.6, 0.8, 1.0};

    int[] nValues = new int[]{probMutation.length, initUsingPolicy.length,
            appendUsingPolicy.length, mutateUsingPolicy.length};
    int nDims = nValues.length;

    int evalGames = 1;

    SimplePlayerInterface guidanceAgent;
    PolicyEvoAgent evoAgent;
    PolicyEvoAgent opponentAgent;

    static int probMutationIndex = 0;
    static int initUsingPolicyIndex = 1;
    static int appendUsingPolicyIndex = 2;
    static int mutateUsingPolicyIndex = 3;

    // log the solutions found
    public EvolutionLogger logger;
    int planets;
    public PolicyEvoSearchSpace(SimplePlayerInterface guidanceAgent, int planets) {
        this.guidanceAgent = guidanceAgent;
        this.planets = planets;
        evoAgent = getPolicyEvoAgent(guidanceAgent);
        evoAgent.setUseMutationTransducer(false);

        opponentAgent = getPolicyEvoAgent(new RandomAgent());

        this.logger = new EvolutionLogger();
    }

    @Override
    public Param[] getParams() {
        return new Param[]{
                new DoubleParam().setArray(probMutation).setName("Prob Mutation"),
                new DoubleParam().setArray(initUsingPolicy).setName("Prob Init using Policy"),
                new DoubleParam().setArray(appendUsingPolicy).setName("Prob Append using Policy"),
                new DoubleParam().setArray(mutateUsingPolicy).setName("Prob Mutate using Policy")
        };
    }

    @Override
    public int nDims() {
        return nDims;
    }

    @Override
    public int nValues(int i) {
        return nValues[i];
    }

    @Override
    public void reset() {
        logger.reset();
    }

    @Override
    public double evaluate(int[] solution) {
        // run solution through games
        evoAgent.setProbMutation(solution[probMutationIndex]);
        evoAgent.setInitUsingPolicy(solution[initUsingPolicyIndex]);
        evoAgent.setAppendUsingPolicy(solution[appendUsingPolicyIndex]);
        evoAgent.setMutateUsingPolicy(solution[mutateUsingPolicyIndex]);

        StatSummary stats = new StatSummary();
        SpinGameState gameState;

        Random random = new Random();
        int playerFirst;


        int[] actions = new int[2];
        for (int i = 0; i < evalGames; i++) {
            evoAgent.reset();
            opponentAgent.reset();
            playerFirst = random.nextInt(2);
            gameState = restartStaticGame(this.planets);
            gameState.playerFirst = playerFirst;
            while(!gameState.isTerminal()) {
                if (playerFirst == 0) {
                    actions[0] = evoAgent.getAction(gameState, 0);
                    actions[1] = opponentAgent.getAction(gameState, 1);
                } else {
                    actions[1] = evoAgent.getAction(gameState, 1);
                    actions[0] = opponentAgent.getAction(gameState, 0);
                }
                gameState = (SpinGameState) gameState.next(actions);
            }

            stats.add(gameState.getScore());

        }
        double meanScore = stats.mean();
        logger.log(meanScore, solution, false);
        return meanScore;
    }

    public String report(int[] solution) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("probMutation:     %.2f\n", probMutation[solution[probMutationIndex]]));
        sb.append(String.format("initUsingPolicy:     %.2f\n", initUsingPolicy[solution[initUsingPolicyIndex]]));
        sb.append(String.format("appendUsingPolicy:     %.2f\n", appendUsingPolicy[solution[appendUsingPolicyIndex]]));
        sb.append(String.format("mutateUsingPolicy:     %.2f\n", mutateUsingPolicy[solution[mutateUsingPolicyIndex]]));
        return sb.toString();
    }

    public String csvReport(int[] solution) {
        StringBuilder sb = new StringBuilder();
        sb.append("probMutation, initUsingPolicy, appendUsingPolicy, mutateUsingPolicy\n");
        sb.append(String.format("%.2f, %.2f, %.2f, %.2f\n",
                probMutation[solution[probMutationIndex]],
                initUsingPolicy[solution[initUsingPolicyIndex]],
                appendUsingPolicy[solution[appendUsingPolicyIndex]],
                mutateUsingPolicy[solution[mutateUsingPolicyIndex]]));
        return sb.toString();
    }

    @Override
    public SearchSpace searchSpace() {
        return this;
    }

    @Override
    public int nEvals() {
        return logger.nEvals();
    }

    @Override
    public EvolutionLogger logger() {
        return logger;
    }

    @Override
    public boolean optimalFound() {
        return false;
    }

    @Override
    public Double optimalIfKnown() {
        return null;
    }

    static PolicyEvoAgent getPolicyEvoAgent(SimplePlayerInterface policy) {
        PolicyEvoAgent evoAgent = new PolicyEvoAgent();
        evoAgent.setUseShiftBuffer(true);
        evoAgent.setNEvals(20);
        evoAgent.setSequenceLength(100);
        evoAgent.setPolicy(policy);
        return evoAgent;
    }

    public static SpinGameState restartStaticGame(int planets) {
        // Game Setup
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
