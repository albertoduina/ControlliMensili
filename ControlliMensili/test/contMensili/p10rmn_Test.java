package contMensili;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.util.ArrayList;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Plot;
import ij.gui.WaitForUserDialog;
import ij.measure.ResultsTable;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import utils.InputOutput;
import utils.MyConst;
import utils.MyLog;
import utils.MyPlot;
import utils.UtilAyv;

public class p10rmn_Test {
	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
		// new WaitForUserDialog("Do something, then click OK.").show();

	}

	@Test
	public final void testMainUniforTestGe() {

		// 16 dec 2011 sistemato, ora funziona in automatico

		String home1 = new p10rmn_().findTestImages();
		String path1 = home1 + "C001_testP10";
		String path2 = home1 + "C002_testP10";

		String autoArgs = "0";
		boolean autoCalled = false;
		boolean step = false;
		boolean verbose = true;
		boolean test = true;
		boolean fast = true;
		double[] vetReference = new p10rmn_().referenceSiemens();
		double profond = 30;

		ResultsTable rt1 = p10rmn_.mainUnifor(path1, path2, autoArgs, profond,
				"", autoCalled, step, verbose, test, fast);

		double[] vetResults = UtilAyv.vectorizeResults(rt1);

		boolean ok = UtilAyv.verifyResults1(vetResults, vetReference, null);
		assertTrue(ok);
	}

	@Test
	public final void testMainUniforTestSiemens() {

		// 16 dec 2011 sistemato, ora funziona in automatico

		String home1 = new p10rmn_().findTestImages();
		String path1 = home1 + "C001_testP10";
		String path2 = home1 + "C002_testP10";

		String autoArgs = "0";
		boolean autoCalled = false;
		boolean step = false;
		boolean verbose = true;
		boolean test = true;
		boolean fast = true;
		double[] vetReference = new p10rmn_().referenceSiemens();
		double profond = 30;

		ResultsTable rt1 = p10rmn_.mainUnifor(path1, path2, autoArgs, profond,
				"", autoCalled, step, verbose, test, fast);

		double[] vetResults = UtilAyv.vectorizeResults(rt1);

		boolean ok = UtilAyv.verifyResults1(vetResults, vetReference,
				MyConst.P5_vetName);
		assertTrue(ok);
	}

	@Test
	public final void testMainUnifor() {

		// 16 dec 2011 sistemato, ora funziona in automatico

		String path1 = "./Test2/C001_testP10";
		String path2 = "./Test2/C002_testP10";
		String autoArgs = "0";
		boolean autoCalled = false;
		boolean step = false;
		boolean verbose = false;
		boolean test = false;
		double profond = 30;
		boolean fast = true;
		ResultsTable rt1 = p10rmn_.mainUnifor(path1, path2, autoArgs, profond,
				"info10", autoCalled, step, verbose, test, fast);

		double[] vetResults = UtilAyv.vectorizeResults(rt1);
		double[] vetReference = new p10rmn_().referenceSiemens();

		boolean ok = UtilAyv.verifyResults1(vetResults, vetReference, null);
		assertTrue(ok);
	}

	@Test
	public final void testPositionSearch() {

		// 16 dic 2011 sistemato, ora funziona in automatico

		String path1 = "./Test2/C001_testP10";

		ImagePlus imp11 = UtilAyv.openImageNoDisplay(path1, true);

		boolean autoCalled = false;
		boolean step = false;
		boolean verbose = false;
		boolean test = false;
		boolean fast = true;
		double profond = 30;

		double out2[] = p10rmn_.positionSearch(imp11, profond, "", autoCalled,
				step, verbose, test, fast);

		double[] expected = { 159.0, 105.0, 118.0, 133.0, 202.0, 77.0,
				33.690067525979785 };
		// MyLog.logVector(out2, "out2");
		boolean ok = UtilAyv.verifyResults1(expected, out2, null);
		assertTrue(ok);
	}

	@Test
	public final void testSelfTestSilent() {
		new p10rmn_().selfTestSilent();
	}

	@Test
	public final void testInterpola() {
		double ax = 117.0;
		double ay = 45.0;
		double bx = 70.0;
		double by = 78.0;
		double prof = 20.0;
		double[] out = p10rmn_.interpola(ax, ay, bx, by, prof);
		MyLog.logVector(out, "out");

	}

	@Test
	public final void testAngoloRad() {
		double ax = 117.0;
		double ay = 45.0;
		double bx = 70.0;
		double by = 78.0;
		// double prof = 20.0;
		double out = p10rmn_.angoloRad(ax, ay, bx, by);
		IJ.log("angoloRad= " + out + " angoloDeg= "
				+ IJ.d2s(Math.toDegrees(out)));
	}

	@Test
	public final void testCreateErf() {

		// 16 dec 2011 sistemato, ora funziona in automatico senza bisogno di
		// visualizzare il profilo

		double[] profile1 = InputOutput
				.readDoubleArrayFromFile((new InputOutput()
						.findResource("/002.txt")));
		MyLog.logVector(profile1, "profile1");

		double[] smooth1 = p10rmn_.smooth3(profile1, 3);

		boolean invert = true;

		double[] profile2 = p10rmn_.createErf(smooth1, invert);
		MyLog.logVector(profile2, "profile2");

		double[] expected = InputOutput
				.readDoubleArrayFromFile((new InputOutput()
						.findResource("/003.txt")));

		boolean ok = UtilAyv.verifyResults1(expected, profile2, null);
		assertTrue(ok);

		// Plot plot2 = MyPlot.basePlot(profile2, "Plot profilo ERF",
		// Color.blue);
		// plot2.show();
		// MyLog.waitHere("display plot, dare OK");
		// IJ.wait(200);

	}

	@Test
	public final void testPeakDet() {

		// 16 dec 2011 sistemato, ora funziona in automatico senza bisogno di
		// visualizzare il profilo

		double[][] profile1 = InputOutput
				.readDoubleMatrixFromFile((new InputOutput()
						.findResource("/BADProfile.txt")));
		double delta = 100.0;
		new p10rmn_();
		ArrayList<ArrayList<Double>> matOut = p10rmn_.peakDet(profile1, delta);
		double[][] out = new InputOutput().fromArrayListToDoubleTable(matOut);

		double[] vetx = new double[profile1.length];
		double[] vety = new double[profile1.length];

		for (int j = 0; j < profile1.length; j++)
			vetx[j] = profile1[j][0];
		for (int j = 0; j < profile1.length; j++)
			vety[j] = profile1[j][1];

		// Plot plot2 = MyPlot.basePlot(vetx, vety, "P R O F I L O",
		// Color.blue);
		// plot2.show();
		// new WaitForUserDialog("Do something, then click OK.").show();
		// IJ.wait(200);

		double expected = 66.796875;
		assertEquals(expected, out[0][0], 1e-12);
		expected = 14.09287783266325;
		assertEquals(expected, out[1][0], 1e-12);
		expected = 9.9609375;
		assertEquals(expected, out[2][0], 1e-12);
		expected = 128.3203125;
		assertEquals(expected, out[2][1], 1e-12);
		expected = 445.2493818993196;
		assertEquals(expected, out[3][0], 1e-12);
		expected = 199.34767076939997;
		assertEquals(expected, out[3][1], 1e-12);
	}

	@Test
	public final void testFromPointsToEquLineExplicit() {

		double x1 = 0;
		double y1 = 0;
		double x2 = 255;
		double y2 = 0;
		double[] out1 = null;

		x1 = 0;
		y1 = 0;
		x2 = 0;
		y2 = 255;

		out1 = p10rmn_.fromPointsToEquLineExplicit(x1, y1, x2, y2);

		MyLog.logVector(out1, "out1");
	}

	@Test
	public final void testFromPointsToEquLineImplicit() {

		double x1 = 0;
		double y1 = 0;
		double x2 = 255;
		double y2 = 0;
		double[] out1 = null;

		x1 = 120;
		y1 = 30;
		x2 = 120;
		y2 = 50;

		out1 = p10rmn_.fromPointsToEquLineImplicit(x1, y1, x2, y2);

		MyLog.logVector(out1, "out1");
	}

	@Test
	public final void testLiangBarsky() {

		double edgeLeft = 0.;
		double edgeRight = 255.;
		double edgeBottom = 0.;
		double edgeTop = 255.;
		double x0src = 20.;
		double y0src = -30.;
		double x1src = 200.;
		double y1src = 290.;

		double[] out = p10rmn_.liangBarsky(edgeLeft, edgeRight, edgeBottom,
				edgeTop, x0src, y0src, x1src, y1src);

		MyLog.logVector(out, "out");
	}

	@Test
	public final void testCrossing() {

		double x0 = 54.14;
		double y0 = 99.69;
		double x1 = 94.53;
		double y1 = 57.58;
		double width = 220;
		double height = 220;

		double[] out = p10rmn_.crossing(x0, y0, x1, y1, width, height);
		MyLog.logVector(out, "out");

		double[] vetResults1 = { 149.75812158632155, 0.0, 0.0, 156.135540975489 };
		boolean ok = UtilAyv.verifyResults1(vetResults1, out, null);
		assertTrue(ok);

		x0 = 25.78;
		y0 = 205.05868;
		x1 = 151.25;
		y1 = 132.34;

		out = p10rmn_.crossing(x0, y0, x1, y1, width, height);
		MyLog.logVector(out, "out");

		double[] vetResults2 = { 2.0627437110885412E-6, 220.0, 220.0,
				92.49454491113413 };
		ok = UtilAyv.verifyResults1(vetResults2, out, null);
		assertTrue(ok);

	}

	@Test
	public final void testCountPixTest() {

		String path1 = "./Test2/C001_testP10";
		ImagePlus imp1 = UtilAyv.openImageMaximized(path1);

		int xPos = 117;
		int yPos = 117;
		int sqNEA = 20;
		double checkPixels = 58.064;
		boolean test = true;
		// rispetto a questo test ricordarsi che la roi assegnata è
		// assolutamente arbitraria, si deve solo vedere un quadrato chiaro, più
		// o meno al centro dell'immagine

		int pixx = p10rmn_.countPixTest(imp1, xPos, yPos, sqNEA, checkPixels,
				test);

		// IJ.log("pixx= " + pixx);
		IJ.wait(200);
		assertEquals(pixx, 441);
	}

}
