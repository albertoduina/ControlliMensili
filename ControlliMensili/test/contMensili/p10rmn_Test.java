package contMensili;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.util.ArrayList;

import ij.IJ;
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

		String home1 = new p10rmn_().findTestImages();
		String path1 = home1 + "A001_testP10";
		String path2 = home1 + "A002_testP10";

		String autoArgs = "0";
		boolean autoCalled = false;
		boolean step = false;
		boolean verbose = true;
		boolean test = true;
		double[] vetReference = new p5rmn_().referenceGe();

		ResultsTable rt1 = p10rmn_.mainUnifor(path1, path2, autoArgs,
				autoCalled, step, verbose, test);

		double[] vetResults = UtilAyv.vectorizeResults(rt1);

		boolean ok = UtilAyv.verifyResults1(vetResults, vetReference,
				MyConst.P5_vetName);
		// ButtonMessages.ModelessMsg("", "CONTINUA");
		assertTrue(ok);
	}

	@Test
	public final void testMainUniforTestSiemens() {

		String home1 = new p10rmn_().findTestImages();
		String path1 = home1 + "A001_testP10";
		String path2 = home1 + "A002_testP10";

		String autoArgs = "0";
		boolean autoCalled = false;
		boolean step = false;
		boolean verbose = true;
		boolean test = true;
		double[] vetReference = new p5rmn_().referenceSiemens();

		ResultsTable rt1 = p10rmn_.mainUnifor(path1, path2, autoArgs,
				autoCalled, step, verbose, test);

		double[] vetResults = UtilAyv.vectorizeResults(rt1);

		boolean ok = UtilAyv.verifyResults1(vetResults, vetReference,
				MyConst.P5_vetName);
		// ButtonMessages.ModelessMsg("", "CONTINUA");
		assertTrue(ok);
	}

	@Test
	public final void testMainUnifor() {
		String path1 = "./Test2/C001_testP10";
		String path2 = "./Test2/C002_testP10";
		String autoArgs = "0";
		boolean autoCalled = false;
		boolean step = false;
		boolean verbose = true;
		boolean test = false;
		p10rmn_.mainUnifor(path1, path2, autoArgs, autoCalled, step, verbose,
				test);
		new WaitForUserDialog("Do something, then click OK.").show();
		// IJ.wait(500);
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

		double[] profile1 = InputOutput
				.readDoubleArrayFromFile((new InputOutput()
						.findResource("/002.txt")));
		MyLog.logVector(profile1, "profile1");

		double[] smooth1 = p10rmn_.smooth3(profile1, 3);

		boolean invert = true;

		double[] profile2 = p10rmn_.createErf(smooth1, invert);
		MyLog.logVector(profile2, "profile2");

		double[] xcoord1 = new double[profile2.length];
		for (int j = 0; j < profile2.length; j++)
			xcoord1[j] = j;
		Plot plot2 = MyPlot.basePlot(profile2, "Plot profilo ERF", Color.blue);

		plot2.show();
		new WaitForUserDialog("Do something, then click OK.").show();
		// IJ.wait(500);

		// plot3.draw();

	}

	@Test
	public final void testPeakDet() {
		double[][] profile1 = InputOutput
				.readDoubleMatrixFromFile((new InputOutput()
						.findResource("/BADProfile.txt")));
		MyLog.logMatrix(profile1, "profile1");
		double delta = 100.0;
		new p10rmn_();
		ArrayList<ArrayList<Double>> matOut = p10rmn_.peakDet(profile1, delta);
		double[][] out = new InputOutput().fromArrayListToDoubleTable(matOut);
		MyLog.logMatrix(out, "out");

		double[] vetx = new double[profile1.length];
		double[] vety = new double[profile1.length];

		for (int j = 0; j < profile1.length; j++)
			vetx[j] = profile1[j][0];
		for (int j = 0; j < profile1.length; j++)
			vety[j] = profile1[j][1];

		Plot plot2 = MyPlot.basePlot(vetx, vety, "P R O F I L O", Color.blue);
		plot2.show();
		// new WaitForUserDialog("Do something, then click OK.").show();
		IJ.wait(1000);

		double expected = 155.2734375;
		assertEquals(expected, out[0][0], 1e-12);
		expected = 3.583803177;
		assertEquals(expected, out[1][0], 1e-12);
		expected = 45.703125;
		assertEquals(expected, out[2][0], 1e-12);
		expected = 163.4765625;
		assertEquals(expected, out[2][1], 1e-12);
		expected = 1990.840209961;
		assertEquals(expected, out[3][0], 1e-12);
		expected = 399.587341309;
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

		double x1 = 5;
		double y1 = 80;
		double x2 = 36;
		double y2 = 42;
		double width = 256;
		double height = 256;

		double[] out = p10rmn_.crossing(x1, y1, x2, y2, width, height);
		MyLog.logVector(out, "out");

	}

}
