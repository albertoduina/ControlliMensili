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
		String[] path = new String[2];
		path[0] = "./Test2/S12S_01testP11";
		path[1] = "./Test2/S12S_02testP11";
		boolean autoCalled = false;
		boolean step = false;
		boolean verbose = true;
		boolean test = false;
		boolean verticalDir = false;
		double profond = 30.0;

		p11rmn_.mainUnifor(path, verticalDir, profond, autoCalled, step,
				verbose, test);

		new WaitForUserDialog("Do something, then click OK.").show();
		// IJ.wait(500);
	}

	@Test
	public final void testMainUniforTestSiemens() {

		String[] list = { "S12S_01testP11", "S12S_02testP11" };
		String[] path = new InputOutput().findListTestImages2(
				MyConst.TEST_FILE, list, MyConst.TEST_DIRECTORY);

		boolean autoCalled = false;
		boolean step = false;
		boolean verbose = true;
		boolean test = false;
		double[] vetReference = new p11rmn_().referenceSiemens();
		boolean verticalDir = false;
		double profond = 30.0;

		ResultsTable rt1 = p11rmn_.mainUnifor(path, verticalDir, profond,
				autoCalled, step, verbose, test);

		// ResultsTable rt1 = p11rmn_.mainUnifor(path, sqX, sqY, autoArgs,
		// verticalProfile, autoCalled, step, verbose, test);

		double[] vetResults = UtilAyv.vectorizeResults(rt1);

		boolean ok = UtilAyv.verifyResults1(vetResults, vetReference,
				MyConst.P11_vetName);
		assertTrue(ok);
	}

	@Test
	public final void testMainUniforTestGe() {

		String[] list = { "S12S_01testP11", "S12S_02testP11" };
		String[] path = new InputOutput().findListTestImages2(
				MyConst.TEST_FILE, list, MyConst.TEST_DIRECTORY);

		boolean autoCalled = false;
		boolean step = false;
		boolean verbose = true;
		boolean test = false;
		boolean direz = false;
		double profond = 30.0;
		double[] vetReference = new p11rmn_().referenceGe();

		// ResultsTable rt1 = p11rmn_.mainUnifor(path, sqX, sqY, autoArgs,
		// verticalProfile, autoCalled, step, verbose, test);

		ResultsTable rt1 = p11rmn_.mainUnifor(path, direz, profond, autoCalled,
				step, verbose, test);

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
