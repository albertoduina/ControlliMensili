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
	public final void testMainUniforTestSiemens() {

		String[] list = { "S1SA_01testP5", "S1SA_02testP5", "S1SA_03testP5" };
		String[] path = new InputOutput().findListTestImages2(
				MyConst.TEST_FILE, list, MyConst.TEST_DIRECTORY);

		String autoArgs = "0";
		boolean autoCalled = false;
		boolean step = false;
		boolean verbose = true;
		boolean test = true;
		double[] vetReference = new p5rmn_().referenceSiemens();
		boolean verticalProfile = true;
		int sqX = MyConst.P5_X_ROI_TESTSIEMENS;
		int sqY = MyConst.P5_Y_ROI_TESTSIEMENS;

		ResultsTable rt1 = p5rmn_.mainUnifor(path, sqX, sqY, autoArgs,
				verticalProfile, autoCalled, step, verbose, test);

		double[] vetResults = UtilAyv.vectorizeResults(rt1);

		boolean ok = UtilAyv.verifyResults1(vetResults, vetReference,
				MyConst.P5_vetName);
		assertTrue(ok);
	}

	@Test
	public final void testMainUnifor() {
		String path1 = "./Test2/0001";
		String path2 = "./Test2/HUSA_002testP3";
		String autoArgs = "0";
		boolean autoCalled = false;
		boolean step = false;
		boolean verbose = true;
		boolean test = true;
		p10rmn_.prepUnifor(path1, path2, autoArgs, autoCalled, step, verbose,
				test);
		new WaitForUserDialog("Do something, then click OK.").show();
		// IJ.wait(500);
	}

	@Test
	public final void testMainUniforTestGe() {

		String[] list = { "CTSA2_01testP5", "CTSA2_02testP5", "CTSA2_03testP5" };
		String[] path = new InputOutput().findListTestImages2(
				MyConst.TEST_FILE, list, MyConst.TEST_DIRECTORY);

		String autoArgs = "0";
		boolean autoCalled = false;
		boolean step = false;
		boolean verbose = true;
		boolean test = true;
		boolean verticalProfile = false;
		double[] vetReference = new p5rmn_().referenceGe();
		int sqX = MyConst.P5_X_ROI_TESTGE;
		int sqY = MyConst.P5_Y_ROI_TESTGE;

		ResultsTable rt1 = p5rmn_.mainUnifor(path, sqX, sqY, autoArgs,
				verticalProfile, autoCalled, step, verbose, test);

		double[] vetResults = UtilAyv.vectorizeResults(rt1);

		boolean ok = UtilAyv.verifyResults1(vetResults, vetReference,
				MyConst.P5_vetName);
		// ButtonMessages.ModelessMsg("", "CONTINUA");
		assertTrue(ok);
	}

	@Test
	public final void testMainUniforTestSilent() {
		new p5rmn_().selfTestSilent();
	}

	@Test
	public final void testOverlayGrid() {

		ImagePlus imp1 = UtilAyv.openImageMaximized(".\\Test2\\S1SA_01testP5");
		boolean verbose = true;
		p5rmn_.overlayGrid(imp1, MyConst.P5_GRID_NUMBER, verbose);
		IJ.wait(300);

	}

	@Test
	public final void testInterpola() {
		double ax = 117.0;
		double ay = 45.0;
		double bx = 70.0;
		double by = 78.0;
		double prof = 20.0;
		double[] out = new p10rmn_().interpola(ax, ay, bx, by, prof);

	}

	@Test
	public final void testAngoloRad() {
		double ax = 117.0;
		double ay = 45.0;
		double bx = 70.0;
		double by = 78.0;
		double prof = 20.0;
		double out = new p10rmn_().angoloRad(ax, ay, bx, by);
		IJ.log("angoloRad= " + out + " angoloDeg= "
				+ IJ.d2s(Math.toDegrees(out)));
	}

	@Test
	public final void testCreateErf() {

		double[] profile1 = InputOutput
				.readDoubleArrayFromFile((new InputOutput()
						.findResource("/002.txt")));
		MyLog.logVector(profile1, "profile1");

		double[] smooth1 = new p10rmn_().smooth3(profile1, 3);

		boolean invert = true;

		double[] profile2 = new p10rmn_().createErf(smooth1, invert);
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
}
