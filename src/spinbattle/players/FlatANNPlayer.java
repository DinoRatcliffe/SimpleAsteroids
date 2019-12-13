package spinbattle.players;

import ggi.core.AbstractGameState;
import ggi.core.SimplePlayerInterface;
import org.tensorflow.SavedModelBundle;
import org.tensorflow.Tensor;

import java.util.List;
import java.util.Random;
import java.util.function.BiFunction;

public class FlatANNPlayer implements SimplePlayerInterface {

    private SavedModelBundle savedModel;
    private int numActions;
    private BiFunction<AbstractGameState, Integer, double[]> gameStateConverter;

    public FlatANNPlayer(String checkpointDir, int numActions, BiFunction<AbstractGameState, Integer, double[]> gameStateConverter) {
        savedModel = SavedModelBundle.load(checkpointDir, "serve");
        this.numActions = numActions;
        this.gameStateConverter = gameStateConverter;
    }

    public int getAction(AbstractGameState gameState, int playerId) {
        double[] inputState = gameStateConverter.apply(gameState, playerId);
        double[][] inputData = new double[1][inputState.length];
        for (int i = 0; i < inputState.length; i++) {
            inputData[0][i] = inputState[i];
        }

        Tensor stateTensor = Tensor.create(inputData);

        List<Tensor<?>> result = savedModel.session().runner()
                .feed("serving_default_x:0", stateTensor)
                .fetch("StatefulPartitionedCall:0")
                .fetch("StatefulPartitionedCall:1")
                .run();

        double[][] out_policy = new double[1][numActions];
        result.get(0).copyTo(out_policy);

        int action = 0;
        float accumulation = 0;
        double randomFloat = new Random().nextFloat();

        int max_i = 0;
        double max_value = 0;
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
        return max_i;
    }

    @Override
    public SimplePlayerInterface reset() {
        return this;
    }
}
