package contMensili;

import static org.junit.Assert.assertTrue;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.WaitForUserDialog;
import ij.measure.ResultsTable;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import utils.InputOutput;
import utils.MyConst;
import utils.MyLog;
import utils.UtilAyv;

public class p11rmn_Test {
	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
		// new WaitForUserDialog("Do something, then click OK.").show();

	}

	@Test
	public final void testMainUnifor() {

		String path1 = "./Test2/S12S_01testP11";
		String path2 = "./Test2/S12S_02testP11";
		boolean autoCalled = false;
		boolean step = false;
		boolean verbose = false;
		boolean test = false;
		int verticalDir = 3;
		double[] vetReference = new p11rmn_().referenceSiemens();
		double profond = 30.0;
		boolean fast = true;

		ResultsTable rt1 = p11rmn_.mainUnifor(path1, path2, verticalDir,
				profond, "", autoCalled, step, verbose, test, fast);

		double[] vetResults = UtilAyv.vectorizeResults(rt1);

		boolean ok = UtilAyv.verifyResults1(vetResults, vetReference, null);
		assertTrue(ok);

		// MyLog.waitHere("FINE");
		// IJ.wait(500);
	}

	@Test
	public final void testMainUniforTestSiemens() {

		// 16 dec 2011 sistemato, ora funziona in automatico

		String[] list = { "S12S_01testP11", "S12S_02testP11" };
		String[] path = new InputOutput().findListTestImages2(
				MyConst.TEST_FILE, list, MyConst.TEST_DIRECTORY);

		String path1 = path[0];
		String path2 = path[1];

		boolean autoCalled = false;
		boolean step = false;
		boolean verbose = true;
		boolean test = false;
		double[] vetReference = new p11rmn_().referenceSiemens();
		int direz = 3;
		double profond = 30.0;
		boolean fast = true;

		ResultsTable rt1 = p11rmn_.mainUnifor(path1, path2, direz, profond, "",
				autoCalled, step, verbose, test, fast);

		// ResultsTable rt1 = p11rmn_.mainUnifor(path, sqX, sqY, autoArgs,
		// verticalProfile, autoCalled, step, verbose, test);

		double[] vetResults = UtilAyv.vectorizeResults(rt1);

		boolean ok = UtilAyv.verifyResults1(vetResults, vetReference,
				MyConst.P11_vetName);
		assertTrue(ok);
	}

	@Test
	public final void testMainUniforTestGe() {

		// 16 dec 2011 sistemato, ora funziona in automatico

		String[] list = { "S12S_01testP11", "S12S_02testP11" };
		String[] path = new InputOutput().findListTestImages2(
				MyConst.TEST_FILE, list, MyConst.TEST_DIRECTORY);

		String path1 = path[0];
		String path2 = path[1];
		boolean autoCalled = false;
		boolean step = false;
		boolean verbose = true;
		boolean test = false;
		int direz = 3;
		double profond = 30.0;
		boolean fast = true;
		double[] vetReference = new p11rmn_().referenceGe();

		// ResultsTable rt1 = p11rmn_.mainUnifor(path, sqX, sqY, autoArgs,
		// verticalProfile, autoCalled, step, verbose, test);

		ResultsTable rt1 = p11rmn_.mainUnifor(path1, path2, direz, profond, "",
				autoCalled, step, verbose, test, fast);

		double[] vetResults = UtilAyv.vectorizeResults(rt1);

		boolean ok = UtilAyv.verifyResults1(vetResults, vetReference,
				MyConst.P11_vetName);
		// ButtonMessages.ModelessMsg("", "CONTINUA");
		assertTrue(ok);
	}

	@Test
	public final void testMainUniforTestSilent() {
		new p11rmn_().selfTestSilent();
	}

	@Test
	public final void testOverlayGrid() {

		ImagePlus imp1 = UtilAyv.openImageMaximized(".\\Test2\\S1SA_01testP11");
		boolean verbose = true;
		p11rmn_.overlayGrid(imp1, MyConst.P11_GRID_NUMBER, verbose);
		IJ.wait(300);

	}

	@Test
	public final void testMaxPeakSearch() {

		// 16 dec 2011 sistemato, ora funziona in automatico

		double[] profile1 = InputOutput
				.readDoubleArrayFromFile((new InputOutput()
						.findResource("/002.txt")));
		// MyLog.logVector(profile1, "profile1");
		double[] out = p11rmn_.maxPeakSearch(profile1);
		// MyLog.logVector(out, "out");

		double[] expected = { 96.0, 531.1009869960047 };
		boolean ok = UtilAyv.verifyResults1(expected, out, null);
		assertTrue(ok);

	}

	@Test
	public final void testPositionSearch() {

		// 16 dec 2011 sistemato, ora funziona in automatico

		boolean autoCalled = false;
		int verticalDir = 3;
		double profond = 30.0;
		boolean step = false;
		boolean verbose = false;
		boolean test = false;
		boolean fast = true;
		String path1 = "./Test2/S12S_01testP11";

		ImagePlus imp1 = UtilAyv.openImageNoDisplay(path1, true);

		double[] out = p11rmn_.positionSearch(imp1, autoCalled, verticalDir,
				profond, "STRINGA", step, verbose, test, fast);

		double[] expected = { 164.0, 172.0, 164.05714285714285, 0.0,
				164.05714285714285, 256.0, 186.0, 169.0 };
		boolean ok = UtilAyv.verifyResults1(expected, out, null);
		assertTrue(ok);
		// MyLog.logVector(out, "out");

	}

}
