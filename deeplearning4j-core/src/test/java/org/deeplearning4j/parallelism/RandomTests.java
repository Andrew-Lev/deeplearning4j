package org.deeplearning4j.parallelism;

import org.deeplearning4j.nn.api.Model;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.WorkspaceMode;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.layers.ConvolutionLayer;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.conf.layers.SubsamplingLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.junit.Test;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.Assert.assertEquals;

/**
 * @author raver119@gmail.com
 */
public class RandomTests {

    /**
     * In this test we check for equality of model params after initialization in different threads
     *
     * @throws Exception
     */
    @Test
    public void testModelInitialParamsEquality1() throws Exception {
        final List<Model> models = new CopyOnWriteArrayList<>();

        for (int i = 0; i < 4; i++) {
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder().seed(119).iterations(1) // Training iterations as above
                                    .regularization(true).l2(0.0005)
                                    /*
                                    Uncomment the following for learning decay and bias
                                     */
                                    .learningRate(.01)//.biasLearningRate(0.02)
                                    //.learningRateDecayPolicy(LearningRatePolicy.Inverse).lrPolicyDecayRate(0.001).lrPolicyPower(0.75)
                                    .weightInit(WeightInit.XAVIER)
                                    .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                                    .updater(Updater.NESTEROVS).momentum(0.9)
                                    .trainingWorkspaceMode(WorkspaceMode.SINGLE).list()
                                    .layer(0, new ConvolutionLayer.Builder(5, 5)
                                                    //nIn and nOut specify depth. nIn here is the nChannels and nOut is the number of filters to be applied
                                                    .nIn(1).stride(1, 1).nOut(20).activation(Activation.IDENTITY)
                                                    .build())
                                    .layer(1, new SubsamplingLayer.Builder(SubsamplingLayer.PoolingType.MAX)
                                                    .kernelSize(2, 2).stride(2, 2).build())
                                    .layer(2, new ConvolutionLayer.Builder(5, 5)
                                                    //Note that nIn need not be specified in later layers
                                                    .stride(1, 1).nOut(50).activation(Activation.IDENTITY).build())
                                    .layer(3, new SubsamplingLayer.Builder(SubsamplingLayer.PoolingType.MAX)
                                                    .kernelSize(2, 2).stride(2, 2).build())
                                    .layer(4, new DenseLayer.Builder().activation(Activation.RELU).nOut(500).build())
                                    .layer(5, new OutputLayer.Builder(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD)
                                                    .nOut(10).activation(Activation.SOFTMAX).build())
                                    .setInputType(InputType.convolutionalFlat(28, 28, 1)) //See note below
                                    .backprop(true).pretrain(false).build();

                    MultiLayerNetwork network = new MultiLayerNetwork(conf);
                    network.init();

                    models.add(network);
                }
            });

            thread.start();
            thread.join();
        }


        // at the end of day, model params has to
        for (int i = 0; i < models.size(); i++) {
            assertEquals(models.get(0).params(), models.get(i).params());
        }
    }


    @Test
    public void testRngInitMLN() {
        Nd4j.getRandom().setSeed(12345);

        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder().seed(12345).activation(Activation.TANH)
                        .weightInit(WeightInit.XAVIER).list()
                        .layer(0, new DenseLayer.Builder().nIn(10).nOut(10).build())
                        .layer(1, new DenseLayer.Builder().nIn(10).nOut(10).build()).layer(2,
                                        new OutputLayer.Builder(LossFunctions.LossFunction.MCXENT)
                                                        .activation(Activation.SOFTMAX).nIn(10).nOut(10).build())
                        .build();

        String json = conf.toJson();

        MultiLayerNetwork net1 = new MultiLayerNetwork(conf);
        net1.init();

        MultiLayerNetwork net2 = new MultiLayerNetwork(conf);
        net2.init();

        assertEquals(net1.params(), net2.params());

        MultiLayerConfiguration fromJson = MultiLayerConfiguration.fromJson(json);

        Nd4j.getRandom().setSeed(987654321);
        MultiLayerNetwork net3 = new MultiLayerNetwork(fromJson);
        net3.init();

        assertEquals(net1.params(), net3.params());
    }
}
