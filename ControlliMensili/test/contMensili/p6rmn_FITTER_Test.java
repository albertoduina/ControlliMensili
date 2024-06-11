package contMensili;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.awt.Color;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Line;
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

public class p6rmn_FITTER_Test {

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
	public final void testPeakFitterFWHM_SUPERGAUSSIAN() {
		String path1 = "data/P6/SolaGavardo_HT5A";
//	ImagePlus imp1 = UtilAyv.openImageMaximized(path1);

		ImagePlus imp1 = UtilAyv.openImageNoDisplay(path1, false);

		String name1 = "data/P6/primoCuneoGavardo.txt";
		String name2 = "data/P6/SolaGavardo004cuneo5.txt";
		String name3 = "data/P6/slab1_5mm.txt";
		String name4 = "data/P6/slab2.txt";
		String name5 = "data/P6/profile1.txt";

		double[][] profile1 = InputOutput.readDoubleMatrixFromFile(name5);

		MyLog.waitHere();

		boolean slab = true;
		;
		boolean step = true;
		boolean junitTest = true;
		// double[] outGuessing= {-12.545, 46.306, 5.312, 71.324, 3.00 };
		double[] outGuessing = { 0, 50, 10, 40, 3.00 };

		double[] out1 = new p6rmn_FITTER().peakFitterFWHM_SUPERGAUSSIAN(imp1, profile1, slab, step, junitTest, 10,
				outGuessing);
		MyLog.waitHere();

	}

	@Test
	public final void testBaselineCorrection_FITTER() {
		
		String path1 = "data/P6/SolaGavardo_HT5A";
//	ImagePlus imp1 = UtilAyv.openImageMaximized(path1);

		ImagePlus imp1 = UtilAyv.openImageNoDisplay(path1, false);

		String name1 = "data/P6/primoCuneo5mmGavardo.txt";

		double[][] profi11 = InputOutput.readDoubleMatrixFromFileTwo(name1);

		boolean slab = true;
		boolean step = true;
		boolean junitTest = true;

		double[][] out1 = new p6rmn_FITTER().baselineCorrection_FITTER(imp1, profi11, step);
		MyLog.waitHere();

	}

	@Test
	public final void testLocalizzatoreDerivataCuneo5mm() {

		String name1 = "data/P6/primoCuneo5mmGavardo.txt";

		double[][] profi11 = InputOutput.readDoubleMatrixFromFileTwo(name1);

		boolean slab = true;
		boolean step = true;
		boolean smooth=true;
		boolean junitTest = true;

		int[] out = new p6rmn_FITTER().localizzatoreDerivata(profi11, smooth, step);

		MyLog.waitHere();

	}	@Test
	
	public final void testLocalizzatoreDerivataCuneo2mm() {

		String name1 = "data/P6/primoCuneo2mmGavardo.txt";

		double[][] profi11 = InputOutput.readDoubleMatrixFromFileTwo(name1);

		boolean slab = true;
		boolean step = true;
		boolean smooth=true;
		boolean junitTest = true;

		int[] out = new p6rmn_FITTER().localizzatoreDerivata(profi11, smooth, step);

		MyLog.waitHere();

	}
	
	@Test
	public final void testLocalizzatoreDerivataSlab5mm() {

		String name1 = "data/P6/primaSlab5mmGavardo.txt";

		double[][] profi11 = InputOutput.readDoubleMatrixFromFileTwo(name1);

		boolean slab = true;
		boolean step = true;
		boolean smooth=true;
		boolean junitTest = true;

		int[] out = new p6rmn_FITTER().localizzatoreDerivata(profi11, smooth, step);

		MyLog.waitHere();

	}
	
	@Test
	public final void testLocalizzatoreDerivataSlab2mm() {

		String name1 = "data/P6/primaSlab2mmGavardo.txt";

		double[][] profi11 = InputOutput.readDoubleMatrixFromFileTwo(name1);

		boolean slab = true;
		boolean step = true;
		boolean smooth=true;
		boolean junitTest = true;

		int[] out = new p6rmn_FITTER().localizzatoreDerivata(profi11, smooth, step);

		MyLog.waitHere();

	}

}