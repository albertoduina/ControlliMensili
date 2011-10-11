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

public class p11rmn_Test {
	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
//		new WaitForUserDialog("Do something, then click OK.").show();

	}

	@Test
	public final void testMainUniforTestSiemens() {

		String[] list = { "S1SA_01testP11", "S1SA_02testP11", "S1SA_03testP11" };
		String[] path = new InputOutput().findListTestImages2(
				MyConst.TEST_FILE, list, MyConst.TEST_DIRECTORY);

		String autoArgs = "0";
		boolean autoCalled = false;
		boolean step = false;
		boolean verbose = true;
		boolean test = true;
		double[] vetReference = new p11rmn_().referenceSiemens();
		boolean verticalProfile = true;
		int sqX = MyConst.P11_X_ROI_TESTSIEMENS;
		int sqY = MyConst.P11_Y_ROI_TESTSIEMENS;

		ResultsTable rt1 = p11rmn_.mainUnifor(path, sqX, sqY,
				verticalProfile, autoCalled, step, verbose, test);
//		ResultsTable rt1 = p11rmn_.mainUnifor(path, sqX, sqY, autoArgs,
//				verticalProfile, autoCalled, step, verbose, test);

		double[] vetResults = UtilAyv.vectorizeResults(rt1);

		boolean ok = UtilAyv.verifyResults1(vetResults, vetReference,
				MyConst.P11_vetName);
		assertTrue(ok);
	}

	@Test
	public final void testMainUniforTestGe() {

		String[] list = { "CTSA2_01testP11", "CTSA2_02testP11", "CTSA2_03testP11" };
		String[] path = new InputOutput().findListTestImages2(
				MyConst.TEST_FILE, list, MyConst.TEST_DIRECTORY);

		boolean autoCalled = false;
		boolean step = false;
		boolean verbose = true;
		boolean test = true;
		boolean verticalProfile = false;
		double[] vetReference = new p11rmn_().referenceGe();
		int sqX = MyConst.P11_X_ROI_TESTGE;
		int sqY = MyConst.P11_Y_ROI_TESTGE;

//		ResultsTable rt1 = p11rmn_.mainUnifor(path, sqX, sqY, autoArgs,
//				verticalProfile, autoCalled, step, verbose, test);
		ResultsTable rt1 = p11rmn_.mainUnifor(path, sqX, sqY,
				verticalProfile, autoCalled, step, verbose, test);

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

}
