package spinbattle.players;

import ggi.core.AbstractGameState;
import ggi.core.SimplePlayerInterface;
import org.tensorflow.SavedModelBundle;
import org.tensorflow.Tensor;

import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.util.List;
import java.util.Random;
import java.util.function.BiFunction;

public class IterANNPlayer implements SimplePlayerInterface {

    private SavedModelBundle savedModel;
    private int numActions;
    private BiFunction<AbstractGameState, Integer, double[]> gameStateConverter;
    private double[] inputState;
    private boolean initInput = false;
    private DoubleBuffer doubleBuffer;
    double[][] out_policy;

    private long[] shape;

    public IterANNPlayer(String checkpointDir, int numActions, BiFunction<AbstractGameState, Integer, double[]> gameStateConverter) {
        savedModel = SavedModelBundle.load(checkpointDir, "serve");
        this.numActions = numActions;
        this.gameStateConverter = gameStateConverter;
        shape = new long[2];
    }

    public int getAction(AbstractGameState gameState, int playerId) {
        inputState = gameStateConverter.apply(gameState, playerId);
        if (!initInput) {
            shape[0] = 1;
            shape[1] = inputState.length;
            initInput = true;
            doubleBuffer = DoubleBuffer.allocate(inputState.length);
            out_policy = new double[1][numActions * numActions - numActions];
        }

        doubleBuffer.clear();

        for (int i = 0; i < inputState.length; i++) {
            doubleBuffer.put(inputState[i]);
        }
        doubleBuffer.flip();

        Tensor stateTensor = Tensor.create(shape, doubleBuffer);

        List<Tensor<?>> result = savedModel.session().runner()
                .feed("serving_default_x:0", stateTensor)
                .fetch("StatefulPartitionedCall:0")
                .run();

        result.get(0).copyTo(out_policy);
        stateTensor.close();
        for (Tensor t : result) {
            t.close();
        }

        int action = 0;
        float accumulation = 0;
        double randomFloat = new Random().nextFloat();

        int max_i = 0;

        float sum = 0;
        for (int j = 0; j < out_policy[0].length; j++) {
            sum += out_policy[0][j];
        }

        boolean stochastic = sum <= 1.0 && sum >= 0.99;

        double max_value = out_policy[0][0];
        for (int i = 0; i < out_policy[0].length; i++) {
            if (stochastic) {
                randomFloat -= out_policy[0][i];
                if (randomFloat <= 0) {
                    max_i = i;
                    break;
                }
            } else {
                if (out_policy[0][i] > max_value) {
                    max_i = i;
                    max_value = out_policy[0][i];
                }
            }
        }

        return max_i;
    }

    @Override
    public SimplePlayerInterface reset() {
        return this;
    }
}
