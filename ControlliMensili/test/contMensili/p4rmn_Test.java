package contMensili;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import ij.IJ;
import ij.WindowManager;
import ij.gui.WaitForUserDialog;
import ij.measure.ResultsTable;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import utils.ButtonMessages;
import utils.MyConst;
import utils.MyLog;
import utils.UtilAyv;

public class p4rmn_Test {

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public final void testWritePreferences_1() {
		double[] in1 = { 1.235, 2.4467, 55.347, 88.234 };
		p4rmn_.writePreferences(in1);
		double[] result = p4rmn_.readPreferences();
		MyLog.logVector(result, "result readPreferences1");

	}

	@Test
	public final void testReadPreferences_1() {
		double[] result = p4rmn_.readPreferences();
		MyLog.logVector(result, "result readPreferences1");
	}

	@Test
	public final void testWritePreferences_2() {
		double[] in1 = { 1.444, 1.111, 5.555, 8.888 };
		p4rmn_.writePreferences(in1);
	}

	@Test
	public final void testReadPreferences_2() {
		double[] result = p4rmn_.readPreferences();
		MyLog.logVector(result, "result readPreferences2");
	}

	@Test
	public final void testAfterWork() {
		String path1 = ".\\Test2\\HR2A_testP4";
		UtilAyv.openImageMaximized(path1);
		UtilAyv.openImageMaximized(path1);
		int num = WindowManager.getWindowCount();
		int expected = 2;
		assertTrue(num > 0);
		IJ.log("p4rmn>>> num=" + num);
		IJ.log("------------------------------");
		UtilAyv.afterWork();
		// ButtonMessages.ModelessMsg("", "CONTINUA");
		// IJ.wait(500);
		num = WindowManager.getWindowCount();
		expected = 0;
		assertTrue(expected == num);
	}

	@Test
	public final void testSelfTestSilent() {
		new p4rmn_().selfTestSilent();
	}

	@Test
	public final void testMainMTF() {
		String path1 = ".\\Test2\\HR2A_testP4";
		boolean autoCalled = true;
		boolean step = false;
		boolean verbose = false;
		boolean test = true;

		double xStartRefline = MyConst.P4_X_START_REFLINE_TESTSIEMENS;
		double yStartRefline = MyConst.P4_Y_START_REFLINE_TESTSIEMENS;
		double xEndRefline = MyConst.P4_X_END_REFLINE_TESTSIEMENS;
		double yEndRefline = MyConst.P4_Y_END_REFLINE_TESTSIEMENS;
		double[] vetPreferences = { xStartRefline, yStartRefline, xEndRefline,
				yEndRefline };

		ResultsTable rt = p4rmn_.mainMTF(path1, vetPreferences, autoCalled,
				step, verbose, test);

		double[] vetResults = UtilAyv.vectorizeResults(rt);
		
		double[] vetReference = { 0.0, 0.9598900737778213, 0.9866962668164938,
				0.9883716512349785, 0.14203500307561293, 0.3751277435706519,
				0.9765625, 0.0 };

		boolean ok = UtilAyv.verifyResults1(vetResults, vetReference);
		assertTrue(ok);
	}

}