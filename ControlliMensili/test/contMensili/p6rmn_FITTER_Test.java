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

public class p6rmn_FITTER_Test {

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
	public final void testPeakFitterFWHM_SUPERGAUSSIAN() {
		
		
	
	String path1 = "data/P6/SolaGavardo_HT5A";
//	ImagePlus imp1 = UtilAyv.openImageMaximized(path1);
	
	ImagePlus imp1 = UtilAyv.openImageNoDisplay(path1, false);
	
	String name1="data/P6/primoCuneoGavardo.txt";
	String name2="data/P6/SolaGavardo004cuneo5.txt";
	
	double[][] profile1 = InputOutput.readDoubleMatrixFromFile(name1);
	
	MyLog.waitHere();

	boolean slab= true;;
	boolean step=true;	
	boolean junitTest=true;

	double[] out1 = new p6rmn_FITTER().peakFitterFWHM_SUPERGAUSSIAN(imp1, profile1,  slab, step, junitTest); 
	MyLog.waitHere();
	
	}
	
	


}