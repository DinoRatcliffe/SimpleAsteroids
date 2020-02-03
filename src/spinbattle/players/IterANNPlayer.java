package spinbattle.players;

import ggi.core.AbstractGameState;
import ggi.core.SimplePlayerInterface;
import org.tensorflow.SavedModelBundle;
import org.tensorflow.Tensor;

import java.util.List;
import java.util.Random;
import java.util.function.BiFunction;

public class IterANNPlayer implements SimplePlayerInterface {

    private SavedModelBundle savedModel;
    private int numActions;
    private BiFunction<AbstractGameState, Integer, double[]> gameStateConverter;
    public boolean insideAction = false;
    private int nextAction = 0;

    public IterANNPlayer(String checkpointDir, int numActions, BiFunction<AbstractGameState, Integer, double[]> gameStateConverter) {
        savedModel = SavedModelBundle.load(checkpointDir, "serve");
        this.numActions = numActions;
        this.gameStateConverter = gameStateConverter;
    }

    public int getAction(AbstractGameState gameState, int playerId) {
        if (insideAction) {
            insideAction = false;
            return nextAction;
        }
        double[] inputState = gameStateConverter.apply(gameState, playerId);
        double[][] inputData = new double[1][inputState.length];
        for (int i = 0; i < inputState.length; i++) {
            inputData[0][i] = inputState[i];
        }

        Tensor stateTensor = Tensor.create(inputData);

        List<Tensor<?>> result = savedModel.session().runner()
                .feed("serving_default_x:0", stateTensor)
                .fetch("StatefulPartitionedCall:0")
                .run();

        double[][] out_policy = new double[1][numActions * numActions - numActions];
        result.get(0).copyTo(out_policy);

        int action = 0;
        float accumulation = 0;
        double randomFloat = new Random().nextFloat();

        int max_i = 0;
        double max_value = out_policy[0][0];
        for (int i = 0; i < out_policy[0].length; i++) {
            randomFloat -= out_policy[0][i];
            if (randomFloat <= 0) {
                //action = i;
                //break;
            }

            if (out_policy[0][i] > max_value) {
                max_i = i;
                max_value = out_policy[0][i];
            }

        }

        //convert max_i to two actions
        // matches how python itertools.permutations creates pairs
        int step = numActions - 1;
        int action_1 = max_i / step;
        int action_2 = max_i % step;
        if (action_2 >= action_1) {
            action_2 += 1;
        }
        nextAction = action_2;
        insideAction = true;
        return action_1;
    }

    @Override
    public SimplePlayerInterface reset() {
        this.insideAction = false;
        return this;
    }
}
