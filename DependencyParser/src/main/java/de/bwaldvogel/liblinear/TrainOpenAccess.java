package de.bwaldvogel.liblinear;

import com.google.common.base.Joiner;

import java.io.File;
import java.io.IOException;

/**
 * This class opens up some package-private methods to public in the liblinear
 * java package, so that liblinear is actually sensible to use on anything other
 * than the command line...
 *
 * Created by Andrew D. Robertson on 14/04/2014.
 */
public class TrainOpenAccess extends Train {

    public TrainOpenAccess() {
        super();
    }

    @Override
    public double getBias() {
        return super.getBias();
    }

    @Override
    public Problem getProblem() {
        return super.getProblem();
    }

    @Override
    public Parameter getParameter() {
        return super.getParameter();
    }

    @Override
    public void readProblem(String filename) throws IOException, InvalidInputDataException {
        super.readProblem(filename);
    }

    /**
     * This is a workaround to get the ability to parse option switches sensibly. Options are provided just how
     * they would be at command line. But if you read the source and see how I'm calling this thing in ClassifierLinearSVM,
     * you'll see that passing those files fills no particular purpose other than making the command line switch
     * function play nice, because I'm separately calling readProblem() with trainingData, and saveModel with model.
     *
     * UGH.
     */
    public void parseCommandline(String options, File trainingData, File model){
        String[] optionArgs = options.split("\\s+");
        String[] args = new String[optionArgs.length+2];
        System.arraycopy(optionArgs, 0, args, 0, optionArgs.length);
        args[optionArgs.length] = trainingData.getAbsolutePath();
        args[optionArgs.length+1] = model.getAbsolutePath();
        System.out.println(Joiner.on(" ").join(args));
        parse_command_line(args);
    }
}
