package contMensili;

import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.Roi;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import utils.MyConst;
import utils.MyLog;
import utils.UtilAyv;

public class p20rmn_Test {

	@Before
	public void setUp() throws Exception {
		new ImageJ(ImageJ.NORMAL);
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public final void testManualMenuTest() {
		int preset = 1;
		String testDirectory = "";
		boolean testA = true;
		int[] out = p20rmn_.manualMenu(preset, testDirectory, testA);
		UtilAyv.afterWork();
	}

	@Test
	public final void testMsgT1T2Test() {
		boolean T2 = p20rmn_.msgT1T2();
		MyLog.waitHere("T2= " + T2);

	}

	@Test
	public final void testManualImageSelectionTest() {
		String[] out1 = p20rmn_.manualImageSelection();
		MyLog.logVector(out1, "out1");
		MyLog.waitHere();
	}

	@Test
	public final void testManualRoiPreparationTest() {
		String vetPath[] = { ".\\Test2\\HT1A2_01testP2",
				".\\Test2\\HT1A2_02testP2", ".\\Test2\\HT1A2_03testP2",
				".\\Test2\\HT1A2_04testP2" };
		boolean typeT2 = true;
		ImagePlus imp1 = p20rmn_.imaPreparation(vetPath, typeT2);
		Roi[] pippo = p20rmn_.manualRoiPreparation(imp1);
		MyLog.waitHere();
	}

	@Test
	public final void testAutomaticRoiPreparationTest() {
		String vetPath[] = { ".\\Test2\\HT1A2_01testP2",
				".\\Test2\\HT1A2_02testP2", ".\\Test2\\HT1A2_03testP2",
				".\\Test2\\HT1A2_04testP2" };
		boolean typeT2 = true;
		ImagePlus imp1 = p20rmn_.imaPreparation(vetPath, typeT2);
		Roi[] pippo = p20rmn_.automaticRoiPreparation(imp1,
				MyConst.P20_X_ROI_TESTGE, MyConst.P20_Y_ROI_TESTGE,
				MyConst.P20_DIAM_ROI);
		MyLog.waitHere();
	}

	@Test
	public final void testMapPreparationTest() {
		String vetPath[] = { ".\\Test2\\HT1A2_01testP2",
				".\\Test2\\HT1A2_02testP2", ".\\Test2\\HT1A2_03testP2",
				".\\Test2\\HT1A2_04testP2" };
		boolean typeT2 = true;
		ImagePlus imp1 = p20rmn_.imaPreparation(vetPath, typeT2);
		Roi[] vetRoi = p20rmn_.automaticRoiPreparation(imp1,
				MyConst.P20_X_ROI_TESTGE, MyConst.P20_Y_ROI_TESTGE,
				MyConst.P20_DIAM_ROI);
		int[] bMap = p20rmn_.mapPreparation(imp1, vetRoi);
		MyLog.waitHere();
	}

	@Test
	public final void testFilterPreparationTest() {
		String vetPath[] = { ".\\Test2\\HT1A2_01testP2",
				".\\Test2\\HT1A2_02testP2", ".\\Test2\\HT1A2_03testP2",
				".\\Test2\\HT1A2_04testP2" };
		boolean typeT2 = true;
		boolean bstep = false;
		ImagePlus imp1 = p20rmn_.imaPreparation(vetPath, typeT2);
		Roi[] vetRoi = p20rmn_.automaticRoiPreparation(imp1,
				MyConst.P20_X_ROI_TESTGE, MyConst.P20_Y_ROI_TESTGE,
				MyConst.P20_DIAM_ROI);
		// int[] bMap = p20rmn_.mapPreparation(imp1, vetRoi);
		double filter = p20rmn_.filterPreparation(imp1, vetRoi, bstep);
		MyLog.waitHere("filter  aaaaa  = " + filter);
	}

	@Test
	public final void testTestSilent() {
		new p20rmn_().selfTestSilent();
	}

}