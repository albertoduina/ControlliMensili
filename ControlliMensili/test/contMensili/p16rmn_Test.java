package contMensili;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.util.ArrayList;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Line;
import ij.gui.Overlay;
import ij.gui.Plot;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.process.ImageProcessor;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import utils.InputOutput;
import utils.MyConst;
import utils.MyLog;
import utils.MyPlot;
import utils.ReadDicom;
import utils.UtilAyv;

public class p16rmn_Test {

	@Before
	public void setUp() throws Exception {
		new ImageJ(ImageJ.NORMAL);
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testVersion() {
		assertFalse(IJ.versionLessThan("1.42p"));
	}

	@Test
	public final void testParanoias() {
		double in1;
		double out1;
		double out2;
		
		

		
		in1 = 0.003471280;
		out2 = 5.663231976;
		out1 = Math.log(in1);

		MyLog.waitHere("in1= " + in1 + " out1= " + out1 + " out2 OLD= " + out2);

	}

	@Test
	public final void testRegressor() {

		ArrayList<Double> arrMeanNorm = new ArrayList<Double>();
		ArrayList<Double> arrBvalue = new ArrayList<Double>();
		ArrayList<String> arrGradient = new ArrayList<String>();

		arrMeanNorm.add(1.000000);
		arrBvalue.add(0.);
		arrGradient.add("x");

		arrMeanNorm.add(1.000000);
		arrBvalue.add(0.);
		arrGradient.add("y");

		arrMeanNorm.add(1.000000);
		arrBvalue.add(0.);
		arrGradient.add("z");

		// 1 0 0
		arrMeanNorm.add(0.362910177);
		arrBvalue.add(500.);
		arrGradient.add("x");

		arrMeanNorm.add(0.365000509);
		arrBvalue.add(500.);
		arrGradient.add("y");

		arrMeanNorm.add(0.365424684);
		arrBvalue.add(500.);
		arrGradient.add("z");

		arrMeanNorm.add(0.130788286);
		arrBvalue.add(1000.);
		arrGradient.add("x");

		arrMeanNorm.add(0.132087957);
		arrBvalue.add(1000.);
		arrGradient.add("y");

		arrMeanNorm.add(0.132834504);
		arrBvalue.add(1000.);
		arrGradient.add("z");

		arrMeanNorm.add(0.046794937);
		arrBvalue.add(1500.);
		arrGradient.add("x");

		arrMeanNorm.add(0.047405748);
		arrBvalue.add(1500.);
		arrGradient.add("y");

		arrMeanNorm.add(0.048013166);
		arrBvalue.add(1500.);
		arrGradient.add("z");

		arrMeanNorm.add(0.016804099);
		arrBvalue.add(2000.);
		arrGradient.add("x");

		arrMeanNorm.add(0.016804099);
		arrBvalue.add(2000.);
		arrGradient.add("y");

		arrMeanNorm.add(0.017078964);
		arrBvalue.add(2000.);
		arrGradient.add("z");

		arrMeanNorm.add(0.006576402);
		arrBvalue.add(2500.);
		arrGradient.add("x");

		arrMeanNorm.add(0.006349045);
		arrBvalue.add(2500.);
		arrGradient.add("y");

		arrMeanNorm.add(0.006562829);
		arrBvalue.add(2500.);
		arrGradient.add("z");

		arrMeanNorm.add(0.003522346);
		arrBvalue.add(3000.);
		arrGradient.add("x");

		arrMeanNorm.add(0.003671655);
		arrBvalue.add(3000.);
		arrGradient.add("y");

		arrMeanNorm.add(0.003681835);
		arrBvalue.add(3000.);
		arrGradient.add("z");

		double[] out2 = p16rmn_.regressor(arrMeanNorm, arrBvalue, arrGradient);

		MyLog.logVector(out2, "out2");

		MyLog.waitHere("out2[0]= " + out2[0] + " out2[1]= " + out2[1]);

	}

}