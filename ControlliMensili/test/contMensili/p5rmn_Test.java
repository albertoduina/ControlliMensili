package contMensili;

import static org.junit.Assert.assertTrue;
import ij.IJ;
import ij.ImagePlus;
import ij.measure.ResultsTable;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import utils.InputOutput;
import utils.MyConst;
import utils.UtilAyv;

public class p5rmn_Test {
	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
//		new WaitForUserDialog("Do something, then click OK.").show();

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

}
