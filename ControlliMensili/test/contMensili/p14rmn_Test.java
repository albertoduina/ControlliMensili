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
import ij.gui.Overlay;
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

public class p14rmn_Test {

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
	public final void testCalculateMTF() {

		String path1 = "./Test2/004_P14";
		ImagePlus imp1 = UtilAyv.openImageMaximized(path1);
		int latoWidth = 130;
		int latoHeight = 150;
		int centerx = 480;
		int centery = 356;

		imp1.setRoi(centerx - latoWidth / 2, centery - latoHeight / 2, latoWidth, latoHeight);
		MyLog.waitHere("posizionare la ROI sullo SLANTED EDGE, in modo che la parte nera sia A SINISTRA e premere OK");

		p14rmn_ p14 = new p14rmn_();

		p14.calculateMTF(imp1);

		MyLog.waitHere();

	}

	@Test
	public final void testCalculateMTF2() {
	
		String path1 = "./Test2/005_P14";
		ImagePlus imp1 = UtilAyv.openImageMaximized(path1);
		int latoWidth = 130;
		int latoHeight = 130;
		int centerx = 586;
		int centery = 300;
	
		imp1.setRoi(centerx - latoWidth / 2, centery - latoHeight / 2, latoWidth, latoHeight);
		MyLog.waitHere("posizionare la ROI sullo SLANTED EDGE, in modo che la parte nera sia A SINISTRA e premere OK");
	
		p14rmn_ p14 = new p14rmn_();
	
		p14.calculateMTF(imp1);
	
		MyLog.waitHere();
	
	}

}